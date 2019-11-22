package com.kvaster.gsuite;

import java.util.Random;

public class PasswordGenerator {
    private static final String PASS_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_$";

    private final Random rand = new Random();

    public String genPass() {
        return genPass(32);
    }

    public String genPass(int size) {
        char[] pass = new char[size];
        for (int i = 0; i < size; i++) {
            pass[i] = PASS_CHARS.charAt(rand.nextInt(PASS_CHARS.length()));
        }
        return new String(pass);
    }
}
