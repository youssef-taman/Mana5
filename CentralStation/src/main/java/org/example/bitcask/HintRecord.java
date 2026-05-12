package org.example.bitcask;


public record HintRecord(
        long   timestamp,
        int    keySize,
        int    valueSize,
        long   valueOffset,
        byte[] key
) {}