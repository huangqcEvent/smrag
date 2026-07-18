package com.hqc.smrag.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Generate BCrypt password hash
 */
public class BCryptGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "admin123";
        String hash = encoder.encode(password);
        System.out.println("Password: " + password);
        System.out.println("Hash length: " + hash.length());
        System.out.println("Hash: " + hash);

        // Verify
        boolean matches = encoder.matches(password, hash);
        System.out.println("Verification: " + matches);
    }
}