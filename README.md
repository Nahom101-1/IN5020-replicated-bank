# IN5020 Assignment 2 – Replicated Bank Account

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

## Project structure
```
src/main/java/com/ass2/
├── common/     shared RMI interfaces, Transaction, ReplicaState, constants
├── delivery/   Message Delivery server
└── bank/       bank server
    ├── core/   replicated account state
    ├── cli/    interactive and batch input
    ├── sync/   getSyncedBalance implementations
    └── util/   logging
input/          currency file, batch files and the graded scenario (input/README.txt)
logs/           output logs from all replicas
scripts/        build and run scripts
```

## Build
```bash
scripts/build.sh
```
This produces `target/bank.jar`. It runs `mvn package` if Maven is installed (which also runs the
unit tests), and otherwise compiles with `javac` and packages with `jar`. Java 21 or newer is required.

## Run
Start the Message Delivery server first, then the bank servers.

```bash
# Message Delivery server, RMI registry on port 1099 (the default)
scripts/run-delivery.sh 1099

# A bank server in interactive mode
scripts/run-bank.sh <server ip:port> <account name> <number of replicas> <currency file>

# A bank server in batch mode: runs one command from the file every 0.5-1.5 s, then exits
scripts/run-bank.sh 10.0.0.5:1099 group01 3 input/TradingRate.txt input/Rep1.txt
```

Without the scripts:
```bash
java -cp target/bank.jar com.ass2.delivery.DeliveryServerMain 1099
java -Dsync=ordered -Dname=rep1-a -jar target/bank.jar 10.0.0.5:1099 group01 3 input/TradingRate.txt input/Rep1.txt
```

**Instance names.** Every bank server needs a name that is unique in the group; its transaction
ids are `<name> <counter>`. Set it with `-Dname=<name>` (`NAME=<name>` for `run-bank.sh`). Without
it the name is `<host name>-<process id>`.

**What a bank server does.** It reads the currency file, connects to the delivery server, joins
the group `<account name>` (a replica that joins later first receives the state of an existing
one), waits until `<number of replicas>` bank servers are in the group, broadcasts its
`outstanding_collection` every 10 s and runs the commands. Every applied transaction is logged as
`Applied <n>. <command> (<id>)`, with the same numbers on every replica. On `exit` it sends what is
still outstanding, waits until that has been delivered, and leaves the group.

**Several machines.** RMI calls go both ways: the bank servers call the delivery server, and
the delivery server calls back to every bank server. Each JVM must therefore announce an address
the others can reach. Set `HOST` to the machine's own IP address:
```bash
HOST=10.0.0.5 scripts/run-delivery.sh 1099                                   # on 10.0.0.5
HOST=10.0.0.6 scripts/run-bank.sh 10.0.0.5:1099 group01 3 input/TradingRate.txt   # on 10.0.0.6
```
(this sets `-Djava.rmi.server.hostname`). Firewalls must allow the registry port and the
ports RMI picks for exported objects.

**The graded scenario.** `input/README.txt` asks for 3 replicas running `Rep1.txt` at t=0,
2 running `Rep2.txt` at t=7 s and 1 running `Rep3.txt` at t=12 s. This script runs it on one
machine and writes one log per replica to `logs/<mode>/`:
```bash
scripts/run-scenario.sh ordered     # correct getSyncedBalance
scripts/run-scenario.sh naive       # naive getSyncedBalance
```

**Currency file.** Each line is `<Currency> <rate to USD>`, e.g. `NOK 0.10`. USD itself is not
listed; its rate is always 1. `getQuickBalance` and `getSyncedBalance` without a currency answer
in USD.

## getSyncedBalance: the two implementations
Choose the implementation with `-Dsync=naive` or `-Dsync=ordered` (the default), or with
`SYNC=naive` for the run scripts. Both are in `com.ass2.bank.sync`.

`getSyncedBalance` must print the balance after every `deposit` and `addInterest` this server
issued *before* the request has been applied, and none issued after it. Neither version blocks
the command line: the request is registered and the answer is printed when it is ready, with
how long it waited, e.g. `getSyncedBalance NOK: 110.00 (ordered, waited 9.8 s)`.

### Naive: wait until `outstanding_collection` is empty (`NaiveSyncedBalance`)
The request waits until `outstanding_collection` is empty and then prints the balance. The
condition is checked when the request is made and after every delivery, since that is the only
time the collection shrinks.

The problem is that the condition looks at the whole collection, not only at the transactions
issued before the request. `outstanding_collection` is broadcast every 10 s, while a batch file
issues a new command every 0.5-1.5 s. By the time one round has been delivered, newer
transactions are already waiting in the collection, so it may never be empty and the request is
never answered (starvation; the assignment calls it deadlock). When it does get answered, the
balance may include deposits issued after the request, so it is not even the right value.
Requests still waiting when the server exits are printed as `never answered`.

### Correct: a marker in the total order (`OrderedSyncedBalance`)
The request appends a marker transaction `getSyncedBalance <Currency>` to
`outstanding_collection`, behind the transactions this server issued before it. It is broadcast
with them and therefore gets a fixed place in the total order that all replicas share. When the
marker is delivered, exactly the transactions ordered before it have been applied, so the
balance is the one at the time of the request. It is answered within one broadcast round,
however many commands arrive in the meantime.

Markers have their own ids, `<name> sync-<n>`, so deposits and addInterest keep the ids
0, 1, 2, ... that `checkTxStatus` refers to. Unlike `deposit` and `addInterest`, the marker:
- is executed only by the replica that issued it (the others skip it);
- is removed from `outstanding_collection` but not added to `executed_list`, and does not
  increase `order_counter`, so all replicas keep identical histories and counters.

The delivery server may deliver the same message twice (it resends after 2 s without an ACK),
so each marker is answered only the first time it is delivered.

### Example
The unit tests (`SyncedBalanceTest`) issue `deposit NOK 100`, then `getSyncedBalance NOK`,
then `deposit NOK 50`, and deliver one broadcast round:

| | Naive | Ordered |
|---|---|---|
| Answer | 150.00 (includes the later deposit) | 100.00 |
| New deposits keep arriving every round | never answered | answered after one round |

<!-- TODO after the scenario runs: add the observed difference from logs/naive and logs/ordered -->

## Workload distribution
| Member | Responsibility |
|---|---|
| Abdimalik | Message Delivery server: RMI registry, join/leave, total-order broadcast with resend and failure detection, membership notifications, state transfer to new members |
| Nahom Berhane | Bank core: per-currency balances, parsing and applying operations, `Account` with exactly-once delivery, history and counters |
| Huan Lee | Bank server process: argument handling, interactive and batch input, command parsing, timestamped logging |
| Alexander Gran Makarov | `getSyncedBalance` (naive and ordered) and its integration into `Account`, tests, build and run scripts, README |
