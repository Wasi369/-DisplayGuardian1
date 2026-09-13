package com.example.displayguardian;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

final class PasswordUtil {
    static String randomSalt() {
        byte[] b = new byte[16]; new SecureRandom().nextBytes(b); return hex(b);
    }
    static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static boolean verify(String password, String expectedHash, String salt) {
        return hash(password, salt).equals(expectedHash);
    }
    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b: bytes) s.append(String.format("%02x", b & 0xff)); return s.toString(); }
}
