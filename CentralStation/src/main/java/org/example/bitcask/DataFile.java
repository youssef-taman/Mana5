package org.example.bitcask;

import lombok.Getter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

@Getter
public class DataFile {

    private final int fileId;
    private final FileChannel channel;
    private final RandomAccessFile raf;

    public DataFile(File file, int fileId) throws IOException {
        this.fileId = fileId;
        this.raf     = new RandomAccessFile(file, "rw");
        this.channel = raf.getChannel();
    }

    public synchronized long append(byte[] data) throws IOException {
        long offset = channel.size();
        channel.write(ByteBuffer.wrap(data), offset);
        return offset;
    }

    /**
     * Positioned read — thread-safe, no seek needed.
     * Multiple threads can read different offsets simultaneously.
     */
    public byte[] read(long offset, int size) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(size);
        channel.read(buf, offset);
        return buf.array();
    }

    /**
     * Reads all records sequentially.
     */
    public List<Record> readAllRecords() throws IOException {
        List<Record> records  = new ArrayList<>();
        long         fileSize = channel.size();
        long         offset   = 0;

        while (offset < fileSize) {
            ByteBuffer headerBuf = ByteBuffer.allocate(Record.HEADER_SIZE);
            int bytesRead = channel.read(headerBuf, offset);
            if (bytesRead < Record.HEADER_SIZE) break;

            headerBuf.flip();
            long timestamp = headerBuf.getLong();
            int  keySize   = headerBuf.getInt();
            int  valueSize = headerBuf.getInt();

            ByteBuffer dataBuf = ByteBuffer.allocate(keySize + valueSize);
            channel.read(dataBuf, offset + Record.HEADER_SIZE);
            dataBuf.flip();

            byte[] keyBytes   = new byte[keySize];
            byte[] valueBytes = new byte[valueSize];
            dataBuf.get(keyBytes);
            dataBuf.get(valueBytes);

            long valueOffset = offset + Record.HEADER_SIZE + keySize;

            records.add(new Record(keyBytes, valueBytes, timestamp, valueOffset));

            offset += Record.HEADER_SIZE + keySize + valueSize;
        }

        return records;
    }

    public long size() throws IOException {
        return channel.size();
    }

    public void close() throws IOException {
        channel.close();
        raf.close();
    }
}