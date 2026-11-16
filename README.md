# Agentic Task Manager

A task manager you can drive by typing sentences. Underneath it is a normal Spring Boot REST
API; in front of it is an LLM agent that turns "delete my report task" into real, validated
changes, and asks before anything is deleted.

**The model only proposes. The application validates and executes.** Every action goes through
the same `TaskService` the REST API uses, scoped to the signed-in user, so the agent has no
privileged route to the data.

## What the agent does

For each command the agent runs a loop (`AgentService.processCommand`):

1. Send the conversation and the tool schemas to the model.
2. If the model asks for tools, validate each call and run it, then send the results back.
3. Repeat until the model answers in plain text, or a cap is hit (6 model calls or 30k tokens).

| Capability | How |
| --- | --- |
| Find a task by name | `listTasks` takes `titleContains` (case-insensitive, scoped to the user), so "delete my report task" works without the model guessing an id |
| Multi-step commands | The loop: search, then update or delete, then summarise |
| Self-correction | A bad argument (`priority: "urgent"`, a bad date) goes back to the model as a tool result with a clear message, and the model retries |
| Safe deletes | `deleteTask` only queues a plan. Nothing is deleted until the user calls `POST /api/agent/confirm/{id}`. Plans are single use, expire after 5 minutes and belong to one user |
| Prompt-injection resistance | Task text is untrusted data (labelled as such in the prompt and in every tool result), and even a hijacked model cannot delete without a human confirmation |
| Follow-ups | The last 4 exchanges per user are kept for 15 minutes, so "make that high priority" works |
| Cost control | Per-user rate limit (10 commands/min by default), step cap, token cap, 500-character commands |
| Honest errors | Users see a generic message; the full error and the whole step trace go to MongoDB, one document per command |

## Measured results

The agent is evaluated end to end against the real model: each case seeds tasks, runs one
command, and checks the resulting database state (see `src/test/resources/evals/`).

| Agent | Cases | Passed | Model |
| --- | --- | --- | --- |
| One-shot agent (baseline, before the loop) | 29 | 16 (55%) | openai/gpt-oss-120b |
| Tool-loop agent (this version) | 92 | **not yet measured** | run pending |

The baseline is recorded in `evals/results/baseline-openai-gpt-oss-120b.md`: it failed every
by-name update and delete, every multi-step command and every off-topic request.

The loop version has not had a clean full run yet. The free Groq tier allows 200,000 tokens
per day per model, and development runs used the quota up before a full pass finished. Partial
runs were encouraging (45 of the first 48 cases passed, with the only failures being one wrong
due date, one wrong status and one rename), and they found two real bugs that are now fixed
and covered by tests. Treat those partial runs as anecdotes, not a result. To produce the
number: `mvn test -Pevals -Deval.label=agent-loop`, then compare the original 29 cases with the
baseline.

Run it yourself: `mvn test -Pevals -Deval.label=mylabel` (needs `LLM_API_KEY`). To compare
models: `LLM_MODEL=<model> mvn test -Pevals -Deval.label=<name>`. Reports are written to
`target/eval-report/`.

## Stack

- Java 21, Spring Boot 3.3.4, Maven
- Spring Security with JWT (jjwt 0.12.6), BCrypt
- Spring Data JPA + MySQL (users, tasks), Spring Data MongoDB (agent logs)
- WebClient for the LLM call (any OpenAI-compatible chat-completions endpoint; Groq by default)
- springdoc-openapi, a single-page UI in `src/main/resources/static/index.html`
- JUnit 5 + Mockito (unit tests), H2 (eval runs), GitHub Actions CI

## Running it

**With Docker (one command):**

```bash
cp .env.example .env      # fill in DB_PASSWORD, JWT_SECRET, LLM_API_KEY
docker compose up --build
```

Open http://localhost:8080 for the UI, or `/swagger-ui/index.html` for the API.

**Without Docker:** needs Java 21, Maven, local MySQL and MongoDB.

```bash
export DB_PASSWORD=<mysql password> JWT_SECRET=$(openssl rand -base64 48) LLM_API_KEY=<key>
mvn spring-boot:run
```

Config knobs: `LLM_API_URL`, `LLM_MODEL`, `DB_URL`, `MONGODB_URI`, `AGENT_RATE_LIMIT_PER_MINUTE`.
Local secrets can also go in `application-local.properties` (gitignored).

Tests: `mvn test` (unit tests, no database or network needed).

## API

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | Create an account / sign in, returns a JWT |
| POST/GET/PUT/DELETE | `/api/tasks`, `/api/tasks/{id}` | Conventional task CRUD (list is filterable and paged) |
| POST | `/api/agent/command` | `{ "command": "..." }` returns `{ message, success, steps[], confirmation? }` |
| POST | `/api/agent/confirm/{id}` | Run a delete the agent proposed |
| DELETE | `/api/agent/confirm/{id}` | Throw a proposed delete away |

Everything except the auth routes needs `Authorization: Bearer <token>`.

## Design notes

**Authentication.** `JwtAuthFilter` validates the bearer token before
`UsernamePasswordAuthenticationFilter` and fills the `SecurityContext`. No server-side session.

**Ownership.** All task access goes through `findOwnedTask`, which returns 404 (not 403) for
another user's task so ids cannot be probed. The agent's search is scoped the same way.

**Argument validation.** `ToolArgs` converts the model's raw arguments: case-insensitive enums,
real-date checks, required fields. Failures are written for the model to read and fix.

**Why MongoDB for logs.** A command now has a variable number of steps and each step's
arguments differ per tool. One flexible document per command holds the whole trace.

## Limitations

- Pending deletes, conversation memory and the rate limiter live in memory, so they assume one
  server instance. Running several would need Redis or a database table for them.
- Relative dates are resolved by the model against the date in the system prompt, not by
  application code.
- Tokens have no refresh or revocation; a token is valid for 24 hours.
- The task list has no manual delete button; deleting is done through the agent with confirmation.
- The eval set is small and written by the author. It measures the failure modes the author
  thought of, and results vary a little between runs because the model is non-deterministic.
- There is no hosted demo yet. The Dockerfile and compose file are ready for one.
