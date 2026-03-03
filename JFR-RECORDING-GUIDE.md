# JFR Recording Guide — Collecting Full Metrics for Hotpath

Hotpath analyzes CPU, GC, memory allocation, lock contention, and thread activity from JFR files.
Follow this guide to ensure your recording captures all the signals Hotpath needs.

---

## 1. Events Used by Hotpath

| Category | JFR Event | default | profile | Hotpath Recommended |
|----------|-----------|:-------:|:-------:|:-------------------:|
| CPU usage | `jdk.CPULoad` | ✅ | ✅ | ✅ |
| Hot Methods | `jdk.ExecutionSample` | ✅ | ✅ | ✅ (10–20 ms) |
| GC events | `jdk.GarbageCollection` | ✅ | ✅ | ✅ |
| Heap usage | `jdk.GCHeapSummary` | ✅ | ✅ | ✅ |
| Allocation (TLAB) | `jdk.ObjectAllocationInNewTLAB` | ❌ | ✅ | Optional |
| Allocation (outside) | `jdk.ObjectAllocationOutsideTLAB` | ❌ | ✅ | Optional |
| Lock contention | `jdk.JavaMonitorEnter` | ❌ | Partial | ✅ (lower threshold) |
| Thread park | `jdk.ThreadPark` | ❌ | Partial | ✅ |
| Thread statistics | `jdk.JavaThreadStatistics` | ✅ | ✅ | ✅ |
| JVM info | `jdk.JVMInformation` | ✅ | ✅ | ✅ |

**Summary:**
- General analysis → use `profile.jfc`
- Deep lock / allocation analysis → use `hotpath.jfc`
- In production, consider disabling allocation events depending on traffic load

---

## 2. Quickstart (Recommended)

Record on application startup with a single JVM flag:

```bash
java \
  -XX:StartFlightRecording=filename=hotpath-%t.jfr,settings=profile,dumponexit=true \
  -jar app.jar
```

| Option | Description |
|--------|-------------|
| `%t` | Timestamp in filename — prevents overwrites |
| `settings=profile` | Enables most events Hotpath needs |
| `dumponexit=true` | Automatically saves the file on normal JVM exit |

---

## 3. Custom JFC for Full Coverage

For deep lock contention and allocation analysis, use the Hotpath-specific configuration:

```bash
java \
  -XX:StartFlightRecording=filename=hotpath-%t.jfr,settings=/path/to/hotpath.jfc,dumponexit=true \
  -jar app.jar
```

---

## 4. Fixed-Duration Recording

Useful when you want to capture only a specific load test window:

```bash
java \
  -XX:StartFlightRecording=filename=hotpath-%t.jfr,settings=profile,duration=120s \
  -jar app.jar
```

---

## 5. Attach to a Running Process (jcmd)

No restart needed — attach JFR to any running JVM:

```bash
# 1. Find the PID
jps -l

# 2. Start recording
jcmd <PID> JFR.start name=hotpath settings=profile filename=hotpath.jfr

# 3. Check status
jcmd <PID> JFR.check

# 4. Dump to file
jcmd <PID> JFR.dump name=hotpath filename=hotpath.jfr

# 5. Stop
jcmd <PID> JFR.stop name=hotpath
```

---

## 6. Recommended JFC Settings

Key event overrides for `hotpath.jfc` (based on `profile.jfc`):

```xml
<!-- More precise CPU profiling -->
<event name="jdk.ExecutionSample">
  <setting name="enabled">true</setting>
  <setting name="period">10 ms</setting>
</event>

<!-- Capture lock waits >= 1 ms (profile default is higher) -->
<event name="jdk.JavaMonitorEnter">
  <setting name="enabled">true</setting>
  <setting name="threshold">1 ms</setting>
</event>

<event name="jdk.ThreadPark">
  <setting name="enabled">true</setting>
  <setting name="threshold">1 ms</setting>
</event>

<!-- Allocation tracking (optional — adds overhead) -->
<event name="jdk.ObjectAllocationInNewTLAB">
  <setting name="enabled">true</setting>
</event>

<event name="jdk.ObjectAllocationOutsideTLAB">
  <setting name="enabled">true</setting>
</event>
```

> **Notes:**
> - `ExecutionSample` at 10 ms gives more precise hot-method data at a slight CPU cost
> - Allocation events can add 5–10% overhead on high-throughput production servers
> - If lock analysis is not needed, raise the threshold or disable the event

---

## 7. Overhead Reference

| Configuration | Estimated overhead | Recommended for |
|---------------|:-----------------:|-----------------|
| `default.jfc` | ~1% | Always-on production monitoring |
| `profile.jfc` | ~2% | Development / staging |
| `hotpath.jfc` (with alloc) | ~2–5% | Dedicated profiling sessions |

> Actual overhead varies with workload.

---

## 8. Limiting Disk Usage in Production

For long-running recordings, cap file size to avoid disk pressure:

```bash
java \
  -XX:StartFlightRecording=filename=hotpath-%t.jfr,settings=profile,maxsize=1G,maxage=30m \
  -jar app.jar
```

| Option | Description |
|--------|-------------|
| `maxsize` | Maximum file size |
| `maxage` | Automatically drop data older than this |

---

## 9. Quick Selection Guide

| Goal | Recommended setting |
|------|---------------------|
| Always-on production monitoring | `default.jfc` |
| General performance analysis | `profile.jfc` |
| Deep GC + Allocation + Lock analysis | `hotpath.jfc` |

---

한국어 문서: [JFR-RECORDING-GUIDE.ko.md](./JFR-RECORDING-GUIDE.ko.md)
