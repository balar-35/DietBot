import { useState, useEffect } from "react";
import LoginPage from "./LoginPage";
import ChatPage from "./ChatPage";
import "./App.css";

function App() {
  const [user, setUser] = useState(null); // null = loading, false = not logged in, string = username
  const [checking, setChecking] = useState(true);

  // Check if user is already logged in (session still valid)
  useEffect(() => {
    async function checkSession() {
      try {
        const res = await fetch("http://localhost:8080/auth/me", {
          credentials: "include",
        });
        if (res.ok) {
          const data = await res.json();
          setUser(data.username);
        } else {
          setUser(false);
        }
      } catch {
        setUser(false);
      }
      setChecking(false);
    }
    checkSession();
  }, []);

  const handleLogin = (username) => {
    setUser(username);
  };

  const handleLogout = () => {
    setUser(false);
  };

  // Show loading while checking session
  if (checking) {
    return (
      <div className="ios-app">
        <div className="ambient-background">
          <div className="blob blob-1"></div>
          <div className="blob blob-2"></div>
          <div className="blob blob-3"></div>
        </div>
        <div className="loading-screen">
          <div className="loading-brand">
            <div className="loading-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" />
                <path d="M8 14s1.5 2 4 2 4-2 4-2" />
                <line x1="9" y1="9" x2="9.01" y2="9" />
                <line x1="15" y1="9" x2="15.01" y2="9" />
              </svg>
            </div>
            <h1>DietBot</h1>
            <div className="loading-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (!user) {
    return <LoginPage onLogin={handleLogin} />;
  }

  return <ChatPage username={user} onLogout={handleLogout} />;
}

export default App;