package com.kmbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
public class VerifyUserTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void generateSeedHashes() {
        System.out.println("====== BCrypt Hash Generator ======");
        System.out.println("Test@1234  → " + passwordEncoder.encode("Test@1234"));
        System.out.println("Admin@1234 → " + passwordEncoder.encode("Admin@1234"));
        System.out.println("====== Done ======");
    }
}
