# Hotpath

A CLI tool that analyzes Java Flight Recorder (JFR) files and converts them into a human-readable HTML report.

```
java -jar hotpath.jar recording.jfr
```

Produces a single `report.html` file. [View sample report](https://yyubin.github.io/hotpath/sample-report.html)

---

## Download

Download `hotpath.jar` from [GitHub Releases](https://github.com/yyubin/hotpath/releases/latest), or grab it directly:

```bash
curl -L https://github.com/yyubin/hotpath/releases/latest/download/hotpath.jar -o hotpath.jar
java -jar hotpath.jar recording.jfr
```

Requires JDK 21+. No installation needed.

---

## Features

- **Native JFR parsing** — Uses the built-in `jdk.jfr.consumer` API with zero external dependencies
- **Single-pass streaming** — Handles recordings of hundreds of MB without memory pressure
- **Self-contained HTML output** — Works offline with no CDN dependencies
- **Fat JAR distribution** — Runs anywhere a JDK is installed

## What It Analyzes

| Category | Metrics |
|----------|---------|
| **CPU** | JVM user/system CPU usage over time, Hot Methods Top 10 |
| **GC** | GC count, max/avg pause, total STW time, pause distribution histogram |
| **Memory** | Heap usage over time, total allocation, allocation rate per second, Top Allocators |
| **Threads** | Peak/avg thread count, lock contention total, longest monitor wait |
| **Findings** | Automatic anomaly detection (CRITICAL / WARNING / INFO) |

## Report Layout

```
┌─ Summary      Finding cards by severity
├─ Findings     Issue list with cause and recommendation
├─ CPU          Load timeline chart + Hot Methods table
├─ GC           Pause timeline + distribution histogram + stats
├─ Memory       Heap usage chart + Top Allocators table
└─ Threads      Thread count stats + Lock Contention table
```

---

## Requirements

- JDK 21+

## Usage

```bash
# Basic — outputs report.html
java -jar hotpath.jar recording.jfr

# Custom output path
java -jar hotpath.jar recording.jfr -o my-report.html

# Help
java -jar hotpath.jar --help
```

## Build from Source

```bash
git clone https://github.com/yyubin/hotpath.git
cd hotpath
./gradlew :hotpath-cli:shadowJar
```

Output: `hotpath-cli/build/libs/hotpath.jar` (~3 MB)

---

## JFR Recording

To capture all metrics, record with the `profile.jfc` configuration:

```bash
# Record on application startup
java -XX:StartFlightRecording=filename=recording.jfr,settings=profile,dumponexit=true \
     -jar app.jar

# Attach to a running process
jcmd <PID> JFR.start name=hotpath settings=profile filename=recording.jfr
jcmd <PID> JFR.dump name=hotpath filename=recording.jfr
```

For per-event details, overhead guidelines, and a custom JFC configuration, see [JFR-RECORDING-GUIDE.md](./JFR-RECORDING-GUIDE.md).

---

## Architecture

### Module Structure

```
hotpath/
├── hotpath-core/                  Analysis logic
│   └── src/main/java/.../
│       ├── model/                 Data models (records)
│       │   ├── AnalysisResult
│       │   ├── CpuSummary / GcSummary / MemorySummary / ThreadSummary
│       │   ├── Finding            (CRITICAL / WARNING / INFO)
│       │   └── TimeBucket         1-second aggregation slot
│       ├── reader/                JFR parsing
│       │   ├── JfrReader          Single-pass streaming
│       │   ├── EventRouter        Dispatches events by type
│       │   ├── TimelineBuilder    Aggregates into 1s buckets
│       │   └── handler/
│       │       ├── CpuHandler     jdk.CPULoad, jdk.ExecutionSample
│       │       ├── GcHandler      jdk.GarbageCollection, jdk.GCHeapSummary
│       │       ├── MemoryHandler  jdk.ObjectAllocation*
│       │       ├── ThreadHandler  jdk.JavaMonitorEnter, jdk.JavaThreadStatistics
│       │       └── MetaHandler    jdk.JVMInformation
│       ├── analyzer/              Anomaly detection + Finding generation
│       │   ├── CpuAnalyzer
│       │   ├── GcAnalyzer
│       │   ├── MemoryAnalyzer
│       │   └── ThreadAnalyzer
│       └── renderer/
│           └── HtmlRenderer       AnalysisResult → JSON → report.html
└── hotpath-cli/                   CLI entry point
    └── src/main/java/.../
        ├── HotpathCommand         Picocli command
        └── Main
```

### Pipeline

```
.jfr file
  │
  ▼  jdk.jfr.consumer.RecordingFile (single pass)
[JfrReader + EventRouter]
  │
  ├─► CpuHandler / GcHandler / MemoryHandler / ThreadHandler
  │
  ▼  Aggregate into 1-second buckets
[TimelineBuilder]
  │
  ▼  Anomaly detection → generate Findings
[Analyzers]
  │
  ▼  AnalysisResult → JSON → inline embed into HTML
[HtmlRenderer]
  │
  ▼
report.html
```

---

## Roadmap

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | JFR → HTML report (CPU, GC, Memory, Threads) | ✅ |
| 2 | `gc.log` + Gatling `stats.json` integration + cross-source correlation | Planned |
| 3 | async-profiler flame graph embed | Planned |
| 4 | Before/after performance comparison (`before.jfr` vs `after.jfr`) | Planned |
| 5 | Gradle Plugin wrapper | Planned |

---

## Tech Stack

| Role | Technology |
|------|------------|
| JFR parsing | `jdk.jfr.consumer` (JDK built-in) |
| CLI | Picocli 4.7 |
| JSON | Jackson + jackson-datatype-jsr310 |
| Charts | Plotly.js (inlined in HTML) |
| Build | Gradle + Shadow Plugin |

---

## Contributing

Contributions of all kinds are welcome — bug reports, feature requests, ideas, and pull requests.

- **Bug / question** → [Open an issue](https://github.com/yyubin/hotpath/issues)
- **Feature suggestion** → Issues or Discussions, any format is fine
- **Pull request** → Fork → branch → PR, no strict process required

There are no contribution guidelines yet. Just open an issue or send a PR — it will be reviewed.

---

## License

[MIT](./LICENSE)

---

한국어 문서: [README.ko.md](./README.ko.md)
