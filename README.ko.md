# Hotpath

JFR(Java Flight Recorder) 파일 및 async-profiler collapsed stacks를 분석해 HTML 리포트로 변환하는 CLI 도구.

```bash
# JFR 분석
java -jar hotpath.jar recording.jfr

# 플레임 그래프 분석 (async-profiler collapsed stacks)
java -jar hotpath.jar profile.collapsed
```

→ `report.html` 단일 파일 생성. [샘플 리포트 보기](https://yyubin.github.io/hotpath/sample-report.html)

---

## 다운로드

[GitHub Releases](https://github.com/yyubin/hotpath/releases/latest) 페이지에서 `hotpath.jar`를 받거나, 아래 명령으로 직접 받는다.

```bash
curl -L https://github.com/yyubin/hotpath/releases/latest/download/hotpath.jar -o hotpath.jar
java -jar hotpath.jar recording.jfr
```

JDK 21 이상 필요. 별도 설치 없음.

---

## 특징

- **JFR 네이티브 파싱** — `jdk.jfr.consumer` 내장 API로 외부 의존성 없이 직접 파싱
- **플레임 그래프 분석** — async-profiler collapsed stacks를 받아 인터랙티브 d3-flamegraph 리포트 생성
- **단일 패스 스트리밍** — 수백 MB JFR도 메모리 부담 없이 처리
- **단일 HTML 출력** — CDN 없이 오프라인에서도 열리는 자기완결형 리포트
- **입력 타입 자동 감지** — 매직 바이트·확장자·파일 구조로 JFR / collapsed stacks 자동 판별
- **Fat JAR 배포** — JDK만 있으면 설치 없이 바로 실행

## 분석 항목

### JFR 분석

| 카테고리 | 수집 지표 |
|----------|-----------|
| **CPU** | JVM user/system CPU 사용률 추이, Hot Methods Top 10 |
| **GC** | GC 횟수, 최대·평균 pause, STW 누적 시간, pause 분포 히스토그램 |
| **Memory** | 힙 사용량 추이, 총 할당량, 초당 allocation rate, Top Allocators |
| **Threads** | 피크·평균 스레드 수, Lock Contention 누적, 최장 대기 모니터 |
| **Findings** | 이상 패턴 자동 감지 (CRITICAL / WARNING / INFO) |

### 플레임 그래프 분석

| 카테고리 | 수집 지표 |
|----------|-----------|
| **Flame Graph** | 클릭 zoom / 텍스트 검색, 호버 시 self/total % 표시 |
| **Hot Methods** | Self time Top 10, Total time Top 10 |
| **Package Breakdown** | 레이어별 CPU 점유율 — Application / Framework / JDK / JVM Internal |
| **Findings** | CPU 병목 감지, 깊은 스택 경고, 재귀 감지 |

## 리포트 구성

### JFR 리포트
```
┌─ Summary      Findings 카드 (심각도별 카운트)
├─ Findings     이슈 목록 + 원인·권고사항
├─ CPU          load 추이 차트 + Hot Methods 테이블
├─ GC           pause 타임라인 + 분포 히스토그램 + 통계
├─ Memory       힙 추이 차트 + Top Allocators 테이블
└─ Threads      스레드 수 통계 + Lock Contention 상위
```

### 플레임 그래프 리포트
```
┌─ Summary          Findings 카드 (심각도별 카운트)
├─ Findings         이슈 목록 + 원인·권고사항
├─ Overview         총 샘플 수, 최대 스택 깊이, 고유 프레임 수
├─ Flame Graph      인터랙티브 뷰 — 클릭으로 zoom, 검색으로 하이라이트
├─ Hot Methods      Self time Top 10 테이블
└─ Package          레이어 비율 파이 차트 + Total time 막대 차트
   Breakdown
```

---

## 요구사항

- JDK 21 이상 (실행 및 빌드)

## 사용법

```bash
# JFR 분석 — report.html 생성
java -jar hotpath.jar recording.jfr

# 플레임 그래프 분석 (async-profiler collapsed stacks)
java -jar hotpath.jar profile.collapsed
java -jar hotpath.jar profile.stacks

# 출력 파일 지정
java -jar hotpath.jar recording.jfr -o my-report.html

# 입력 타입 강제 지정 (기본: AUTO)
java -jar hotpath.jar profile.txt --type flamegraph

# 도움말
java -jar hotpath.jar --help
```

### async-profiler로 collapsed stacks 생성

```bash
# 실행 중인 프로세스를 30초 프로파일링
./asprof -d 30 -o collapsed -f profile.collapsed <PID>

# Java agent 방식
java -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,collapsed,file=profile.collapsed -jar app.jar
```

## 빌드

```bash
git clone https://github.com/yyubin/hotpath.git
cd hotpath
./gradlew :hotpath-cli:shadowJar
```

결과물: `hotpath-cli/build/libs/hotpath.jar` (~3 MB)

---

## JFR 녹화 옵션

전체 측정치를 수집하려면 `profile.jfc` 설정으로 녹화해야 한다.

```bash
# 애플리케이션 시작 시 자동 녹화
java -XX:StartFlightRecording=filename=recording.jfr,settings=profile,dumponexit=true \
     -jar app.jar

# 실행 중인 프로세스에 동적으로 붙이기
jcmd <PID> JFR.start name=hotpath settings=profile filename=recording.jfr
jcmd <PID> JFR.dump name=hotpath filename=recording.jfr
```

측정 항목별 필요 이벤트, 오버헤드 기준, 커스텀 JFC 설정은 [JFR-RECORDING-GUIDE.ko.md](./JFR-RECORDING-GUIDE.ko.md) 참고.

---

## 내부 파이프라인

```
.jfr 파일                                   .collapsed / .stacks 파일
  │                                                │
  ▼  RecordingFile (단일 패스)                    ▼  줄 단위 텍스트 파싱
[JfrReader + EventRouter]              [CollapsedStacksReader]
  │                                                │
  ├─► CpuHandler / GcHandler / ...               ▼  재귀 트리 구성
  │                                    [FlameGraphBuilder]  →  FlameNode root
  ▼  1초 버킷 집계                               │
[TimelineBuilder]                                 ▼  hot frames, 패키지 비율 집계
  │                                    [FlameGraphAnalyzer]
  ▼  이상 탐지 → Finding 생성                    │
[Analyzers]                                       ▼
  │                                    [FlameGraphHtmlRenderer]
  ▼                                               │
[HtmlRenderer]                                    ▼
  │                                    flamegraph-report.html
  ▼
report.html
```

---

## 기술 스택

| 역할 | 기술 |
|------|------|
| JFR 파싱 | `jdk.jfr.consumer` (JDK 내장) |
| Collapsed stacks 파싱 | 순수 Java (외부 의존성 없음) |
| CLI | Picocli 4.7 |
| JSON | Jackson + jackson-datatype-jsr310 |
| 차트 | Plotly.js (CDN) |
| 플레임 그래프 | d3-flamegraph (CDN) |
| 빌드 | Gradle + Shadow Plugin |

---

## 기여

버그 리포트, 기능 제안, 아이디어, PR 모두 환영합니다.

- **버그 / 질문** → [이슈 등록](https://github.com/yyubin/hotpath/issues)
- **기능 제안** → 이슈나 Discussions, 형식 무관
- **Pull Request** → Fork → 브랜치 → PR, 별도 규칙 없음

기여 가이드라인은 아직 없습니다. 이슈를 열거나 PR을 보내주시면 검토하겠습니다.

---

## 라이선스

[MIT](./LICENSE)
