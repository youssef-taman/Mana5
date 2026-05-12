package org.example.controller;

import org.example.bitcask.BitCaskImp;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

@RestController
@RequestMapping("/bitcask")
public class BitCaskController {

    private final BitCaskImp bitCask;

    public BitCaskController(BitCaskImp bitCask) {
        this.bitCask = bitCask;
    }

    @GetMapping("/view-all")
    public Map<String, String> viewAll() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : new TreeSet<>(bitCask.keys())) {
            result.put(key, bitCask.get(key));
        }
        return result;
    }

    @GetMapping("/view")
    public ResponseEntity<?> view(@RequestParam String key) throws Exception {
        String value = bitCask.get(key);
        return value == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(value);
    }
}

