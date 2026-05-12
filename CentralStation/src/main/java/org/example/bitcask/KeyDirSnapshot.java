package org.example.bitcask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializes and deserializes the full KeyDir to/from a single binary file.
 *
 * Format:
 *   [entryCount: 4 bytes]
 *   For each entry:
 *     [keySize: 4][fileId: 4][valueSize: 4][timestamp: 8][valueOffset: 8][key: N]
 */
public class KeyDirSnapshot {

    public static final String FILENAME = "keydir.snapshot";

    private static final int FIXED_ENTRY_SIZE =
            Integer.BYTES +   // keySize
                    Integer.BYTES +   // fileId
                    Integer.BYTES +   // valueSize
                    Long.BYTES    +   // timestamp
                    Long.BYTES;       // valueOffset  = 24 bytes


    public static void write(File file,
                             Map<String, KeyDirEntry> keyDir) throws IOException {

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {

            out.writeInt(keyDir.size());

            for (Map.Entry<String, KeyDirEntry> e : keyDir.entrySet()) {
                byte[]      keyBytes = e.getKey().getBytes(StandardCharsets.UTF_8);
                KeyDirEntry kd       = e.getValue();

                out.writeInt(keyBytes.length);
                out.writeInt(kd.fileId());
                out.writeInt(kd.valueSize());
                out.writeLong(kd.timestamp());
                out.writeLong(kd.valueOffset());
                out.write(keyBytes);
            }
        }
    }


    public static Map<String, KeyDirEntry> read(File file) throws IOException {

        Map<String, KeyDirEntry> keyDir = new HashMap<>();

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            int entryCount = in.readInt();

            for (int i = 0; i < entryCount; i++) {
                int    keySize     = in.readInt();
                int    fileId      = in.readInt();
                int    valueSize   = in.readInt();
                long   timestamp   = in.readLong();
                long   valueOffset = in.readLong();
                byte[] keyBytes    = new byte[keySize];
                in.readFully(keyBytes);

                String key = new String(keyBytes, StandardCharsets.UTF_8);
                keyDir.put(key, new KeyDirEntry(
                        fileId, valueOffset, valueSize, timestamp));
            }
        }

        return keyDir;
    }
}