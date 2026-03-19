package com.example.backend.controller;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.backend.model.ChatMessage;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.service.MistralService;

@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ChatController {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private static final int MAX_HISTORY = 50; // 25 conversations = 50 messages (user+assistant pairs)

    // Helper to load history from DB
    private List<Map<String, String>> loadHistory(Long userId) {
        List<ChatMessage> dbHistory = chatMessageRepository
                .findByUserIdOrderByCreatedAtAsc(userId);

        List<Map<String, String>> history = new ArrayList<>();
        int start = Math.max(0, dbHistory.size() - MAX_HISTORY);
        for (int i = start; i < dbHistory.size(); i++) {
            ChatMessage msg = dbHistory.get(i);
            history.add(Map.of(
                    "role", msg.getRole(),
                    "content", msg.getContent()));
        }
        return history;
    }

    // ✅ STREAMING ENDPOINT - word-by-word response
    @PostMapping("/chat/stream")
    public Object chatStream(
            @RequestBody Map<String, String> req,
            HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Not authenticated"));
        }

        String userMessage = req.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message is required"));
        }

        // Load history and add current message
        List<Map<String, String>> history = loadHistory(userId);
        history.add(Map.of("role", "user", "content", userMessage));

        // Save user message immediately
        chatMessageRepository.save(new ChatMessage(userId, "user", userMessage));

        // Create SSE emitter with 2 minute timeout
        SseEmitter emitter = new SseEmitter(120_000L);

        // Process streaming in background thread
        new Thread(() -> {
            try {
                InputStream mistralStream = MistralService.getStreamingResponse(history);
                BufferedReader reader = new BufferedReader(new InputStreamReader(mistralStream));
                StringBuilder fullResponse = new StringBuilder();

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();

                        if (data.equals("[DONE]")) {
                            break;
                        }

                        String content = MistralService.extractDeltaContent(data);
                        if (content != null && !content.isEmpty()) {
                            fullResponse.append(content);
                            emitter.send(SseEmitter.event()
                                    .data(Map.of("content", content)));
                        }
                    }
                }

                reader.close();

                // Save complete assistant reply to DB
                String completeReply = fullResponse.toString();
                if (!completeReply.isEmpty()) {
                    chatMessageRepository.save(
                            new ChatMessage(userId, "assistant", completeReply));
                }

                // Signal done
                emitter.send(SseEmitter.event().data(Map.of("done", true)));
                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .data(Map.of("error", "Stream failed: " + e.getMessage())));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    // Non-streaming endpoint (kept as fallback)
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody Map<String, String> req,
            HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Not authenticated"));
        }

        String userMessage = req.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message is required"));
        }

        try {
            List<Map<String, String>> history = loadHistory(userId);
            history.add(Map.of("role", "user", "content", userMessage));

            String reply = MistralService.callMistralWithHistory(history);

            chatMessageRepository.save(new ChatMessage(userId, "user", userMessage));
            chatMessageRepository.save(new ChatMessage(userId, "assistant", reply));

            return ResponseEntity.ok(Map.of("reply", reply));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get response: " + e.getMessage()));
        }
    }

    @GetMapping("/chat/history")
    public ResponseEntity<?> getHistory(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Not authenticated"));
        }

        List<ChatMessage> dbHistory = chatMessageRepository
                .findByUserIdOrderByCreatedAtAsc(userId);

        int start = Math.max(0, dbHistory.size() - MAX_HISTORY);
        List<Map<String, String>> messages = new ArrayList<>();
        for (int i = start; i < dbHistory.size(); i++) {
            ChatMessage msg = dbHistory.get(i);
            messages.add(Map.of(
                    "role", msg.getRole(),
                    "content", msg.getContent()));
        }

        return ResponseEntity.ok(Map.of("messages", messages));
    }

    @DeleteMapping("/chat/delete")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteChat(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Not authenticated"));
        }

        chatMessageRepository.deleteByUserId(userId);
        return ResponseEntity.ok(Map.of("message", "Chat deleted"));
    }
}
