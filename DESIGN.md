# Hotpath 설계 문서

## 목표

JFR 원자료를 사람이 읽을 수 있는 판단 단위로 변환한다.

- **입력**: `.jfr` (필수) + `gc.log`, `async-profiler.html`, `gatling stats.json` (선택)
- **출력**: `report.html` (단일 파일, 외부 의존성 없음)
- **배포**: Fat JAR CLI (1차), Gradle Plugin (2차)

---

## 모듈 구조

```
hotpath/
├── hotpath-core/        ← 분석 로직 전체
│   ├── reader/          ← 소스별 파서
│   ├── model/           ← 데이터 모델
│   ├── analyzer/        ← 카테고리별 분석기
│   ├── correlator/      ← 교차 소스 상관 분석
│   └── renderer/        ← HTML 생성
├── hotpath-cli/         ← Picocli 진입점
└── hotpath-gradle/      ← Gradle Plugin (추후)
```

---

## 파이프라인

```
.jfr 파일
    │
    ▼
[Reader] RecordingFile 스트리밍 (단일 패스)
    │
    ▼
[EventRouter] 이벤트 타입별 핸들러로 분기
    │
    ├─► CpuHandler
    ├─► GcHandler
    ├─► MemoryHandler
    ├─► ThreadHandler
    ├─► IoHandler
    └─► AllocationHandler
    │
    ▼
[Aggregator] 1초 단위 time bucket으로 집계
    │
    ▼
[Analyzer] 카테고리별 이상 탐지 + Finding 생성
    │
    ▼
[Correlator] 타임라인 교차 분석
    │
    ▼
[Renderer] AnalysisResult → JSON → HTML 인라인 임베드
    │
    ▼
report.html
```

---

## JFR 파싱 전략

`jdk.jfr.consumer.RecordingFile` 사용 — JDK 내장, 외부 의존성 없음.

### 핵심 원칙: 단일 패스 스트리밍

전체 이벤트를 메모리에 올리지 않고, 각 핸들러가 집계 상태만 유지한다.
수백 MB JFR 파일도 안정적으로 처리 가능.

```java
try (RecordingFile rf = new RecordingFile(path)) {
    while (rf.hasMoreEvents()) {
        RecordedEvent e = rf.readEvent();
        router.dispatch(e);
    }
}
```

### 추출 이벤트 목록

| 카테고리 | JFR 이벤트 타입 |
|----------|----------------|
| CPU | `jdk.CPULoad`, `jdk.ExecutionSample` |
| GC | `jdk.GarbageCollection`, `jdk.GCHeapSummary`, `jdk.GCPhasePause` |
| 메모리 | `jdk.ObjectAllocationInNewTLAB`, `jdk.ObjectAllocationOutsideTLAB` |
| 스레드 | `jdk.JavaMonitorEnter`, `jdk.ThreadPark`, `jdk.JavaMonitorWait` |
| I/O | `jdk.SocketRead`, `jdk.SocketWrite`, `jdk.FileRead`, `jdk.FileWrite` |
| 메타 | `jdk.JVMInformation`, `jdk.ActiveSetting` |

---

## 데이터 모델

```java
// 분석 결과 단위
record Finding(
    Severity severity,      // INFO / WARNING / CRITICAL
    String category,
    String title,
    String description,
    String recommendation
) {}

// 타임라인 교차 이벤트
record Correlation(
    Instant start,
    Instant end,
    List<String> sources,   // ["JFR/GC", "Gatling"] 등
    String summary
) {}

// 렌더러에 전달하는 전체 결과
record AnalysisResult(
    RecordingMeta meta,
    CpuSummary cpu,
    GcSummary gc,
    MemorySummary memory,
    ThreadSummary threads,
    IoSummary io,
    List<Correlation> correlations,
    List<Finding> findings
) {}
```

---

## HTML 렌더링 전략

외부 템플릿 엔진 없이 **데이터와 뷰를 완전히 분리**한다.

```
AnalysisResult
    │  Jackson 직렬화
    ▼
JSON 데이터 blob
    │
    ▼
HTML 템플릿 (정적, 소스에 포함)
    ├── <script>const DATA = { ...JSON... }</script>
    ├── Plotly.js (인라인 번들)
    └── CSS (인라인)
```

Java 코드는 JSON 직렬화만 담당. 차트 렌더링은 브라우저의 Plotly.js가 처리한다.

### 리포트 섹션 구성

```
┌─ Summary      파인딩 카드, 심각도별 요약
├─ Overview     전체 타임라인 오버뷰
├─ CPU          CPU load 추이, Hot Methods 테이블
├─ GC           Pause 분포, GC 빈도, STW 누적 시간
├─ Memory       힙 사용 추이, Allocation rate
├─ Threads      Lock contention, Park 비율
├─ I/O          Latency 이상값
└─ Correlations 교차 인사이트 (GC pause ↔ latency spike 등)
```

---

## 기술 스택

| 역할 | 선택 | 이유 |
|------|------|------|
| JDK | 21 | records, sealed class, 최신 LTS |
| JFR 파싱 | `jdk.jfr.consumer` | JDK 내장, 의존성 없음 |
| CLI | Picocli | 어노테이션 기반, 배포 친화적 |
| JSON | Jackson | 직렬화 표준 |
| Fat JAR 빌드 | Gradle Shadow Plugin | 단일 JAR 배포 |
| 차트 | Plotly.js (인라인) | CDN 없이 단일 HTML 완성 |

---

## CLI 인터페이스

```bash
# 기본 사용
java -jar hotpath.jar recording.jfr

# 선택 소스 추가
java -jar hotpath.jar recording.jfr \
  --gc-log gc.log \
  --async-profiler profile.html \
  --gatling stats.json \
  --output report.html
```

---

## 개발 단계

| 단계 | 내용 |
|------|------|
| Phase 1 | JFR 단독 파싱 → HTML 리포트 (CPU + GC + Memory) |
| Phase 2 | gc.log, Gatling 연동 + Correlator |
| Phase 3 | async-profiler flame graph 임베드 |
| Phase 4 | 성능 개선 전후 비교 (`before.jfr` vs `after.jfr`) |
| Phase 5 | Gradle Plugin 래퍼 |
