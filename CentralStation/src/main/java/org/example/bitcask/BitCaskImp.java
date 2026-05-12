package org.example.bitcask;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BitCaskImp implements BitCask {

    private final File directory;
    private final File snapshotFile;
    private final long maxFileSize;
    private final long compactionDelayMs;

    private final ConcurrentHashMap<String, KeyDirEntry> keyDir =
            new ConcurrentHashMap<>();

    private DataFile activeFile;

    private final ScheduledExecutorService scheduler;



    public BitCaskImp(
            @Value("${bitcask.path}") String path,
            @Value("${bitcask.max-file-size}") long maxFileSize,
            @Value("${bitcask.compaction-delay-ms}") long compactionDelayMs
    ) throws Exception {
        this.directory = new File(path);
        this.snapshotFile = new File(directory, KeyDirSnapshot.FILENAME);
        this.maxFileSize = maxFileSize;
        this.compactionDelayMs = compactionDelayMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bitcask-compactor");
            t.setDaemon(true);
            return t;
        });

        if (!directory.exists() && !directory.mkdirs())
            throw new Exception("Failed to create directory: " + path);

        recover();
        scheduleCompaction();
    }


    private void recover() throws Exception {

        List<File> dataFiles = loadExistingDataFiles();

        if (dataFiles.isEmpty()) {
            activeFile = openDataFile(0);
            log.info("Fresh start — no existing data");
            return;
        }

//        if (snapshotFile.exists()) {
//            recoverFromSnapshot(dataFiles);
//        } else {
//            recoverFromHintAndDataFiles(dataFiles);
//        }
        recoverFromHintAndDataFiles(dataFiles);

        int maxFileId = getFileId(dataFiles.get(dataFiles.size() - 1));
        activeFile = openDataFile(maxFileId + 1);

        log.info("Recovery complete — {} keys, activeFile={}",
                keyDir.size(), activeFile.getFileId());
    }

//
//    private void recoverFromSnapshot(List<File> dataFiles) throws Exception {
//
//        Map<String, KeyDirEntry> snapshot = KeyDirSnapshot.read(snapshotFile);
//        keyDir.putAll(snapshot);
//        log.info("Loaded snapshot — {} keys", keyDir.size());
//
//
//        for (File dataFile : dataFiles) {
//            int  fileId   = getFileId(dataFile);
//            File hintFile = hintFileFor(fileId);
//
//            if (!hintFile.exists()) {
//                log.info("Scanning segment fileId={} (no hint file)", fileId);
//                scanDataFile(dataFile, fileId);
//            }
//        }
//    }


    private void recoverFromHintAndDataFiles(List<File> dataFiles) throws Exception {
        for (File dataFile : dataFiles) {
            int  fileId   = getFileId(dataFile);
            File hintFile = hintFileFor(fileId);

            if (hintFile.exists()) {
                recoverFromHintFile(hintFile, fileId);
                log.info("Recovered from hint — fileId={}", fileId);
            } else {
                scanDataFile(dataFile, fileId);
                log.warn("Recovered from data file (no hint) — fileId={}", fileId);
            }
        }
    }


    private void recoverFromHintFile(File hintFile, int fileId) throws IOException {
        for (HintRecord r : HintFile.read(hintFile)) {
            String key = new String(r.key(), StandardCharsets.UTF_8);
            updateKeyDirIfNewer(key,
                    new KeyDirEntry(fileId, r.valueOffset(), r.valueSize(), r.timestamp()));
        }
    }


    private void scanDataFile(File file, int fileId) throws Exception {
        DataFile dataFile = new DataFile(file, fileId);
        try {
            for (Record record : dataFile.readAllRecords()) {
                String key = new String(record.getKey(), StandardCharsets.UTF_8);
                updateKeyDirIfNewer(key, new KeyDirEntry(
                        fileId,
                        record.getValueOffset(),
                        record.getValue().length,
                        record.getTimestamp()
                ));
            }
        } finally {
            dataFile.close();
        }
    }

    private void updateKeyDirIfNewer(String key, KeyDirEntry entry) {
        keyDir.merge(key, entry, (existing, incoming) ->
                incoming.timestamp() > existing.timestamp() ? incoming : existing);
    }


    public synchronized void put(String key, String value) throws Exception {
        Record record = Record.create(key, value);
        byte[] bytes  = record.serialize();

        rotateIfNeeded(bytes.length);

        long offset = activeFile.append(bytes);

        long valueOffset = offset
                + Long.BYTES       // timestamp
                + Integer.BYTES    // key size
                + Integer.BYTES    // value size
                + key.getBytes(StandardCharsets.UTF_8).length;

        keyDir.put(key, new KeyDirEntry(
                activeFile.getFileId(),
                valueOffset,
                value.getBytes(StandardCharsets.UTF_8).length,
                record.getTimestamp()
        ));
    }

    public String get(String key) throws Exception {
        KeyDirEntry entry = keyDir.get(key);
        if (entry == null) return null;

        DataFile file = openDataFile(entry.fileId());
        try {
            byte[] value = file.read(entry.valueOffset(), entry.valueSize());
            return new String(value, StandardCharsets.UTF_8);
        } finally {
            file.close();
        }
    }

    public Set<String> keys() {
        return keyDir.keySet();
    }


    private void rotateIfNeeded(int newRecordSize) throws Exception {
        if (activeFile.size() + newRecordSize <= maxFileSize) return;

        int closingFileId = activeFile.getFileId();
        DataFile closingFile = activeFile;

        activeFile = openDataFile(closingFileId + 1);


        writeHintFile(closingFileId);

        closingFile.close();
        log.info("Rotated — closed fileId={} opened fileId={}",
                closingFileId, activeFile.getFileId());
    }

    private void writeHintFile(int fileId) {
        try {
            HintFile.write(hintFileFor(fileId), keyDir, fileId);
            log.debug("Hint file written for fileId={}", fileId);
        } catch (Exception e) {
            log.warn("Failed to write hint file for fileId={}: {}",
                    fileId, e.getMessage());
        }
    }


    private void scheduleCompaction() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                log.info("Compaction starting");
                compact();
                log.info("Compaction finished");
            } catch (Exception e) {
                log.error("Compaction failed — will retry next cycle", e);
            }
        }, compactionDelayMs, compactionDelayMs, TimeUnit.MILLISECONDS);
    }


    public void compact() throws Exception {

        final Map<String, KeyDirEntry> snapshot;
        final List<File> filesToDelete;
        final int compactedFileId;

        synchronized (this) {
            if (keyDir.isEmpty()) return;

            snapshot = new HashMap<>(keyDir);
            filesToDelete = loadExistingDataFiles();
            compactedFileId = activeFile.getFileId() + 1;

            if (filesToDelete.isEmpty()) return;
        }

        Map<String, KeyDirEntry> hintEntries = createCompactFile(compactedFileId, snapshot);

        HintFile.write(hintFileFor(compactedFileId), hintEntries, compactedFileId);

        updateKeyDir(hintEntries, snapshot);
        deleteFiles(filesToDelete ,  compactedFileId);

    }

    private Map<String , KeyDirEntry> createCompactFile(int compactedFileId, Map<String, KeyDirEntry> snapshot) throws Exception {
        Map<String, KeyDirEntry> newEntries = new HashMap<>();
        DataFile compactedFile = openDataFile(compactedFileId);
        try {
            for (Map.Entry<String, KeyDirEntry> e : snapshot.entrySet()) {
                String key = e.getKey();
                KeyDirEntry kdEntry = e.getValue();

                DataFile source = openDataFile(kdEntry.fileId());
                byte[] value;
                try {
                    value = source.read(kdEntry.valueOffset(), kdEntry.valueSize());
                } finally {
                    source.close();
                }

                Record record = new Record(
                        key.getBytes(StandardCharsets.UTF_8),
                        value,
                        kdEntry.timestamp()
                );

                long offset = compactedFile.append(record.serialize());
                long valueOffset = offset
                        + Long.BYTES
                        + Integer.BYTES
                        + Integer.BYTES
                        + key.getBytes(StandardCharsets.UTF_8).length;

                newEntries.put(key, new KeyDirEntry(
                        compactedFileId, valueOffset, value.length, kdEntry.timestamp()));
            }
            return newEntries;
        } finally {
            compactedFile.close();
        }
    }



    private void updateKeyDir(Map<String, KeyDirEntry> newEntries, Map<String, KeyDirEntry> snapshot) {
        synchronized (this) {
            for (Map.Entry<String, KeyDirEntry> e : newEntries.entrySet()) {
                String key = e.getKey();
                KeyDirEntry snapshotEntry = snapshot.get(key);
                KeyDirEntry current= keyDir.get(key);

                if (current.timestamp() == snapshotEntry.timestamp()) {
                    keyDir.put(key, e.getValue());
                }
            }
        }
    }

    private void deleteFiles(List<File> filesToDelete, int compactedFileId) {

        for (File file : filesToDelete) {
            int fileId = getFileId(file);

            if (fileId == compactedFileId || fileId == activeFile.getFileId()) continue;

            if (file.exists() && !file.delete())
                log.warn("Failed to delete data file: {}", file.getName());

            File hint = hintFileFor(fileId);
            if (hint.exists() && !hint.delete())
                log.warn("Failed to delete hint file: {}", hint.getName());
        }

        log.info("Compaction complete");
    }

    public void close() throws Exception {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS))
                scheduler.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

//        synchronized (this) {
//            writeHintFile(activeFile.getFileId());
//
//            KeyDirSnapshot.write(snapshotFile, keyDir);
//            log.info("Snapshot written — {} keys", keyDir.size());
//
//            activeFile.close();
//        }

        log.info("BitCask closed cleanly");
    }


    private List<File> loadExistingDataFiles() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".data"));
        if (files == null) return Collections.emptyList();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparingInt(this::getFileId));
        return list;
    }

    private int getFileId(File file) {
        return Integer.parseInt(file.getName().replace(".data", ""));
    }

    private DataFile openDataFile(int fileId) throws Exception {
        return new DataFile(new File(directory, fileId + ".data"), fileId);
    }

    private File hintFileFor(int fileId) {
        return new File(directory, fileId + HintFile.EXTENSION);
    }
}