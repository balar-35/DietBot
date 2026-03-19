# DietBot

DietBot is a full-stack web application that provides users with a personalized diet and nutrition chat assistant, with a focus on Indian foods. It features a modern React frontend and a secure Spring Boot backend with persistent chat history and user authentication.

---

## Features

- **Conversational Diet Assistant**: Natural, friendly chat interface for nutrition, food, and health advice.
- **Personalized Experience**: Remembers user details (age, height, weight, preferences) for tailored responses.
- **Indian Food Focus**: Specializes in Indian cuisine, but can answer general diet questions.
- **User Authentication**: Secure signup, login, and session management.
- **Persistent Chat History**: Stores and retrieves user chat history.
- **Streaming AI Responses**: Real-time, word-by-word AI replies for a smooth chat experience.

---

## Tech Stack

- **Frontend**: React (Create React App), React Markdown, modern CSS
- **Backend**: Spring Boot (Java), Spring Data JPA, MySQL, Spring Security, REST API, Server-Sent Events (SSE)
- **AI Integration**: Mistral AI API (for chat responses)

---

## Project Structure

```
backend/   # Spring Boot backend (Java)
frontend/  # React frontend (JavaScript)
```

---

## Getting Started

### Prerequisites
- Node.js & npm (for frontend)
- Java 17+ (for backend)
- MySQL (or compatible database)

### 1. Backend Setup

1. Configure your database in `backend/src/main/resources/application.properties`.
2. Set the `MISTRAL_API_KEY` environment variable for AI chat.
3. From the `backend` directory, build and run:
   ```sh
   ./mvnw spring-boot:run
   ```
   The backend runs on [http://localhost:8080](http://localhost:8080)

### 2. Frontend Setup

1. From the `frontend` directory, install dependencies:
   ```sh
   npm install
   ```
2. Start the React app:
   ```sh
   npm start
   ```
   The frontend runs on [http://localhost:3000](http://localhost:3000)

---

## Usage

- Visit [http://localhost:3000](http://localhost:3000)
- Sign up or log in
- Start chatting with DietBot for personalized diet advice

---

## API Endpoints (Backend)

- `POST /auth/signup` — Create a new user
- `POST /auth/login` — Log in
- `POST /auth/logout` — Log out
- `GET /auth/me` — Get current user info
- `POST /chat/stream` — Stream chat response (SSE)
- `POST /chat` — Get chat response (non-streaming)
- `GET /chat/history` — Get chat history
- `DELETE /chat/delete` — Delete chat history

---

## Contribution

Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.

---

## License

This project is for educational/demo purposes. See individual files for license details.
