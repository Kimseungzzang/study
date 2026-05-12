# MVC Spring 스레드 & 비동기 처리 정리

---

## 1. HTTP 요청 전체 흐름 (TCP → Tomcat → Spring)

```
클라이언트
    ↓ (IP + Port만 있으면 됨, URL 몰라도 됨)

━━━━━━━━━━━━━━━━━━━━━━━━━━━
[OS 커널 영역]
━━━━━━━━━━━━━━━━━━━━━━━━━━━
  1. NIC(네트워크 카드)가 패킷 수신
  2. TCP 3-way handshake 처리 (SYN → SYN-ACK → ACK)
  3. 연결 완료 → OS accept queue에 적재

━━━━━━━━━━━━━━━━━━━━━━━━━━━
[JVM / Tomcat 영역]
━━━━━━━━━━━━━━━━━━━━━━━━━━━
  4. Tomcat Acceptor 스레드
       accept() 시스템 콜로 OS 큐에서 연결 꺼냄

  5. Tomcat Poller에 등록
       NIO로 "이 연결 데이터 오면 알려줘" 감시 (스레드 안 씀)

  6. HTTP 데이터 도착
       Poller 감지 → Tomcat 스레드 풀에 제출
       스레드 없으면 → Tomcat executor 내부 큐에 대기

  7. Tomcat 스레드 배정
       HTTP 파싱 → "POST /auth/login" 확인

━━━━━━━━━━━━━━━━━━━━━━━━━━━
[Spring 영역]
━━━━━━━━━━━━━━━━━━━━━━━━━━━
  8. DispatcherServlet → URL 매핑
  9. Controller → Service → DB 조회 → 응답
```

### 레이어별 역할

| 레이어 | 담당 | URL 인식 |
|---|---|---|
| TCP (L4) | OS 커널 | X — IP+Port만 봄 |
| HTTP (L7) | Tomcat | O — 데이터 읽은 후 |
| 라우팅 | Spring | O — 컨트롤러 매핑 |

URL은 TCP 연결 후 HTTP 데이터를 읽어야 알 수 있다. OS/Tomcat 큐 단계는 URL과 무관하게 동작.

---

## 2. 스레드 vs 스레드 풀

| | 설명 |
|---|---|
| 스레드 | 작업 실행 단위 하나 (직원 한 명) |
| 스레드 풀 | 스레드를 미리 만들어서 재사용하는 관리 시스템 |

```
풀 없을 때: 요청 → 스레드 생성(비용) → 작업 → 스레드 삭제(비용)
풀 있을 때: 요청 → 풀에서 꺼냄 → 작업 → 풀에 반납
```

### Tomcat 스레드 vs Executor 스레드

```
[JVM 안]
  ├── Tomcat 스레드 풀  ← HTTP 요청 accept ~ 컨트롤러 처리
  └── Executor 스레드 풀  ← @Async 작업 처리 (별도 설정 필요)
```

`@Async` 안 쓰면 Executor 스레드 풀 없고 Tomcat 스레드 풀만 있음.

---

## 3. Tomcat 구조 및 설정

Tomcat = HTTP 요청을 받아서 Java 코드로 연결해주는 웹 서버 컨테이너.
- Tomcat이 스레드 풀을 **포함**하고 있는 것이지, Tomcat 자체가 스레드 풀은 아님

### 디폴트 설정

| 설정 | 기본값 | 의미 |
|---|---|---|
| 최대 스레드 | 200 | 동시 처리 가능한 요청 수 |
| 최소 유휴 스레드 | 10 | 항상 대기 중인 스레드 (즉시 투입 가능) |
| connection timeout | 20000ms | 연결 후 요청 없으면 20초 뒤 종료 |
| accept-count | 100 | OS accept queue 크기 |

### 내부 구성 요소

- **Acceptor 스레드**: 포트 하나당 1개. OS accept queue에서 연결 꺼냄
- **Poller 스레드**: 1~2개. NIO로 수천 개 연결 감시 (데이터 도착 감지)
- **스레드 풀 (executor)**: 실제 요청 처리

### NIO vs BIO

```
BIO (옛날): 연결 하나당 스레드 하나가 데이터 올 때까지 blocking 대기
NIO (현재): Poller가 수천 개 감시 → 데이터 준비된 것만 스레드 배정
           → 연결 수 >> 스레드 수 가능
```

---

## 4. 큐 구조와 막히는 순서

### 세 종류의 큐

| 큐 | 위치 | 역할 | 꽉 차면 |
|---|---|---|---|
| OS accept queue | 커널 | TCP 연결 대기 (accept-count=100) | SYN drop (연결 거부) |
| Tomcat executor 큐 | JVM 힙 (`LinkedBlockingQueue`) | 스레드 배정 대기 | 500 에러 |
| Executor 내부 큐 | JVM 힙 | @Async 작업 대기 (queueCapacity 설정) | RejectedExecutionException |

### 막히는 순서 (역방향)

```
스레드 풀 꽉 참
    → Tomcat executor 큐에 쌓임
    → executor 큐 꽉 참 → Acceptor가 멈춤
    → OS accept queue에 쌓임
    → OS accept queue 꽉 참
    → 커널이 SYN drop
```

아래(스레드)부터 막히고 위로 전파되는 구조.

---

## 5. 스레드 수가 많으면 안 좋은 이유

### 스레드 하나당 비용
- 스레드 하나당 512KB ~ 1MB 스택 메모리
- 스레드 1000개 × 1MB = 1GB 메모리 낭비

### 컨텍스트 스위칭
```
CPU 코어 4개, 스레드 1000개
→ 잘게 쪼개서 번갈아 실행
→ 전환 비용이 실제 작업 비용보다 커짐
→ 처리량 감소
```

---

## 6. IO 작업 vs CPU 작업

| | IO 작업 (DB, 외부 API) | CPU 작업 (연산, BCrypt) |
|---|---|---|
| 스레드가 하는 일 | 대부분 대기 | 계속 CPU 점유 |
| 적정 스레드 수 | 코어 수보다 많아도 됨 | 코어 수랑 비슷하게 |
| 많이 늘리면? | 처리량 증가 | 컨텍스트 스위칭만 늘어남 |



## 7. @Async 동작 방식

```
[HTTP 요청]
     ↓
[Tomcat 스레드] → Controller → Service
                                  ↓
                           @Async 메서드 호출
                                  ↓
                    Executor 스레드 풀에 작업 던짐
                                  ↓
[Tomcat 스레드] ← 즉시 반환 → 다음 요청 받을 수 있음
     ↓
HTTP 응답 (200 ok)
                   (이 시점에 Executor 스레드가 백그라운드에서 작업 중)
```


ThreadPoolExecutor 동작 순서

1. corePoolSize 만큼 스레드 생성
2. 그 이후는 큐에 적재
3. 큐가 꽉 차면 maxPoolSize까지 스레드 추가 생성
4. 그것도 꽉 차면 reject



### @Async 적합한 용도
- 이메일/문자 발송, 로그 저장, 푸시 알림, 통계 집계
- **응답이 그 작업의 결과에 의존하지 않는 경우**

### Executor 스레드 풀 설정

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);      // 기본 유지 스레드 수
        executor.setMaxPoolSize(50);       // 최대 스레드 수
        executor.setQueueCapacity(100);    // 대기 큐 크기 (JVM 힙)
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

### 여러 풀 구분

```java
@Async("emailExecutor")
public void sendEmail() { ... }

@Async("reportExecutor")
public void generateReport() { ... }
```

---
결국 동시 트래픽이 많아지면 외부 큐 사용이 필수


## 10. 외부 큐 + @Async 패턴 (Backpressure)

트래픽 많은 서비스에서 비동기 처리하는 구조.

```
[요청 폭발]
     ↓
[외부 큐] ← 일단 다 받아줌
     ↓
[Consumer]
     ↓
[Executor 스레드 풀] ← 처리 가능한 만큼만 꺼내서 처리
     ↓
작업 완료 → 다음 꺼내서 처리 → 반복
```

처리할 수 있는 만큼만 당겨서 처리하고 나머지는 큐에서 대기 = **백프레셔(Backpressure)**.



## 12. k6 부하 테스트

### 기본 구조
```javascript
export let options = {
    vus: 30,           // 동시 가상 유저 수
    duration: '40s',   // 테스트 시간
    thresholds: {
        http_req_duration: ['p(95)<3000'], // 95%가 3초 이내
        http_req_failed:   ['rate<0.1'],   // 에러율 10% 미만
    },
};
```

### 주요 결과 지표

| 지표 | 의미 |
|---|---|
| `avg` | 평균 응답시간 |
| `p(95)` | 95% 요청의 응답시간 (가장 중요) |
| `http_req_failed` | HTTP 에러율 |
| `iterations/s` | 초당 처리량 (RPS) |

avg보다 p(95)가 중요한 이유: avg는 빠른 요청에 끌려서 실제보다 좋아 보임. p(95)가 실제 유저 95%의 체감 성능.

---

## 13. 실제 부하 테스트 결과 (로그인 API, 동시 30명)

**환경**: BCrypt strength=10 (약 400ms 소요), PostgreSQL

```
┌─────────────────┬─────────┬─────────┬──────────┬────────┬────────┐
│      설정       │   avg   │  p(95)  │ 초당처리 │ 총처리 │ 에러율 │
├─────────────────┼─────────┼─────────┼──────────┼────────┼────────┤
│ 스레드1  / DB5  │ 2180ms  │ 2250ms  │   13건   │  564건 │   0%   │
├─────────────────┼─────────┼─────────┼──────────┼────────┼────────┤
│ 스레드5  / DB5  │  537ms  │  564ms  │   55건   │ 2249건 │   0%   │
├─────────────────┼─────────┼─────────┼──────────┼────────┼────────┤
│ 스레드20 / DB20 │  423ms  │  538ms  │   71건   │ 2852건 │   0%   │
├─────────────────┼─────────┼─────────┼──────────┼────────┼────────┤
│ 스레드20 / DB5  │  417ms  │  530ms  │   72건   │ 2895건 │   0%   │
└─────────────────┴─────────┴─────────┴──────────┴────────┴────────┘
```

### 인사이트
```
1. 스레드 풀이 핵심 병목
   스레드 1→5: avg 2180ms → 537ms (4배 개선)
   스레드 5→20: avg 537ms → 423ms (1.3배 개선)

2. DB 풀은 영향 없었음
   스레드20/DB5 vs 스레드20/DB20 거의 동일
   → BCrypt가 병목, DB 조회 자체는 빠름
   → DB 커넥션 점유 시간이 짧아서 5개로 충분

3. 무조건 DB 풀 = 스레드 풀이 아님
   작업 특성에 따라 최적값이 다름
```


스레드풀을 그래서 어떻게 설정해야하는가


## 12. HikariCP (DB 커넥션 풀)

DB 커넥션을 미리 만들어서 재사용하는 라이브러리. Spring Boot 기본 내장.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # 기본값
      connection-timeout: 30000
```

### 스레드 풀 vs DB 커넥션 풀

```
스레드 풀 > DB 풀 → 일부 스레드가 커넥션 대기 (병목)
스레드 풀 < DB 풀 → 커넥션 낭비
스레드 풀 = DB 풀 → 균형 (DB 집약적 서비스)
```

### 실무 기준
```
DB 집약적 서비스 → 스레드 ≈ DB 풀
캐시/혼합 서비스 → 스레드 > DB 풀도 OK (모든 요청이 DB를 쓰는 게 아니니까)
```



