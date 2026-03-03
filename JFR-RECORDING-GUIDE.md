# JFR 녹화 가이드 — Hotpath 분석용 전체 측정 수집

Hotpath는 JFR 파일을 기반으로 CPU / GC / Allocation / Lock / Thread 상태를 자동 분석합니다.
아래 가이드를 따라 녹화하면 **Hotpath가 필요한 대부분의 신호를 수집**할 수 있습니다.

---

# 1️⃣ Hotpath가 사용하는 주요 이벤트

| 카테고리            | JFR 이벤트                           | default | profile |    Hotpath 권장    |
| --------------- | --------------------------------- | :-----: | :-----: | :--------------: |
| CPU 사용률         | `jdk.CPULoad`                     |    ✅    |    ✅    |         ✅        |
| Hot Methods     | `jdk.ExecutionSample`             |    ✅    |    ✅    |    ✅ (10~20ms)   |
| GC 이벤트          | `jdk.GarbageCollection`           |    ✅    |    ✅    |         ✅        |
| 힙 사용량           | `jdk.GCHeapSummary`               |    ✅    |    ✅    |         ✅        |
| 객체 할당 (TLAB)    | `jdk.ObjectAllocationInNewTLAB`   |    ❌    |    ✅    |        선택        |
| 객체 할당 (Outside) | `jdk.ObjectAllocationOutsideTLAB` |    ❌    |    ✅    |        선택        |
| Lock Contention | `jdk.JavaMonitorEnter`            |    ❌    |    일부   | ✅ (threshold 조정) |
| Thread Park     | `jdk.ThreadPark`                  |    ❌    |    일부   |         ✅        |
| 스레드 통계          | `jdk.JavaThreadStatistics`        |    ✅    |    ✅    |         ✅        |
| JVM 정보          | `jdk.JVMInformation`              |    ✅    |    ✅    |         ✅        |

### ✔ 기본 결론

* **일반 분석 → `profile.jfc` 사용**
* **Lock / Allocation까지 깊게 보려면 → `hotpath.jfc` 사용**
* 운영 환경에서는 오버헤드를 고려해 Allocation 이벤트는 상황에 따라 끄는 것을 권장

---

# 2️⃣ 가장 쉬운 방법 (권장)

애플리케이션 시작할 때 바로 녹화

```bash
java \
  -XX:StartFlightRecording=filename=hotpath-%t.jfr,settings=profile,dumponexit=true \
  -jar app.jar
```

### 설명

* `%t` : 시간 기반 파일명 (덮어쓰기 방지)
* `profile` : Hotpath 분석에 적합한 설정
* `dumponexit=true` : 정상 종료 시 자동 저장

---

# 3️⃣ Hotpath 전용 설정 사용 (심화 분석)

Lock contention과 Allocation까지 모두 보고 싶다면
Hotpath 전용 설정 파일을 사용합니다.

```bash
java \
  -XX:StartFlightRecording=filename=hotpath-%t.jfr,settings=/path/to/hotpath.jfc,dumponexit=true \
  -jar app.jar
```

---

# 4️⃣ 일정 시간만 녹화

```bash
java \
  -XX:StartFlightRecording=filename=hotpath-%t.jfr,settings=profile,duration=120s \
  -jar app.jar
```

테스트 구간만 측정할 때 사용합니다.

---

# 5️⃣ 실행 중인 프로세스에 붙기 (jcmd)

### 1) PID 확인

```bash
jps -l
```

### 2) 녹화 시작

```bash
jcmd <PID> JFR.start name=hotpath settings=profile filename=hotpath.jfr
```

### 3) 녹화 상태 확인

```bash
jcmd <PID> JFR.check
```

### 4) 파일 저장

```bash
jcmd <PID> JFR.dump name=hotpath filename=hotpath.jfr
```

### 5) 종료

```bash
jcmd <PID> JFR.stop name=hotpath
```

---

# 6️⃣ Hotpath 권장 JFC 설정 (핵심만)

Hotpath는 아래 이벤트를 활성화하는 설정을 권장합니다.

```xml
<event name="jdk.ExecutionSample">
  <setting name="enabled">true</setting>
  <setting name="period">10 ms</setting>
</event>

<event name="jdk.JavaMonitorEnter">
  <setting name="enabled">true</setting>
  <setting name="threshold">1 ms</setting>
</event>

<event name="jdk.ThreadPark">
  <setting name="enabled">true</setting>
  <setting name="threshold">1 ms</setting>
</event>

<event name="jdk.ObjectAllocationInNewTLAB">
  <setting name="enabled">true</setting>
</event>

<event name="jdk.ObjectAllocationOutsideTLAB">
  <setting name="enabled">true</setting>
</event>
```

### ⚠ 참고

* `ExecutionSample` 10ms → 더 정밀하지만 약간의 오버헤드 증가
* Allocation 이벤트는 트래픽 많은 운영 서버에서는 오버헤드 증가 가능
* Lock 분석이 필요 없다면 threshold를 높이거나 비활성화 가능

---

# 7️⃣ 오버헤드 가이드 (대략적 기준)

| 설정                 | 예상 오버헤드 | 사용 권장     |
| ------------------ | ------- | --------- |
| default            | ~1%     | 운영 상시     |
| profile            | ~2%     | 개발 / 스테이징 |
| hotpath (alloc 포함) | ~2–5%   | 성능 분석 세션  |

> 실제 오버헤드는 워크로드에 따라 달라질 수 있습니다.

---

# 8️⃣ 운영에서 안전하게 쓰는 팁

장시간 녹화 시 디스크 사용을 제한하세요.

```bash
-XX:StartFlightRecording=filename=hotpath-%t.jfr,settings=profile,maxsize=1G,maxage=30m
```

* `maxsize` : 최대 파일 크기
* `maxage` : 오래된 데이터 자동 삭제

---

# 9️⃣ 빠른 선택 가이드

| 목적                           | 추천 설정       |
| ---------------------------- | ----------- |
| 운영 모니터링                      | default     |
| 일반 성능 분석                     | profile     |
| GC + Allocation + Lock 심층 분석 | hotpath.jfc |

