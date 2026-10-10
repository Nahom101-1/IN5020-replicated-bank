# IN5020 Assignment 2 – Replicated Bank Account

project structure only. Nothing is implemented yet.

## The assignment
- A **Message Delivery server** receives transactions from bank servers, broadcasts each message
  to all of them, and waits for every ACK before sending the next one.
    - No ACK after 2s: resend to that bank server.
    - No ACK after 5s: mark it as failed and remove it.
- Several **bank servers** each hold a replica of the same account.
    - Users issue commands through a command line or a batch file.
    - Updates (`deposit`, `addInterest`) are broadcast every 10s and applied in the same order
      on every replica, so all balances stay equal.
- The system must handle replicas joining late and replicas leaving or crashing.
- `getSyncedBalance` must be implemented in two ways, a naive one and a correct one, and the
  difference between them explained.

Deadline: Thursday 15 Oct 2026, 23:59.

## Project structure
```
src/main/java/com/ass2/
├── common/     shared RMI interfaces, Transaction, constants
├── delivery/   Message Delivery server
└── bank/       bank server
    ├── core/   replicated account state
    ├── cli/    interactive and batch input
    ├── sync/   getSyncedBalance implementations
    └── util/   logging
input/          currency and batch files
logs/           output logs from all replicas
scripts/        run scripts
```

## Build
```bash
mvn package
```
This produces `target/bank.jar`. For now, both main classes only print a placeholder.

## Workload distribution
| Member | Responsibility |
|---|---|
|  | Message Delivery server |
|  | Bank core (replicated state) |
|  | Bank CLI, commands, logging |
|  | getSyncedBalance, testing, delivery |