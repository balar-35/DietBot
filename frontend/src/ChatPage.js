import { useState, useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import "./App.css";

const SUGGESTIONS = [
    "Healthy breakfast ideas",
    "Low calorie lunch",
    "High protein Indian foods",
    "Calculate my BMI",
];

const WELCOME_MESSAGE = {
    from: "bot",
    text: `Welcome to your **Diet Assistant**! I can help with personalized nutrition advice, especially for **Indian foods**.

Share your **age**, **height**, **weight**, and **activity level** or just ask me anything!`,
};

function ChatPage({ username, onLogout }) {
    const [input, setInput] = useState("");
    const [loading, setLoading] = useState(false);
    const [streaming, setStreaming] = useState(false);
    const [messages, setMessages] = useState([WELCOME_MESSAGE]);
    const [showSuggestions, setShowSuggestions] = useState(true);
    const [notification, setNotification] = useState("");
    const [historyLoaded, setHistoryLoaded] = useState(false);
    const [menuOpen, setMenuOpen] = useState(false);
    const bottomRef = useRef(null);
    const inputRef = useRef(null);
    const menuRef = useRef(null);

    // Load chat history on mount
    useEffect(() => {
        async function loadHistory() {
            try {
                const res = await fetch("http://localhost:8080/chat/history", {
                    credentials: "include",
                });
                if (res.ok) {
                    const data = await res.json();
                    if (data.messages && data.messages.length > 0) {
                        const loaded = data.messages.map((m) => ({
                            from: m.role === "user" ? "user" : "bot",
                            text: m.content,
                        }));
                        setMessages([WELCOME_MESSAGE, ...loaded]);
                        setShowSuggestions(false);
                    }
                }
            } catch {
                // If server is unreachable, just show welcome
            }
            setHistoryLoaded(true);
        }
        loadHistory();
    }, []);

    // Close menu on click outside
    useEffect(() => {
        function handleClickOutside(e) {
            if (menuRef.current && !menuRef.current.contains(e.target)) {
                setMenuOpen(false);
            }
        }
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    // Auto scroll
    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, loading]);

    // Auto focus input
    useEffect(() => {
        if (historyLoaded) {
            setTimeout(() => inputRef.current?.focus(), 100);
        }
    }, [historyLoaded]);

    // Notification auto-dismiss
    useEffect(() => {
        if (notification) {
            const timer = setTimeout(() => setNotification(""), 3000);
            return () => clearTimeout(timer);
        }
    }, [notification]);

    async function send(messageText) {
        const text = typeof messageText === "string" ? messageText : input;
        if (!text.trim() || streaming) return;

        setShowSuggestions(false);
        setMessages((prev) => [...prev, { from: "user", text }]);
        setInput("");
        setLoading(true);
        setStreaming(true);

        try {
            const res = await fetch("http://localhost:8080/chat/stream", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({ message: text }),
            });

            if (res.status === 401) {
                onLogout();
                return;
            }

            if (!res.ok) {
                let errMsg = "Something went wrong...";
                try {
                    const data = await res.json();
                    errMsg = data.error || errMsg;
                } catch { }
                setMessages((prev) => [...prev, { from: "bot", text: errMsg }]);
                return;
            }

            // Add empty bot message and stop typing indicator
            setMessages((prev) => [...prev, { from: "bot", text: "" }]);
            setLoading(false);

            // Read SSE stream
            const reader = res.body.getReader();
            const decoder = new TextDecoder();
            let buffer = "";

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });

                // Process complete lines
                const lines = buffer.split("\n");
                buffer = lines.pop(); // keep incomplete last line

                for (const line of lines) {
                    const trimmed = line.trim();
                    if (trimmed.startsWith("data:")) {
                        const jsonStr = trimmed.slice(5).trim();
                        if (!jsonStr) continue;
                        try {
                            const parsed = JSON.parse(jsonStr);
                            if (parsed.done) continue;
                            if (parsed.content) {
                                setMessages((prev) => {
                                    const updated = [...prev];
                                    const lastIdx = updated.length - 1;
                                    updated[lastIdx] = {
                                        ...updated[lastIdx],
                                        text: updated[lastIdx].text + parsed.content,
                                    };
                                    return updated;
                                });
                            }
                        } catch { }
                    }
                }
            }
        } catch {
            setMessages((prev) => [
                ...prev,
                { from: "bot", text: "Something went wrong..." },
            ]);
        } finally {
            setLoading(false);
            setStreaming(false);
            setTimeout(() => inputRef.current?.focus(), 100);
        }
    }

    async function handleDeleteChat() {
        setMenuOpen(false);
        try {
            const res = await fetch("http://localhost:8080/chat/delete", {
                method: "DELETE",
                credentials: "include",
            });
            if (res.ok) {
                setMessages([WELCOME_MESSAGE]);
                setShowSuggestions(true);
                setNotification("Chat deleted");
                setTimeout(() => inputRef.current?.focus(), 100);
            }
        } catch {
            setNotification("Failed to delete chat");
        }
    }

    async function handleLogout() {
        setMenuOpen(false);
        try {
            await fetch("http://localhost:8080/auth/logout", {
                method: "POST",
                credentials: "include",
            });
        } catch {
            // logout anyway
        }
        onLogout();
    }

    return (
        <div className="ios-app">
            {/* Abstract dynamic background */}
            <div className="ambient-background">
                <div className="blob blob-1"></div>
                <div className="blob blob-2"></div>
                <div className="blob blob-3"></div>
            </div>

            {/* Notification toast */}
            {notification && (
                <div className="toast-notification">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                        <polyline points="22 4 12 14.01 9 11.01" />
                    </svg>
                    {notification}
                </div>
            )}

            {/* Main chat interface */}
            <div className="chat-container">
                {/* Header with menu */}
                <header className="ios-header">
                    <h1>DietBot</h1>
                    <div className="header-right">
                        <span className="header-username">{username}</span>
                        <div className="menu-wrapper" ref={menuRef}>
                            <button
                                className="menu-toggle-btn"
                                onClick={() => setMenuOpen(!menuOpen)}
                                aria-label="Menu"
                            >
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <circle cx="12" cy="5" r="1" />
                                    <circle cx="12" cy="12" r="1" />
                                    <circle cx="12" cy="19" r="1" />
                                </svg>
                            </button>
                            {menuOpen && (
                                <div className="dropdown-menu">
                                    <button className="dropdown-item delete-item" onClick={handleDeleteChat}>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                            <polyline points="3 6 5 6 21 6" />
                                            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                                            <line x1="10" y1="11" x2="10" y2="17" />
                                            <line x1="14" y1="11" x2="14" y2="17" />
                                        </svg>
                                        Delete Chat
                                    </button>
                                    <div className="dropdown-divider"></div>
                                    <button className="dropdown-item logout-item" onClick={handleLogout}>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                                            <polyline points="16 17 21 12 16 7" />
                                            <line x1="21" y1="12" x2="9" y2="12" />
                                        </svg>
                                        Logout
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>
                </header>

                {/* Messages area */}
                <div className="ios-messages">
                    <div className="messages-padding-top"></div>

                    {messages.map((m, i) => (
                        <div key={i} className={`message-wrapper ${m.from}`}>
                            <div className="ios-bubble">
                                <ReactMarkdown>{m.text}</ReactMarkdown>
                            </div>
                        </div>
                    ))}

                    {showSuggestions && messages.length === 1 && !loading && (
                        <div className="ios-chips-container">
                            {SUGGESTIONS.map((s, i) => (
                                <button
                                    key={i}
                                    className="ios-chip"
                                    onClick={() => send(s)}
                                    style={{ animationDelay: `${i * 0.1}s` }}
                                >
                                    {s}
                                </button>
                            ))}
                        </div>
                    )}

                    {loading && (
                        <div className="message-wrapper bot">
                            <div className="ios-bubble typing">
                                <span></span><span></span><span></span>
                            </div>
                        </div>
                    )}
                    <div ref={bottomRef} className="messages-padding-bottom" />
                </div>

                {/* Input area */}
                <div className="ios-input-area">
                    <div className="input-glass-panel">
                        <input
                            ref={inputRef}
                            value={input}
                            onChange={(e) => setInput(e.target.value)}
                            onKeyDown={(e) => e.key === "Enter" && send()}
                            placeholder={streaming ? "Waiting for response..." : "Ask about your diet..."}
                            disabled={streaming}
                        />
                        <button
                            className={`ios-send-btn ${input.trim() && !streaming ? "active" : ""}`}
                            onClick={send}
                            disabled={!input.trim() || streaming}
                        >
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <line x1="12" y1="19" x2="12" y2="5" />
                                <polyline points="5 12 12 5 19 12" />
                            </svg>
                        </button>
                    </div>
                    {/* Disclaimer */}
                    <div className="chat-disclaimer">
                        Dietbot can be wrong. Always seek professional advice.
                    </div>
                </div>
            </div>
        </div>
    );
}

export default ChatPage;
