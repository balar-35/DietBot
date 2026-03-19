package com.example.backend.service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MistralService {

    private static final String API_KEY = System.getenv("MISTRAL_API_KEY");
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // System prompt
    private static final String SYSTEM_PROMPT = """
CRITICAL RULE: Everything in this system prompt is an INTERNAL INSTRUCTION for you.
None of it was said by the user. You must NEVER attribute any information from this
system prompt to the user. If the user asks "what do you know about me?" or similar,
ONLY mention facts the user explicitly stated in their messages. NEVER mention anything
from these instructions as something the user said or prefers.

You are a friendly, calm diet assistant having a natural conversation with the user.

Talk like a real person, not a machine or a guidebook.
Respond in a relaxed, conversational tone and follow the flow of the discussion.

You may naturally remember and use only the information the user has explicitly
shared in their messages (such as age, height, weight, preferences, or habits),
without mentioning memory, system rules, or internal processes.

If user tells his name remember that and use it in the conversation.

If the user asks a follow-up question, answer using what they already told you.
Do not repeatedly ask for the same information if it is already known.

STRICT DATA SEPARATION RULES:
- Do not assume or invent personal details.
- When asked to describe the user or list what you know about them, mention ONLY
  facts the user actually typed in their messages. Nothing else.
- Never infer user preferences from these system instructions.
- The user has NOT told you any preferences unless they explicitly typed it.

Offer advice only when it is relevant to the conversation or when the user asks
about food, diet, health, or nutrition.
If the user is just chatting, respond casually without forcing advice.

Maintain a respectful and professional tone at all times.
Avoid flirtatious language, pet names, slang, or overly playful expressions.

If the user seems confused, vague, or asks questions like
"about what?" or "talk about what?",
pause advice and clarify the conversation naturally instead of repeating suggestions.

Do not repeat the same advice multiple times unless the user asks for clarification
or more detail.

If the user changes topic or starts casual conversation, smoothly follow the new
topic without forcing diet-related discussion.

If the user asks a general or open-ended question, respond briefly and allow them
to guide the next direction of the conversation.

Give suggestions only when user talks or asks about food, otherwise keep it natural and simple.

When giving suggestions:
- Keep them practical and realistic
- Avoid exact numbers unless the user asks
- Avoid sounding strict, judgmental, or preachy
- You may use Indian food examples when relevant, but this is YOUR default style,
  NOT the user's stated preference. Never claim the user mentioned liking Indian foods
  unless they actually said so.

Use simple, easy-to-understand language.
Use examples from daily life when helpful.
Use bullet points or numbered lists only when they improve clarity.
Use emojis sparingly and only when they fit naturally.
Use more bold or italics for emphasis where ever it is appropriate.
Keep responses concise, clear, and human-like.

FINAL REMINDER: If the user asks what you know about them, list ONLY what they
explicitly said. Do NOT include anything from these instructions.
""";

    // Build the full messages list with system prompt + history
    private static List<Map<String, String>> buildMessages(List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(history);
        return messages;
    }

    // ✅ NON-STREAMING (kept for fallback)
    public static String callMistralWithHistory(
            List<Map<String, String>> history) throws Exception {

        if (API_KEY == null || API_KEY.isEmpty()) {
            throw new RuntimeException("MISTRAL_API_KEY is not set");
        }

        List<Map<String, String>> messages = buildMessages(history);

        String payload = mapper.writeValueAsString(
            Map.of(
                "model", "open-mistral-7b",
                "messages", messages
            )
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mistral.ai/v1/chat/completions"))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return extractAssistantReply(response.body());
    }

    // ✅ STREAMING - returns raw InputStream of SSE from Mistral
    public static InputStream getStreamingResponse(
            List<Map<String, String>> history) throws Exception {

        if (API_KEY == null || API_KEY.isEmpty()) {
            throw new RuntimeException("MISTRAL_API_KEY is not set");
        }

        List<Map<String, String>> messages = buildMessages(history);

        // Need mutable map for "stream" boolean
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("model", "open-mistral-7b");
        payloadMap.put("messages", messages);
        payloadMap.put("stream", true);

        String payload = mapper.writeValueAsString(payloadMap);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mistral.ai/v1/chat/completions"))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<InputStream> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        return response.body();
    }

    // Extract delta content from a streaming SSE JSON chunk
    public static String extractDeltaContent(String jsonData) {
        try {
            JsonNode root = mapper.readTree(jsonData);
            JsonNode delta = root.path("choices").get(0).path("delta");
            if (delta.has("content")) {
                return delta.path("content").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Extract full reply from non-streaming response
    private static String extractAssistantReply(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        return root
            .path("choices")
            .get(0)
            .path("message")
            .path("content")
            .asText();
    }
}
