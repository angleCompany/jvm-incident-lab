# JVM Incident Lab — Java Thread Pool Starvation Lab

**Reproduce Java thread pool starvation. Inspect thread dumps and Java Flight Recorder (JFR). Verify the fix.**

JVM Incident Lab is an open-source, hands-on **Java concurrency and JVM troubleshooting lab**. Run a bounded `ExecutorService` / `Future.get()` failure locally, inspect real worker stacks and JFR recordings, then compare a separate-executor fix.

Use it to learn **thread dump analysis**, practice Java debugging, or teach a team why nested blocking tasks can stop a fixed-size thread pool. The dashboard and offline HTML reports support **English and 한국어**.

[![Verify incident lab](https://github.com/angleCompany/jvm-incident-lab/actions/workflows/verify.yml/badge.svg)](https://github.com/angleCompany/jvm-incident-lab/actions/workflows/verify.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Status: v0.1 preview. One scenario implemented.** Not a production diagnostic agent or a performance benchmark.

[한국어](README.ko.md) · [Scenario walkthrough](docs/thread-pool-starvation.md) · [First-run feedback](https://github.com/angleCompany/jvm-incident-lab/issues/new?template=first-run-feedback.yml) · [Contributing](CONTRIBUTING.md)

[![30-second walkthrough: real JVM failure and fix results](docs/media/demo-poster.png)](docs/media/demo.mp4)

**[Watch the 30-second demo](docs/media/demo.mp4)** — silent, edited walkthrough using actual JVM results; not a benchmark.

<details>
<summary>Actual dashboard after running failure → fix</summary>

![Actual local dashboard after running the failure and fix](docs/dashboard.jpg)

</details>

## Quick start: reproduce Java thread pool starvation

Requires a **JDK 21 or newer** (`java`, `javac`, and `jfr`). No Maven, Gradle, database, API key, or third-party Java dependencies.

```sh
git clone https://github.com/angleCompany/jvm-incident-lab.git
cd jvm-incident-lab
./jvmlab
```

Open **http://127.0.0.1:8787** and click **Run failure → fix**. The dashboard starts with empty measurements; all displayed results come from real JVM runs.

Use **English / 한국어** in the dashboard or an offline report to switch language. The initial language follows your browser, with your selection saved when storage is available. `?lang=en` and `?lang=ko` provide direct links. Code, raw JVM stacks, and JSON field names stay unchanged when you switch.

For a headless comparison:

```sh
./jvmlab run compare
```

Windows: use WSL with JDK 21+. Native Windows launcher support is not implemented.

## Thread pool starvation: failure vs. fix

| Evidence | Shared pool: failure | Separate child pool: fix |
|---|---:|---:|
| Parent tasks | 4 | 4 |
| Completed parents | 0 | 4 |
| Children that started | 0 | 4 |
| Queued children at snapshot | 4 | 0 |

Every parent occupies a worker, submits a child into the **same** executor, then blocks on `Future.get()`. No worker is available to run the children. In the fixed case, children have a separate executor.

A start barrier ensures all four parent workers are occupied before any child is submitted. The starved run is observed for one second and then cancelled. Both executors are shut down and termination is checked. If the expected state is not observed, the report says so and the CLI returns a nonzero exit code.

This is **dependency starvation**, not a JVM monitor deadlock. A separate pool fixes this example; it does not make all nested blocking workloads safe.

## Thread dumps and Java Flight Recorder (JFR) evidence

Each run creates a unique directory under `artifacts/`:

```text
artifacts/<run-id>/
├── report.html     # Self-contained, offline evidence report
├── result.json     # Measured counters and expected-behavior verdict
├── threads.txt     # Real experiment-worker stacks, captured before cleanup
└── recording.jfr   # Thread park / monitor / sampling events
```

Open `report.html` directly in a browser. Open `recording.jfr` in JDK Mission Control, or inspect it with the JDK CLI:

```sh
jfr print --events jdk.ThreadPark artifacts/<run-id>/recording.jfr
```

The snapshot deliberately includes only lab worker threads. The JFR recording covers the whole **lab JVM**, so other lab process events may be present. No attach to another JVM, external upload, or background daemon occurs.

For the fixed run, workers may already be idle when captured. This is expected: completed task counters provide the outcome evidence, not the existence of a running child in that snapshot.

## Commands

```sh
./jvmlab --help
./jvmlab serve 8787                 # loopback only
./jvmlab serve 8788 /tmp/lab-runs
./jvmlab run starved
./jvmlab run fixed
./jvmlab run compare /tmp/lab-runs
```

The launcher builds into a disposable temporary directory. `JAVA_HOME` is respected when set. Dashboard runs are serialized; an overlapping request receives HTTP 409. Stop the server with Ctrl+C. Delete `artifacts/` when you no longer need the evidence.

## Docker: headless comparison

```sh
docker build -t jvm-incident-lab .
docker run --name jvmlab-demo jvm-incident-lab
docker cp jvmlab-demo:/artifacts ./docker-artifacts
docker rm jvmlab-demo
```

The image runs the CLI as a non-root user. Its default command performs a comparison and exits. The dashboard intentionally binds to loopback; a published Docker port will not expose it. The Docker build must be verified on a host with a running Docker engine.

## Verify

With JDK 21+ and Python 3.10+:

```sh
python3 tests/verify.py
```

Integration checks repeat the real comparison, validate counters and saved artifacts, parse JFR park events, exercise HTTP errors and cross-origin rejection, reject concurrent experiments, and run a successful fix afterward. The initial implementation passed [CI on JDK 21/25 and Linux/macOS](https://github.com/angleCompany/jvm-incident-lab/actions/runs/38774522266). See [current CI](https://github.com/angleCompany/jvm-incident-lab/actions/workflows/verify.yml) for subsequent changes.

## Java concurrency troubleshooting FAQ

### Why can Future.get() hang in a fixed thread pool?

A parent task can submit child work to the same `ExecutorService` and then wait for that work with `Future.get()`. When every worker is occupied by a waiting parent, queued children have no worker to execute them. Incident 001 reproduces this thread pool starvation pattern with four workers and four parents.

### Is thread pool starvation the same as a Java deadlock?

This example has no JVM monitor-lock cycle. It is a capacity dependency: parent tasks hold worker capacity while waiting for child tasks that need the same capacity. A monitor deadlock detector alone does not explain that queue dependency. See the [step-by-step thread dump investigation](docs/thread-pool-starvation.md).

### What does the JFR recording show?

The lab records Java Flight Recorder `jdk.ThreadPark` events with stacks, along with monitor, sampling, and thread lifecycle events. Inspect the recording in JDK Mission Control (JMC), or use `jfr print`. Combine that evidence with measured executor counters and `threads.txt`.

### Is this an alternative to Arthas, async-profiler, or JDK Mission Control?

Those tools diagnose or inspect JVM behavior. This project supplies a reproducible Java failure and a specific before/after verification contract. You can inspect its evidence with existing tools; it does not attach to an arbitrary production JVM or automatically find its root cause.

### Who is this lab for?

Java developers learning concurrency, engineers practicing JVM incident investigation, and teams preparing a short troubleshooting exercise. The first scenario requires only JDK 21+; it works without Spring Boot, a database, or an AI service.

## Help test the first five minutes

Try the lab using only this README: run the failure and fix, find one piece of failure evidence, and explain what changed. If you get stuck, stop after five minutes. Both failures and successful runs help.

[Submit first-run feedback](https://github.com/angleCompany/jvm-incident-lab/issues/new?template=first-run-feedback.yml). Do not include credentials or private production dumps.

The [five-person study protocol](docs/first-run-study.md) defines how we will evaluate usability; no external-user results have been collected yet.

## Scope and direction

Implemented: one bounded thread-pool starvation scenario, failure/fix comparison, local web dashboard, offline HTML/JSON/thread/JFR evidence, integration checks, and CI configuration.

Next candidates, subject to user feedback:

- Connection pool exhaustion with long-held connections and recovery checks.
- Lock contention with controlled concurrency and before/after evidence.
- A scenario contribution contract and curated evidence corpus.

The goal is an **executable incident knowledge base**: each scenario contains its trigger, symptoms, observable evidence, a specific repair, and a verification contract. Existing profilers and analyzers remain useful for exploring the evidence. This project does not replace them or automatically diagnose an arbitrary production outage.

If this runnable Java debugging lab is useful, **star the repository** to bookmark it. Bug reports, first-run feedback, and reproducible scenario contributions help shape what comes next.

## License

[MIT](LICENSE).
