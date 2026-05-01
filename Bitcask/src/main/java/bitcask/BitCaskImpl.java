    package bitcask;

    import exception.GlobalMessages;
    import exception.KeyNotFoundException;
    import exception.LogAppendException;

    import java.io.IOException;
    import java.io.RandomAccessFile;
    import java.nio.ByteBuffer;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.Path;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Map;
    import java.util.concurrent.locks.ReadWriteLock;
    import java.util.concurrent.locks.ReentrantReadWriteLock;


    public class BitCaskImpl implements BitCask{

        private final static int INTEGER_SIZE = 4;
        private final static long MAX_FILE_SIZE = 1024 * 1024 * 10; // 10MB for example

        private Map<String, List<Long>> keyDir = new HashMap<>();


        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        private final Path dataDirectory;


        private RandomAccessFile activeFile;
        private long activeFileId;

        public BitCaskImpl(Path dataDirectory) {
            this.dataDirectory = dataDirectory;
        }

        @Override
        public void put(String key, String value) throws IOException {
            ByteBuffer record = serializeRecord(key, value);
            rwLock.writeLock().lock();
            try {
                long recordOffset = append(record);
                keyDir.put(key, List.of(activeFileId, recordOffset));
            }
            finally {
                rwLock.writeLock().unlock();
            }
        }


        @Override
        public String get(String key) {
                rwLock.readLock().lock();
                try {
                    if (!keyDir.containsKey(key)) {
                        throw new KeyNotFoundException(GlobalMessages.KEY_NOT_FOUND);
                    }
                    List<Long> fileInfo = keyDir.get(key);
                    Long fileId = fileInfo.get(0);
                    Long recordOffset = fileInfo.get(1);

                    return readRecordFromFile(fileId , recordOffset);
                }
                finally {
                    rwLock.readLock().unlock();
                }



        }

        private String readRecordFromFile(Long fileId, Long offset)  {
            try (RandomAccessFile file = new RandomAccessFile(getFileName(fileId), "r")) {
                file.seek(offset);
                int keyLength = file.readInt();
                byte[] keyBytes = new byte[keyLength];
                file.readFully(keyBytes);
                int valueLength = file.readInt();
                byte[] valueBytes = new byte[valueLength];
                file.readFully(valueBytes);
                return new String(valueBytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new KeyNotFoundException(GlobalMessages.KEY_NOT_FOUND);
            }
        }


        long append(ByteBuffer newRecord) {
              try {
                  rotateIfNeeded(newRecord.capacity());
                  long offset = activeFile.length();
                  activeFile.seek(activeFile.length());
                  activeFile.write(newRecord.array());
                  return offset;
              }
              catch (IOException e) {
                  throw new LogAppendException(GlobalMessages.FAILED_TO_WRITE_RECORD);}

        }


        private void rotateIfNeeded(long newRecordLength) throws IOException {

            boolean isThereActiveFile = activeFile != null;

            if (!isThereActiveFile || getSizeAfterAppend(newRecordLength) > MAX_FILE_SIZE) {
                closeActiveFile();
                this.activeFileId = getFileId();
                activeFile = new RandomAccessFile(getFileName(activeFileId), "rw");
            }
        }

        private void closeActiveFile() throws IOException {
            if (activeFile != null) {
                activeFile.close();
            }
        }



    private ByteBuffer serializeRecord(String key, String value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(2 * INTEGER_SIZE + keyBytes.length + valueBytes.length);
        buffer.putInt(keyBytes.length);
        buffer.put(keyBytes);
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
        return buffer;
    }

        private long getFileId() {
            return System.currentTimeMillis();
        }

        private String getFileName(long id) {

            return dataDirectory.resolve("seg_" + id + ".dat").toString();
        }

        private long getSizeAfterAppend(long newRecordLength) throws IOException {
            long currentSize = activeFile == null ? 0 : activeFile.length();
            return currentSize + newRecordLength;
        }

    }
