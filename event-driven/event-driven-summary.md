# Event Driven 정리

# 1. Event Driven이란 무엇인가?

Event Driven은:

```text
어떤 이벤트가 발생했을 때,
그 이벤트에 반응해서 동작하는 구조
```

를 의미한다.

즉, 직접 다른 기능을 호출하는 방식이 아니라:

```text
이벤트 발생
→ 이벤트 발행
→ 이벤트를 구독하는 쪽에서 처리
```

하는 방식이다.

예를 들어 회원가입이 완료되었을 때:

```text
회원 저장
→ UserCreated 이벤트 발행
→ 메일 발송
→ 로그 저장
→ 쿠폰 발급
→ 통계 갱신
```

처럼 처리할 수 있다.

---

# 2. 일반 호출 방식과 Event Driven 방식

## 2.1 일반 호출 방식

```text
회원가입 서비스
→ 메일 서비스 호출
→ 로그 서비스 호출
→ 쿠폰 서비스 호출
```

이 방식은 흐름이 직관적이지만, 회원가입 서비스가 여러 기능을 직접 알아야 한다.

문제점:

- 결합도 증가
- 기능 추가 시 기존 코드 수정 필요
- 실패 처리 복잡
- 비동기 처리 확장 어려움

---

## 2.2 Event Driven 방식

```text
회원가입 서비스
→ UserCreated 이벤트 발행

메일 서비스    → UserCreated 구독 후 메일 발송
로그 서비스    → UserCreated 구독 후 로그 저장
쿠폰 서비스    → UserCreated 구독 후 쿠폰 발급
통계 서비스    → UserCreated 구독 후 통계 갱신
```

회원가입 서비스는 이벤트만 발행하고, 누가 그 이벤트를 처리하는지는 몰라도 된다.

장점:

- 느슨한 결합
- 기능 추가 쉬움
- 비동기 처리와 잘 맞음
- 서비스 간 확장성 증가

단점:

- 흐름 추적이 어려워질 수 있음
- 이벤트 중복 처리 대비 필요
- 순서 보장 문제 고려 필요
- 최종적 일관성(Eventual Consistency) 고려 필요
- 장애 추적, 재처리, 모니터링 설계 필요

---

# 3. Event Driven을 사용하는 아키텍처 예시

# 3.1 모놀리식 애플리케이션 내부 이벤트

하나의 애플리케이션 안에서도 Event Driven 구조를 사용할 수 있다.

예: Spring Application Event

```java
publisher.publishEvent(new UserCreatedEvent(userId));
```

```java
@TransactionalEventListener
public void handle(UserCreatedEvent event) {
    // 메일 발송, 로그 저장 등
}
```

사용 예:

- 회원가입 후 메일 발송
- 주문 완료 후 알림 발송
- 결제 완료 후 로그 저장
- 게시글 작성 후 검색 색인 갱신

특징:

```text
하나의 애플리케이션 내부에서
도메인 이벤트를 분리하는 용도
```

---

# 3.2 Microservices Architecture

마이크로서비스 환경에서는 서비스 간 직접 호출을 줄이기 위해 이벤트를 사용한다.

예:

```text
Order Service
→ OrderCreated 이벤트 발행

Payment Service
→ OrderCreated 구독 후 결제 처리

Inventory Service
→ OrderCreated 구독 후 재고 차감

Notification Service
→ OrderCreated 구독 후 알림 발송
```

이 방식은 서비스 간 결합도를 낮춘다.

특징:

- 서비스 간 느슨한 결합
- 비동기 처리 가능
- 서비스별 독립 확장 가능
- 일부 서비스 장애가 전체 요청을 즉시 막지 않게 설계 가능

주의점:

- 이벤트 중복 수신 가능성
- 메시지 유실 방지
- 재처리 전략
- 트랜잭션 경계
- 데이터 일관성

---

# 3.3 Message Broker 기반 아키텍처

Event Driven 구조에서는 이벤트 전달을 위해 Message Broker를 자주 사용한다.

대표 예:

- Kafka
- RabbitMQ
- Redis Streams
- AWS SNS / SQS
- Google Pub/Sub
- NATS

구조:

```text
Producer
→ Message Broker
→ Consumer
```

예:

```text
User Service
→ UserCreated 이벤트 발행
→ Kafka topic
→ Email Consumer
→ Coupon Consumer
→ Analytics Consumer
```

Message Broker는 이벤트를 저장하거나 전달하고, consumer는 이벤트를 받아 처리한다.

---

# 3.4 CQRS / Event Sourcing

Event Driven은 CQRS나 Event Sourcing과도 자주 같이 사용된다.

## CQRS

CQRS는 Command와 Query 책임을 분리하는 방식이다.

```text
쓰기 모델
→ 이벤트 발행
→ 읽기 모델 갱신
```

예:

```text
주문 생성
→ OrderCreated 이벤트
→ 주문 조회용 read model 갱신
```

## Event Sourcing

Event Sourcing은 현재 상태를 저장하는 대신, 상태 변화 이벤트를 저장한다.

예:

```text
OrderCreated
OrderPaid
OrderShipped
OrderCancelled
```

이 이벤트들을 순서대로 재생하면 현재 주문 상태를 복원할 수 있다.

---

# 4. 핵심 정리

Event Driven은:

```text
이벤트 발생을 중심으로 시스템을 설계하는 아키텍처 방식
```

이다.

핵심 목적은:

```text
이벤트 발행자와 이벤트 처리자를 분리하여
결합도를 낮추고 확장성을 높이는 것
```

이다.

Event Driven은 다음과 같은 방식으로 구현할 수 있다:

- Spring ApplicationEvent 같은 내부 이벤트
- Kafka, RabbitMQ 같은 Message Broker
- Redis Streams
- AWS SNS / SQS
- Google Pub/Sub
- Webhook

이벤트 기반 구조는 I/O 중심 비동기 처리, 마이크로서비스 간 통신, 알림, 로그, 통계, 검색 색인 갱신 같은 부가 작업 분리에 유용하다.
