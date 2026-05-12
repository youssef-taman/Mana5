package org.example.bitcask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes .hint files.
 *
 * A hint file is a compact companion to a .data segment — same keys and
 * offsets, no value bytes. Used as fallback recovery when no snapshot exists.
 *
 * On-disk record layout:
 * [timestamp: 8][keySize: 4][valueSize: 4][valueOffset: 8][key: N]
 */
public class HintFile {

    public static final String EXTENSION = ".hint";

    private static final int FIXED_HEADER =
            Long.BYTES    +   // timestamp
                    Integer.BYTES +   // keySize
                    Integer.BYTES +   // valueSize
                    Long.BYTES;       // valueOffset  = 24 bytes

    public static void write(File hintFile,
                             Map<String, KeyDirEntry> keyDir,
                             int fileId) throws IOException {

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(hintFile)))) {

            for (Map.Entry<String, KeyDirEntry> e : keyDir.entrySet()) {
                KeyDirEntry kd = e.getValue();
                if (kd.fileId() != fileId) continue;

                byte[] keyBytes = e.getKey().getBytes(StandardCharsets.UTF_8);

                out.writeLong(kd.timestamp());
                out.writeInt(keyBytes.length);
                out.writeInt(kd.valueSize());
                out.writeLong(kd.valueOffset());
                out.write(keyBytes);
            }
        }
    }


    public static List<HintRecord> read(File hintFile) throws IOException {

        List<HintRecord> records = new ArrayList<>();

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(hintFile)))) {

            while (in.available() >= FIXED_HEADER) {
                long   timestamp   = in.readLong();
                int    keySize     = in.readInt();
                int    valueSize   = in.readInt();
                long   valueOffset = in.readLong();
                byte[] key         = new byte[keySize];
                in.readFully(key);

                records.add(new HintRecord(
                        timestamp, keySize, valueSize, valueOffset, key));
            }
        }

        return records;
    }
}