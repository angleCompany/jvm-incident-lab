# JVM Incident Lab

**Reproduce the failure. Read the evidence. Verify the fix.**

A local, runnable lab for Java production failure patterns. Start with thread pool starvation: four workers, four queued children, and zero progress. Then run the same workload with a separate child executor.

**Status: v0.1 preview. One scenario implemented.** Not a production diagnostic agent or a performance benchmark.

[한국어](README.ko.md) · [Scenario walkthrough](docs/thread-pool-starvation.md) · [First-run feedback](https://github.com/angleCompany/jvm-incident-lab/issues/new?template=first-run-feedback.yml) · [Contributing](CONTRIBUTING.md)

[![30-second walkthrough: real JVM failure and fix results](docs/media/demo-poster.png)](docs/media/demo.mp4)

**[Watch the 30-second demo](docs/media/demo.mp4)** — silent, edited walkthrough using actual JVM results; not a benchmark.

<details>
<summary>Actual dashboard after running failure → fix</summary>

![Actual local dashboard after running the failure and fix](docs/dashboard.jpg)

</details>

## Try it

Requires a **JDK 21 or newer** (`java`, `javac`, and `jfr`). No Maven, Gradle, database, API key, or third-party Java dependencies.

```sh
git clone https://github.com/angleCompany/jvm-incident-lab.git
cd jvm-incident-lab
./jvmlab
```

Open **http://127.0.0.1:8787** and click **Run failure → fix**. The dashboard starts with empty measurements; all displayed results come from real JVM runs.

For a headless comparison:

```sh
./jvmlab run compare
```

Windows: use WSL with JDK 21+. Native Windows launcher support is not implemented.

## What you should observe

| Evidence | Shared pool: failure | Separate child pool: fix |
|---|---:|---:|
| Parent tasks | 4 | 4 |
| Completed parents | 0 | 4 |
| Children that started | 0 | 4 |
| Queued children at snapshot | 4 | 0 |

Every parent occupies a worker, submits a child into the **same** executor, then blocks on `Future.get()`. No worker is available to run the children. In the fixed case, children have a separate executor.

A start barrier ensures all four parent workers are occupied before any child is submitted. The starved run is observed for one second and then cancelled. Both executors are shut down and termination is checked. If the expected state is not observed, the report says so and the CLI returns a nonzero exit code.

This is **dependency starvation**, not a JVM monitor deadlock. A separate pool fixes this example; it does not make all nested blocking workloads safe.

## Evidence you can inspect

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

## License

[MIT](LICENSE).
