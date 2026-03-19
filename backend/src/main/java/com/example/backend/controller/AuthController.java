package com.example.backend.controller;

import com.example.backend.model.User;
import com.example.backend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(
            @RequestBody Map<String, String> req,
            HttpSession session) {

        String username = req.get("username");
        String password = req.get("password");

        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Username and password are required"));
        }

        username = username.trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Username already exists"));
        }

        if (password.length() < 4) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Password must be at least 4 characters"));
        }

        User user = new User(username, encoder.encode(password));
        userRepository.save(user);

        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());

        return ResponseEntity.ok(Map.of(
            "message", "Account created successfully",
            "username", user.getUsername()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> req,
            HttpSession session) {

        String username = req.get("username");
        String password = req.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Username and password are required"));
        }

        username = username.trim().toLowerCase();

        Optional<User> optUser = userRepository.findByUsername(username);
        if (optUser.isEmpty() || !encoder.matches(password, optUser.get().getPasswordHash())) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid username or password"));
        }

        User user = optUser.get();
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());

        return ResponseEntity.ok(Map.of(
            "message", "Login successful",
            "username", user.getUsername()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");

        if (userId == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Not authenticated"));
        }

        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "username", username
        ));
    }
}
