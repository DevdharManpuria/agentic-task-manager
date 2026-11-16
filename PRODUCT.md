# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Two audiences, equally weighted (confirmed by the owner):
- People who manage their own tasks and want to do it by typing a sentence instead of filling forms.
- Recruiters and interviewers who open the project as a portfolio piece and judge it in a minute or two.

## Product Purpose

A task manager with a natural-language agent in front of it. The user types "move the report to Friday and make it high priority"; an LLM proposes tool calls, and application code validates and runs them against the same task service the REST API uses. Success is correct task changes the user can trust, and an agent whose behaviour is visible and measurable.

## Positioning

The model only proposes; the application validates and executes, scoped to the signed-in user. Deletes never run on the model's say-so: they wait for the user's confirmation. Agent quality is measured with an eval set rather than asserted.

## Operating Context

Spring Boot REST API with JWT login, MySQL (users, tasks), MongoDB (agent logs). Single-page UI served from the app's static folder. The LLM is any OpenAI-compatible chat-completions endpoint (Groq by default).

## Capabilities and Constraints

- Agent tools: create, update, delete (confirmation required), list/search tasks. Short conversation memory for follow-ups. Per-user rate limit.
- Fixed by the owner: deletes always need confirmation; light and dark themes both supported.
- Not decided: hosted deployment target, refresh tokens/revocation, a manual delete control in the task list.

## Evidence on Hand

- Eval set of 92 commands (`src/test/resources/evals/`), with a recorded baseline of the earlier one-shot agent: 16/29 (55%) in `evals/results/`.
- No user testimonials or usage data exist; do not invent any.

## Product Principles

1. The agent's actions are visible: the user can see what it did, not just what it said.
2. Anything destructive asks first.
3. Task text is untrusted data, in the UI as in the prompt.
4. Claims about quality come from measured results.

## Accessibility & Inclusion

Keyboard operable, visible focus, readable contrast in both themes, reduced-motion respected.
