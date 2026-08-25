# Cherrycherry_BE — Copilot 지침

독거노인 낙상감지 시스템의 백엔드. **사람의 안전과 건강정보를 다루는 서비스**라는 점이 모든 판단의 기준이다.
모든 코멘트는 한국어로 작성한다.

## 스택

Java 21 · Spring Boot 4.0.3 · Spring Data JPA · MariaDB · Spring Security + JWT · OAuth2(구글/카카오) · Firebase Admin SDK(FCM) · Gradle

## 구조

```
com.example.cherry_be
├── domain/{device,health,log,member,notification,organization,push,user,ward}
│   └── 각각 controller / dto / entity / repository / service
└── global/{auth,common,config,exception,util}
```

- 도메인 간 참조는 **service 레이어를 통해서만** 한다. 다른 도메인의 repository를 직접 부르지 않는다.
- 새 도메인을 만들 때도 위 5개 하위 패키지 구조를 지킨다.

## 도메인 용어 — 중요

같은 개념에 이름이 세 개다. **새 코드에서 이름을 더 늘리지 말 것.**

| 대상 | 쓰는 곳 |
|---|---|
| `Member` | 엔티티·DB (피보호자 = 돌봄을 받는 노인) |
| `target` | 기관용 API (`/api/targets/**`) |
| `ward` | 보호자용 API (`/api/wards/**`) |

- `User` = 보호자(가족), `Organization` = 복지기관. **`User`는 노인이 아니다.**
- 소유 구조: `Member.user`가 있으면 보호자 소유, 없으면 기관 소유. `Member.organization`은 열람 권한을 뜻하며 둘은 배타적이지 않다.
- 권한 2단계: **조회권**(`findViewableMember`) / **관리권**(`findManagedMember`, `Member.isManageable()`). 삭제·수정은 반드시 관리권으로 검사한다.

## 반드시 지킬 규칙

### 1. Lombok `boolean is~` 필드는 `@JsonProperty`를 붙인다

Lombok이 만든 `isRead()` 게터를 Jackson이 `read`로 직렬화해 **프론트가 값을 못 읽는 사고가 실제로 있었다.**

```java
@JsonProperty("isRead")     // 반드시 명시
private boolean isRead;
```

### 2. 권한 실패는 404로 응답한다

403을 주면 "그 ID가 존재한다"는 사실이 새어나간다(IDOR). 남의 피보호자에 접근하면 `MEMBER_NOT_FOUND`(404)를 던진다. **기존 방어를 깨지 말 것.**

### 3. 엔티티를 바꾸면 마이그레이션을 함께 쓴다

`ddl-auto`는 컬럼 삭제·타입 변경·제약 추가를 반영하지 않는다. 이 때문에 운영에서 500이 두 번 났다(`priority` NOT NULL, `org_code` UNIQUE).

- 컬럼 추가는 **nullable로** 시작한다. NOT NULL은 기본값 백필 후에.
- 유니크 제약을 새로 걸 때는 **기존 중복 여부를 먼저 확인**하는 SQL을 PR 본문에 남긴다.

### 4. 개인정보는 밖으로 내보내지 않는다

- **푸시 알림 본문**에 이름 외 정보(나이·주소·건강정보)를 넣지 않는다. 외부(FCM) 서버를 경유하고 잠금화면에 뜬다.
- **URL 쿼리스트링**에 토큰·개인정보를 싣지 않는다. 액세스 로그에 평문으로 남는다. 본문(body)을 쓴다.
- **로그**에는 식별자(`memberId`)만 남기고 값은 남기지 않는다.
- 건강정보(`disease`/`medication`/`memo`)는 `StringEncryptConverter`로 암호화 저장된다. **암호화 컬럼으로는 검색·정렬·동등비교가 불가능하다.**

### 5. 트랜잭션 커밋 전에 외부 발송을 하지 않는다

`@Async` 푸시를 트랜잭션 안에서 부르면 롤백돼도 푸시는 이미 나간다. 알림함에는 없는데 폰에는 뜬다.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

### 6. 기기 수신 데이터를 신뢰하지 않는다

`/api/device/data`는 현재 인증이 없다. 모든 필드에 `@Valid` + 범위 검증을 건다. `null` 가드 없이 중첩 객체의 게터를 부르지 않는다(500 유발 이력 있음).

- `report_type`: `HEARTBEAT`(상태 갱신만) / `EVENT`(로그·알림 적재). **HEARTBEAT에서 DB 행을 만들지 않는다.**
- 로그는 **상태가 변할 때만** 남긴다. 매 요청 INSERT는 하루 수만 행이 된다.
- 온라인 판정은 기기가 보낸 시각이 아니라 **서버 수신 시각**으로 한다.

### 7. 실패를 조용히 넘기지 않는다

낙상 알림이 안 갔다는 사실 자체가 기록되지 않는 게 가장 위험하다. 부가 기능(FCM 등)의 실패는 핵심 흐름을 막지 않되, **반드시 에러 로그를 남긴다.** `@Async void` 메서드는 예외가 삼켜지므로 내부에서 try/catch 한다.

## 코드 스타일

- 들여쓰기 2칸, google-java-format(100자). `./gradlew spotlessApply`
- **주석은 "무엇"이 아니라 "왜"를 쓴다.** 이 저장소의 기존 주석이 좋은 예시다.
  ```java
  // 푸시 본문은 외부(FCM) 서버를 경유하고 잠금화면에도 노출되므로,
  // 나이·주소·건강정보 등은 넣지 않고 앱에서 조회하도록 유도한다
  ```
- 주석·커밋 메시지·로그는 **한국어**로 쓴다.
- 예외는 `CustomException` + `ErrorCode`만 쓴다. `RuntimeException`을 직접 던지지 않는다.
- 새 `ErrorCode`는 도메인 접두어 + 순번(`O003`, `C009`)을 따른다.

## 커밋 · PR

- 커밋: `feat:` `fix:` `refactor:` `chore:` `style:` `docs:` + 한국어 요약
- **PR은 파일 10개 / 300줄 이내.** 큰 PR이 리뷰되지 못하고 revert된 이력이 있다.
- PR 본문에는 **무엇을 바꿨는지가 아니라 왜 바꿨는지**를 쓴다.

## 하지 말 것

- 자격증명을 커밋하지 않는다 — `.env`, `application-local.yaml`, `*.pem/p12/jks`, `firebase-adminsdk*.json`. CI가 검사한다.
- `.env.example`에 값을 채우지 않는다. 키 이름만 둔다.
- 응답 DTO 필드명을 바꿀 때 **웹·앱을 함께 확인하지 않고 바꾸지 않는다.** 손으로 맞추는 구조라 필드명 드리프트가 세 번 났다.
- 테스트는 실제 자격증명 없이 통과해야 한다. `src/test/resources/application-test.yaml`(H2 + 더미값)을 깨지 않는다.
