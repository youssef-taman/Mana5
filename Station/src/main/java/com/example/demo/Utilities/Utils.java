package com.example.demo.Utilities;

public class Utils {

    private static final double DROP_MESSAGE_PROBABILITY = 0.1;


    public static   boolean tenPercentChance() {
        return Math.random() < DROP_MESSAGE_PROBABILITY;
    }


    public static int randomInt(int min, int max) {
        return (int) (Math.random() * (max - min + 1)) + min;
    }
}
