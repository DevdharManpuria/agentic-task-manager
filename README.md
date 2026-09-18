# Agentic Task Manager

A Spring Boot REST API for task management with:
- JWT-secured, user-scoped authentication (Spring Security)
- Full CRUD + filtering + pagination over tasks (MySQL, JPA/Hibernate)
- An agentic AI command endpoint: natural-language input -> LLM function-calling ->
  automatic dispatch to the correct CRUD operation
- Polyglot persistence: MySQL for relational user/task data, MongoDB for agent
  interaction logs

## Prerequisites

- Java 21
- Maven
- MySQL running locally (or update `spring.datasource.*` in `application.properties`)
- MongoDB running locally (or update `spring.data.mongodb.uri`)
- An LLM API key that supports function/tool calling (OpenAI, or adapt `LlmClient`
  for Gemini/Anthropic)

## Setup

1. Clone/open this project.
2. In `src/main/resources/application.properties`:
   - Set your MySQL username/password (the DB itself is auto-created).
   - Set your MongoDB URI if not running on default localhost.
   - Set `app.llm.api-key` to your real API key.
   - A JWT secret is already generated for you, but for production/anything public,
     regenerate your own with: `openssl rand -base64 32`
3. Run: `mvn spring-boot:run`
4. App starts on `http://localhost:8080`
5. Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Testing the flow end-to-end (Postman or curl)

### 1. Register
```
POST /api/auth/register
{
  "username": "devdhar",
  "email": "devdhar@example.com",
  "password": "password123"
}
```
Response includes a `token` - copy it.

### 2. Use the token on every subsequent request
```
Authorization: Bearer <token>
```

### 3. Create a task manually
```
POST /api/tasks
{
  "title": "Finish resume",
  "priority": "HIGH",
  "dueDate": "2026-09-01"
}
```

### 4. List tasks with filtering + pagination
```
GET /api/tasks?status=PENDING&page=0&size=10
```

### 5. Use the agent endpoint
```
POST /api/agent/command
{
  "command": "remind me to finish the report by Friday"
}
```
This should:
- Call the LLM with your 4 tool schemas
- Get back a `createTask` tool call with extracted title/dueDate
- Execute it against the real TaskService
- Log the interaction to MongoDB (`agent_interaction_logs` collection)
- Return which tool was used and the result

Try also:
```
{ "command": "what tasks do I have?" }
{ "command": "mark task 1 as completed" }
{ "command": "delete task 1" }
```

## Known limitation worth knowing about (be ready to discuss this)

The LLM is instructed to resolve relative dates ("Friday", "tomorrow") into
YYYY-MM-DD itself. This mostly works but isn't 100% reliable - it depends on the
model's training and the system prompt. A more robust version would resolve
relative dates in Java code (e.g. with a small date-parsing library) rather than
trusting the LLM's date math. This is a good thing to mention proactively in an
interview: it shows you understand the tradeoff between "let the model handle it"
and "constrain it with code," which is a real, current debate in agentic system
design.

## Architecture notes for interview prep

- **Auth flow**: JwtAuthFilter runs once per request, extracts + validates the JWT,
  and populates SecurityContext - no server-side session state (stateless).
- **User scoping**: every Task query/mutation goes through `findOwnedTask`, which
  checks `task.getUser().getId()` against the authenticated user's id.
- **Agent flow**: user command -> LlmClient sends it + tool schemas to the LLM ->
  LLM returns a tool_call (name + arguments) -> AgentService.dispatch() maps that
  to the exact same TaskService methods the REST API uses -> result logged to Mongo.
  The LLM never touches the database directly - it only ever proposes a function
  call which our own code decides whether/how to execute.
- **Why MongoDB for logs**: the shape of "tool arguments" differs per tool
  (createTask has title/dueDate, deleteTask has just an id) - a flexible document
  avoids either a wide nullable-column table or one table per tool in MySQL.
