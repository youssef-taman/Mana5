package org.example.bitcask;

public record KeyDirEntry(int fileId , long valueOffset , int valueSize , long timestamp) {
}
