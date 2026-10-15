package dev.jvmlab;

import java.lang.management.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.jfr.Recording;

/** A bounded experiment: cancellation always releases the deliberately starved workers. */
public final class Experiment {
    public record Result(String id, String mode, int workers, int parents, int childrenStarted,
                         int completed, int queued, int active, long elapsedMs,
                         boolean expected, String dump, String report, String recording) {
        public String json() {
            return "{\"id\":" + quote(id) + ",\"mode\":" + quote(mode) +
                ",\"workers\":" + workers + ",\"parents\":" + parents +
                ",\"childrenStarted\":" + childrenStarted + ",\"completed\":" + completed +
                ",\"queued\":" + queued + ",\"active\":" + active +
                ",\"elapsedMs\":" + elapsedMs + ",\"expected\":" + expected +
                ",\"dump\":" + quote(dump) + ",\"report\":" + quote(report) +
                ",\"recording\":" + quote(recording) + "}";
        }
    }

    public static Result run(String mode, Path output) throws Exception {
        if (!Set.of("starved", "fixed").contains(mode)) throw new IllegalArgumentException("mode must be starved or fixed");
        int size = 4;
        String id = Instant.now().toString().replace(':', '-') + "-" + mode + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path directory = output.toAbsolutePath().resolve(id);
        Files.createDirectories(directory);
        String prefix = "lab-" + id + "-";
        ThreadPoolExecutor parents = pool(size, prefix + "parent-");
        ThreadPoolExecutor children = mode.equals("fixed") ? pool(size, prefix + "child-") : parents;
        CountDownLatch occupied = new CountDownLatch(size);
        CountDownLatch submit = new CountDownLatch(1);
        AtomicInteger childrenStarted = new AtomicInteger();
        List<Future<Integer>> futures = new ArrayList<>();
        long start = System.nanoTime();
        Result result;
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
            recording.enable("jdk.ThreadStart");
            recording.enable("jdk.ThreadEnd");
            recording.start();
            for (int i = 0; i < size; i++) {
                futures.add(parents.submit(() -> {
                    occupied.countDown();
                    submit.await();
                    Future<Integer> child = children.submit(() -> {
                        childrenStarted.incrementAndGet();
                        Thread.sleep(50);
                        return 1;
                    });
                    return child.get();
                }));
            }
            if (!occupied.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Workers did not start within 5 seconds");
            submit.countDown();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1000);
            for (Future<Integer> future : futures) {
                try {
                    future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                } catch (TimeoutException timeout) {
                    break;
                }
            }
            int completed = (int) futures.stream().filter(f -> f.isDone() && !f.isCancelled()).count();
            int started = childrenStarted.get();
            int queued = children.getQueue().size();
            int active = parents.getActiveCount();
            StringBuilder dump = new StringBuilder();
            for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
                if (!info.getThreadName().startsWith(prefix)) continue;
                dump.append('"').append(info.getThreadName()).append("\" ").append(info.getThreadState()).append('\n');
                if (info.getLockInfo() != null) dump.append("  waiting on ").append(info.getLockInfo()).append('\n');
                if (info.getLockOwnerName() != null) dump.append("  lock owner: ").append(info.getLockOwnerName()).append('\n');
                for (StackTraceElement frame : info.getStackTrace()) dump.append("    at ").append(frame).append('\n');
                dump.append('\n');
            }
            boolean expected = mode.equals("starved")
                ? completed == 0 && queued == size && started == 0 && active == size
                : completed == size && queued == 0 && started == size;
            // Capture counters/stacks first, then finish parked events before stopping JFR.
            cleanup(futures, parents, children);
            recording.stop();
            Path jfr = directory.resolve("recording.jfr");
            recording.dump(jfr);
            result = new Result(id, mode, size, size, started, completed, queued, active,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), expected, dump.toString(),
                directory.resolve("report.html").toString(), jfr.toString());
            Files.writeString(directory.resolve("threads.txt"), result.dump());
            Files.writeString(directory.resolve("result.json"), result.json() + "\n");
            Files.writeString(directory.resolve("report.html"), report(result));
        } finally {
            submit.countDown();
            cleanup(futures, parents, children);
        }
        return result;
    }

    private static void cleanup(List<Future<Integer>> futures, ThreadPoolExecutor parents,
                                ThreadPoolExecutor children) throws InterruptedException {
        // Stop queue consumption before interrupting the parent futures.
        parents.shutdownNow();
        if (children != parents) children.shutdownNow();
        for (Future<Integer> future : futures) future.cancel(true);
        if (!parents.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("Parent worker cleanup failed");
        if (children != parents && !children.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("Child worker cleanup failed");
    }

    private static ThreadPoolExecutor pool(int count, String name) {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(count, count, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), task -> new Thread(task, name + sequence.incrementAndGet()));
    }

    static String quote(String input) {
        StringBuilder result = new StringBuilder("\"");
        for (char ch : input.toCharArray()) {
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (ch < 32) result.append(String.format("\\u%04x", (int) ch)); else result.append(ch); }
            }
        }
        return result.append('"').toString();
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String report(Result r) throws java.io.IOException {
        return """
            <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title data-en="JVM Incident Lab — evidence report" data-ko="JVM Incident Lab — 증거 리포트">JVM Incident Lab — evidence report</title>
            <style>body{max-width:960px;margin:48px auto;padding:0 24px;font:17px/1.6 system-ui;background:#101820;color:#e8eef5}h1,h2{color:#7be0c3}pre{overflow:auto;background:#182530;padding:24px;font-size:13px}code{color:#7be0c3}.languages{display:flex;gap:8px}button{font:14px system-ui;border:1px solid #30404d;border-radius:6px;padding:8px 12px;background:transparent;color:#e8eef5;cursor:pointer}button[aria-pressed="true"]{background:#7be0c3;color:#101820}button:focus-visible{outline:3px solid white;outline-offset:3px}</style>
            """ + HtmlI18n.controls() + "<h1>JVM Incident Lab</h1><p>" +
            HtmlI18n.text("Thread pool starvation · local experiment evidence", "스레드 풀 고갈 · 로컬 실험 증거") + "</p><h2>" +
            (r.mode().equals("starved") ? HtmlI18n.text("Failure", "실패") : HtmlI18n.text("Fix", "수정")) + " · " +
            (r.expected() ? HtmlI18n.text("Expected behavior observed", "예상한 동작을 확인했습니다") :
                HtmlI18n.text("Unexpected result — investigate", "예상과 다른 결과 — 확인이 필요합니다")) + "</h2><p>" +
            HtmlI18n.text("Parents completed: ", "완료한 부모 작업: ") + "<b>" + r.completed() + "/" + r.parents() + "</b> · " +
            HtmlI18n.text("Children started: ", "실행을 시작한 자식: ") + r.childrenStarted() + " · " +
            HtmlI18n.text("Queued children: ", "대기 중인 자식: ") + r.queued() + " · " +
            HtmlI18n.text("Active parents at snapshot: ", "관측 시점에 활성 상태인 부모: ") + r.active() + "</p><p>" +
            HtmlI18n.text("Starved: four parent tasks occupy every worker, enqueue children in the same pool, then wait on Future.get(). Fixed: children use a separate pool. This resolves this dependency pattern; it is not a universal pool-sizing remedy.",
                "실패 사례에서는 부모 작업 네 개가 모든 워커를 점유하고 같은 풀에 자식을 넣은 뒤 Future.get()으로 기다립니다. 수정 사례는 자식 전용 풀을 사용합니다. 이 예제의 의존 문제를 해결하는 방법이며, 모든 상황에 통하는 풀 크기 처방은 아닙니다.") + "</p><p>" +
            HtmlI18n.text("The starved run is observed for one second, then interrupted and cleaned up. This is dependency starvation, not a monitor deadlock. Elapsed time includes recording and evidence overhead, and is not a benchmark.",
                "실패 사례는 1초 동안 관측한 뒤 중단하고 정리합니다. 이 현상은 작업 간 의존으로 인한 고갈이며 JVM 모니터 데드락과 다릅니다. 실행 시간에는 기록과 증거 수집 비용이 포함되므로 성능 벤치마크가 아닙니다.") + "</p><h2>" +
            HtmlI18n.text("Worker thread snapshot", "워커 스레드 스냅샷") + "</h2><pre>" + html(r.dump()) + "</pre><h2>" +
            HtmlI18n.text("Artifacts", "결과 파일") + "</h2><p>" +
            HtmlI18n.text("Alongside this report: ", "리포트와 같은 폴더에 저장됩니다: ") +
            "<code>threads.txt</code>, <code>result.json</code>, <code>recording.jfr</code>.</p><p>" +
            HtmlI18n.text("In the starved trace, inspect parent workers parked in FutureTask.get and Experiment. The queue count comes from the executor snapshot, not an inference from thread state alone. JFR ThreadPark events can be inspected in JMC.",
                "실패 스택에서는 FutureTask.get과 Experiment에서 대기하는 부모 워커를 확인하세요. 대기 큐 수는 스레드 상태에서 추측한 값이 아니라 실행 풀에서 직접 측정한 값입니다. JFR의 ThreadPark 이벤트는 JMC에서 확인할 수 있습니다.") +
            "</p>" + HtmlI18n.script() + "</html>";
    }
}
