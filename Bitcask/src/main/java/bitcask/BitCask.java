package bitcask;

import java.io.IOException;

public interface BitCask {

    void put(String key, String value) throws IOException;
    String get(String key);
}
