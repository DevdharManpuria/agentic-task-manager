# Agentic Task Manager

A Spring Boot task-management API with two ways in: a conventional REST interface, and a
natural-language endpoint where an LLM chooses which operation to run via tool calling.

Both paths go through the same service layer, so the agent has no privileged route to the
database — it can only propose a call that the application then validates and executes.

## Stack

- Java 21, Spring Boot 3.3.4, Maven
- Spring Security with JWT (jjwt 0.12.6), BCrypt password hashing
- Spring Data JPA + MySQL for users and tasks
- Spring Data MongoDB for agent interaction logs
- WebClient (Spring WebFlux) for the LLM call
- springdoc-openapi for API docs

## Running it

Requires Java 21, Maven, and local MySQL and MongoDB instances.

Set these environment variables before starting:

```bash
export DB_USERNAME=root
export DB_PASSWORD=<your mysql password>
export JWT_SECRET=$(openssl rand -base64 32)
export LLM_API_KEY=<key for any OpenAI-compatible chat-completions endpoint>
```

`app.llm.api-url` and `app.llm.model` in `application.properties` are set to a
Groq-hosted model by default; any provider exposing the OpenAI chat-completions
shape works without code changes.

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. The MySQL schema is created automatically.
Swagger UI is at `/swagger-ui/index.html`.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/register` | Create an account, returns a JWT |
| POST | `/api/auth/login` | Authenticate, returns a JWT |
| POST | `/api/tasks` | Create a task |
| GET | `/api/tasks/{id}` | Fetch one task |
| GET | `/api/tasks` | List tasks, filterable by `status` and `priority`, paginated |
| PUT | `/api/tasks/{id}` | Update a task (partial — unset fields are left alone) |
| DELETE | `/api/tasks/{id}` | Delete a task |
| POST | `/api/agent/command` | Natural-language command, dispatched via LLM tool calling |

Every endpoint except the two auth routes requires `Authorization: Bearer <token>`.

Example agent command:

```json
POST /api/agent/command
{ "command": "remind me to finish the report by Friday" }
```

The model selects one of four tools (`createTask`, `updateTask`, `deleteTask`,
`listTasks`), the application executes it against `TaskService`, and the interaction is
written to the `agent_interaction_logs` collection in MongoDB.

## Design notes

**Authentication.** `JwtAuthFilter` extends `OncePerRequestFilter` and runs ahead of
`UsernamePasswordAuthenticationFilter`. It validates the bearer token and populates the
`SecurityContext`. No session state is held server-side.

**Ownership.** Every task read or mutation routes through `findOwnedTask`, which compares
the task's owner against the authenticated user. A mismatch returns 404 rather than 403,
so one user cannot probe for the existence of another user's task IDs.

**Filtering.** `listTasks` builds a JPA `Specification` from whichever subset of
`status` and `priority` the caller supplied, always ANDed with the current user's ID,
then applies the incoming `Pageable`.

**Why MongoDB for logs.** Tool arguments have a different shape per tool — `createTask`
carries a title and due date, `deleteTask` carries only an ID. A document store avoids
either a wide nullable-column table or one table per tool.

## Limitations

- Relative dates are resolved by the model rather than in application code. The current
  date is injected into the system prompt, which works in practice, but constraining this
  with a date-parsing library in Java would be more reliable than trusting the model's
  arithmetic.
- The agent endpoint has no rate limiting, and each call costs an upstream API request.
- Tokens have no refresh mechanism; a token is valid for 24 hours and cannot be revoked
  before expiry.
- Conversation history is not carried between agent commands — each command is resolved
  independently, so follow-ups like "actually make that high priority" will not work.