package org.example.bitcask;

import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class Record {

    public static final int HEADER_SIZE =
            Long.BYTES +      // timestamp
                    Integer.BYTES +   // key size
                    Integer.BYTES;    // value size

    private final byte[] key;
    private final byte[] value;
    private long valueOffset;
    private final long timestamp;

    Record(byte[] key, byte[] value, long timestamp) {
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
    }

    Record(byte[] key, byte[] value, long timestamp, long valueOffset) {
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
        this.valueOffset = valueOffset;
    }

    public byte[] serialize() {
        int valueSize = value == null ? 0 : value.length;

        ByteBuffer buffer = ByteBuffer.allocate(
                HEADER_SIZE + key.length + valueSize
        );

        buffer.putLong(timestamp);
        buffer.putInt(key.length);
        buffer.putInt(valueSize);
        buffer.put(key);
        if (value != null) {
            buffer.put(value);
        }

        return buffer.array();
    }

    public static Record create(String key, String value) {
        return new Record(
                key.getBytes(StandardCharsets.UTF_8),
                value.getBytes(StandardCharsets.UTF_8),
                System.currentTimeMillis()
        );
    }
}