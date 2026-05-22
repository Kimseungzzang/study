# 이벤트 루프 정리

# 1. 이벤트 루프란 무엇인가?

이벤트 루프(Event Loop)는:

```text
실행 가능한 작업이 있는지 Queue를 확인하고,
준비된 callback을 꺼내 실행시키는 반복 메커니즘
```

이다.

즉, 이벤트 루프는 작업을 직접 오래 수행하는 스레드라기보다:

```text
Call Stack이 비었는지 확인
→ Queue에 실행 가능한 callback이 있는지 확인
→ callback을 JS Thread에서 실행
→ 다시 반복
```

하는 구조이다.

Node.js 기준으로 보면:

```text
실제 I/O 작업:
OS, libuv thread pool, DB 서버, 네트워크 시스템 등이 처리

Event Loop:
완료된 작업의 callback을 적절한 시점에 실행
```

예:

```javascript
fs.readFile("a.txt", (err, data) => {
  console.log(data);
});
```

흐름:

```text
1. 파일 읽기 요청
2. 실제 파일 I/O는 libuv / OS / thread pool에 위임
3. 파일 읽기 완료
4. callback이 Queue에 들어감
5. Event Loop가 callback을 꺼냄
6. JS Thread가 callback 실행
```

핵심은:

```text
Queue에 들어가는 것은 실제 I/O 작업 자체가 아니라,
I/O가 끝난 뒤 실행할 callback이다.
```

---

# 2. 이벤트 루프를 사용하는 환경 또는 프레임워크

이벤트 루프는 Node.js만의 개념은 아니다.

비동기 I/O, GUI 이벤트, 네트워크 이벤트를 처리하는 많은 환경에서 사용된다.

## 2.1 Node.js

Node.js는 이벤트 루프 기반 런타임의 대표적인 예이다.

```text
JavaScript 실행은 메인 스레드에서 수행
I/O 작업은 libuv, OS, thread pool 등에 위임
완료 callback은 Event Loop를 통해 실행
```

주로 사용되는 영역:

- API 서버
- 실시간 채팅
- WebSocket 서버
- Gateway 서버
- I/O 중심 서비스

---

## 2.2 Browser JavaScript

브라우저도 이벤트 루프를 사용한다.

예:

- click event
- setTimeout
- fetch
- Promise
- DOM rendering

브라우저에서는 사용자의 클릭, 네트워크 응답, 타이머 완료 같은 이벤트가 Queue에 들어가고, 이벤트 루프가 이를 처리한다.

---

## 2.3 Netty

Java 진영에서는 Netty가 이벤트 루프 기반 네트워크 프레임워크이다.

Netty는 request마다 thread를 만드는 방식이 아니라, 적은 수의 EventLoop thread가 여러 connection의 I/O 이벤트를 처리한다.

주로 사용되는 곳:

- 고성능 TCP 서버
- Gateway
- WebSocket 서버
- Spring WebFlux 내부 런타임

---

## 2.4 Spring WebFlux

Spring WebFlux는 전통적인 Spring MVC와 달리 non-blocking I/O 모델을 지원한다.

내부적으로 Reactor와 Netty를 사용할 수 있으며, 이벤트 루프 기반으로 많은 요청을 적은 thread로 처리할 수 있다.

```text
Spring MVC:
request per thread 모델 중심

Spring WebFlux:
event loop + non-blocking I/O 모델 중심
```

---

## 2.5 기타 예시

이벤트 루프 또는 유사한 이벤트 기반 실행 모델을 사용하는 예:

- Python asyncio
- JavaScript browser runtime
- Node.js
- Netty
- Nginx
- Redis
- UI framework event loop

---

# 3. 이벤트 루프와 Request-per-Thread 방식의 차이

# 3.1 Request-per-Thread 방식

전통적인 서버 모델에서는 요청 하나를 처리하기 위해 thread 하나를 배정한다.

예:

```text
요청 1 → Thread 1
요청 2 → Thread 2
요청 3 → Thread 3
```

요청 처리 중 DB 응답이나 외부 API 응답을 기다리면, 해당 thread는 대기 상태가 된다.

```text
Thread가 I/O 응답을 기다리며 점유됨
```

장점:

- 구조가 직관적
- 코드 작성이 단순함
- blocking 코드와 잘 맞음

단점:

- 요청이 많아질수록 thread 수 증가
- thread context switching 비용 증가
- I/O 대기 중에도 thread가 점유됨
- 많은 동시 연결 처리에 불리할 수 있음

---

# 3.2 Event Loop 방식

이벤트 루프 방식에서는 요청마다 thread를 하나씩 붙잡아두지 않는다.

I/O 작업은 외부 시스템에 맡기고, 완료되면 callback이나 continuation을 Queue에 넣는다.

```text
요청 처리 시작
→ I/O 작업 위임
→ thread는 다른 요청 처리 가능
→ I/O 완료
→ callback 실행
```

장점:

- 적은 수의 thread로 많은 요청 처리 가능
- I/O 대기 시간 동안 thread를 낭비하지 않음
- 높은 동시성 처리에 유리

단점:

- blocking 코드가 들어가면 이벤트 루프가 막힘
- CPU 연산이 오래 걸리면 전체 응답 지연 가능
- 코드 흐름과 디버깅이 복잡해질 수 있음
- DB connection pool, thread pool, OS 자원 한계는 여전히 존재

---

# 3.3 비교 표

| 구분 | Request-per-Thread | Event Loop |
|---|---|---|
| 기본 구조 | 요청마다 thread 배정 | 적은 thread가 여러 이벤트 처리 |
| I/O 대기 | thread가 대기하며 점유됨 | I/O를 위임하고 다른 작업 처리 |
| 동시성 | thread 수에 크게 의존 | 적은 thread로 높은 동시성 가능 |
| CPU 작업 | thread를 점유하지만 다른 thread는 동작 가능 | 이벤트 루프를 막을 수 있음 |
| 코드 스타일 | 동기/blocking 코드에 적합 | 비동기/non-blocking 코드에 적합 |
| 대표 예 | Spring MVC, Servlet | Node.js, Netty, Spring WebFlux |

---

# 4. 핵심 정리

이벤트 루프는:

```text
Queue에 들어온 callback을 적절한 시점에 꺼내 실행하는 반복 메커니즘
```

이다.

Request-per-Thread 방식은:

```text
요청마다 thread를 배정하고,
I/O 대기 중에도 thread가 점유되는 구조
```

이다.

Event Loop 방식은:

```text
I/O 작업은 외부에 위임하고,
완료 callback만 Queue를 통해 실행하는 구조
```

이다.

따라서 이벤트 루프 방식은 I/O 중심 작업에 강하지만, CPU 연산이나 blocking 코드가 많으면 이벤트 루프가 막혀 성능이 떨어질 수 있다.
