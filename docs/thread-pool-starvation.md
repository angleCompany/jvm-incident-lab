# Incident 001: thread pool starvation

## Question

A service stops making progress, but there is no synchronized-lock cycle. Why?

## Hypothesis and trigger

A fixed-size pool executes parent tasks that synchronously wait for child tasks submitted to that same pool. If every worker is occupied by a waiting parent, children cannot start.

This lab uses four parents and four workers. A latch holds all parent workers until every parent has entered. Only then are child submissions released. Each child would sleep for 50 ms and return `1` if it could execute.

This is intentionally a controlled in-process workload. It does not simulate HTTP request saturation, a database, CPU utilization, or an entire production deployment.

## Investigation

1. Run `./jvmlab run starved` or use the dashboard.
2. Check the actual counters: four active parents, four queued children, zero child starts, zero completed parents.
3. Inspect `threads.txt`: parent workers should be parked in `FutureTask.get`, with the parent task in `Experiment` below it.
4. Inspect `recording.jfr`: cancellation finishes the parked waits before the recording stops, retaining completed `jdk.ThreadPark` events with wait stacks. Thread dumps are captured before cancellation and provide the primary snapshot evidence.
5. Do not infer the queue size from stacks alone; use the executor queue measurement.

A thread waiting on a future is normal in many programs. The combined observations, plus the known executor dependency in the source, establish this experiment's failure mechanism.

## Repair

Use a separate child executor. Keep the same number of parents, same child work, and same parent waiting code. Run `./jvmlab run fixed`.

Expected: all four child tasks start, all four parents complete, and the child queue is empty. Parent workers may already be idle in the snapshot.

Other production designs include nonblocking composition, eliminating nested submission, or limiting parent concurrency so children retain capacity. A larger shared pool can mask the failure until the same capacity condition reappears.

## Verification contract

- Failure: `completed=0`, `childrenStarted=0`, `queued=4`, `active=4` at the bounded observation point.
- Fix: `completed=4`, `childrenStarted=4`, `queued=0`.
- Both runs: artifacts written, parent futures cancelled if needed, executors stopped and termination checked.
- Extremely overloaded hosts can miss the expected observation window. The tool must report an unexpected result, never substitute expected numbers.

The one-second observation limit is not a throughput measurement. JFR startup, recording, snapshots, and filesystem work add overhead. Do not interpret the elapsed-time ratio as a speedup.

## Source map

- `src/main/java/dev/jvmlab/Experiment.java`: trigger, counters, snapshots, cleanup, artifacts.
- `src/main/java/dev/jvmlab/Main.java`: CLI and loopback HTTP API.
- `src/main/resources/index.html`: live comparison dashboard.
- `tests/verify.py`: real JVM integration checks.
