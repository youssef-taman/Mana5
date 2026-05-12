package org.example.bitcask;

import java.io.IOException;

public interface BitCask {

    void put(String key, String value) throws Exception;
    String get(String key)throws Exception;
}
