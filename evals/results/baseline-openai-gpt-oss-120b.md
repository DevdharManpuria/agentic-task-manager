# Eval report: baseline

- Model: `openai/gpt-oss-120b`
- Cases: 29
- **Task success rate: 16 / 29 (55%)**
- Avg LLM calls per command: 1.00
- Avg latency: 864 ms (p50 731 ms, p95 1555 ms)
- Tokens: 13761 prompt + 5272 completion

## By category

| Category | Passed | Total |
| --- | --- | --- |
| create | 6 | 6 |
| phrasing | 2 | 2 |
| update-by-id | 3 | 3 |
| update-by-name | 0 | 5 |
| delete-by-id | 1 | 1 |
| delete-by-name | 0 | 3 |
| list | 3 | 3 |
| multi-step | 0 | 3 |
| not-found | 1 | 1 |
| off-topic | 0 | 2 |

## Failures

- **update-name-01** (`mark my report task as complete`)
  - task 'Write quarterly report': status is PENDING, expected COMPLETED
- **update-name-02** (`make the dentist appointment high priority`)
  - task 'Dentist appointment': priority is MEDIUM, expected HIGH
- **update-name-03** (`I've started the laundry`)
  - expected 2 tasks but found 3 [Do laundry, Clean kitchen, Laundry]
  - task 'Do laundry': status is PENDING, expected IN_PROGRESS
- **update-name-04** (`rename the groceries task to 'Weekly groceries'`)
  - no task with title containing 'Weekly groceries' [Buy groceries, Pay electricity bill]
  - a task containing 'Buy groceries' should have been deleted/renamed
- **update-name-05** (`push the call mom task to Monday`)
  - task 'Call mom': dueDate is 2026-11-15, expected 2026-11-16
- **delete-name-01** (`delete the groceries task`)
  - expected 1 tasks but found 2 [Buy groceries, Write report]
  - a task containing 'groceries' should have been deleted/renamed
- **delete-name-02** (`remove the gym task`)
  - expected 1 tasks but found 2 [Gym session, Pay rent]
  - a task containing 'Gym' should have been deleted/renamed
- **delete-name-03** (`cancel my dentist appointment`)
  - expected 1 tasks but found 2 [Dentist appointment, Do laundry]
  - a task containing 'Dentist' should have been deleted/renamed
- **multi-01** (`mark all my high priority tasks as completed`)
  - task 'Fix login bug': status is PENDING, expected COMPLETED
  - task 'Review pull request': status is PENDING, expected COMPLETED
- **multi-02** (`delete all my completed tasks`)
  - expected 1 tasks but found 3 [Submit invoice, File taxes, Plan trip]
  - a task containing 'invoice' should have been deleted/renamed
  - a task containing 'taxes' should have been deleted/renamed
- **multi-03** (`add two tasks: buy milk and call mom`)
  - expected 2 tasks but found 1 [Buy milk]
  - no task with title containing 'mom' [Buy milk]
- **off-topic-01** (`what's the weather like today?`)
  - expected 1 tasks but found 2 [Buy groceries, Check the weather]
- **off-topic-02** (`tell me a joke`)
  - expected 0 tasks but found 1 [Tell a joke]
