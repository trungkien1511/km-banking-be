package com.kmbank;

import com.kmbank.modules.user.entity.User;
import com.kmbank.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@SpringBootTest
public class VerifyUserTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void testLogin() {
        System.out.println("====== STARTING VERIFICATION ======");
        Optional<User> userOpt = userRepository.findByUsernameOrPhoneNumber("john_doe", "john_doe");
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("User found: " + user.getUsername());
            System.out.println("Hash in DB: " + user.getPasswordHash());
            boolean matches = passwordEncoder.matches("Password123", user.getPasswordHash());
            System.out.println("Password123 matches hash? " + matches);
            System.out.println("New hash for Password123: " + passwordEncoder.encode("Password123"));
        } else {
            System.out.println("User john_doe NOT FOUND in the database!");
            System.out.println("Total users in DB: " + userRepository.count());
            userRepository.findAll().forEach(u -> System.out.println("Existing user: " + u.getUsername() + ", phone: " + u.getPhoneNumber()));
        }
        System.out.println("====== END VERIFICATION ======");
    }
}
