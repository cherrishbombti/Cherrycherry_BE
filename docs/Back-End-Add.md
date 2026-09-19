# Back-End 확장 기획 (Add)

> 독거노인 낙상감지 IoT 시스템 — 2차 기능 확장 문서
> 현재 구현된 코드 기준으로 정리 (테이블/Role 명칭은 실제 코드에 맞춤)

---

## 0. 현재 코드와의 정합성 (먼저 확인)

기획서 원본은 `member` / `user` / `log` 테이블을 가정하지만, 실제 코드의 테이블·Role 명칭은 다르다. 아래 매핑을 기준으로 작업한다.

| 기획서 표기 | 실제 엔티티 테이블 | 비고 |
| --- | --- | --- |
| `member` | **`member_info`** | `@Table(name = "member_info")` |
| `user` | **`users`** | `user`는 예약어라 회피 |
| `log` | **`fall_log`** | `@Table(name = "fall_log")` |

| 기획서 Role | 실제 발급 Role | 비고 |
| --- | --- | --- |
| `ROLE_GUARDIAN` (보호자) | **`ROLE_USER`** | 소셜 로그인 시 발급 |
| `ROLE_SOCIAL_WORKER` (기관) | **`ROLE_ADMIN`** | 기관 로그인 시 발급 |

> **선행 과제**: Role 명칭 통일과 `/api/wards/**` 권한 설정(현재 `permitAll`)을 먼저 정리해야 이후 API 권한이 일관되게 적용된다.

---

## 1. DB 변경사항

### 1-1. `member_health` 테이블 신설 (건강정보 1:1)

기저질환·복용약·병력은 **민감정보**이므로 `member_info` 테이블과 분리하여 별도 관리한다. 피보호자 1명당 건강정보는 하나만 존재하므로 `member_id`를 UNIQUE로 두어 1:1 관계를 명확히 한다.

```sql
CREATE TABLE member_health (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_id   BIGINT UNIQUE NOT NULL,       -- FK → member_info.id (1:1)
    disease     VARCHAR(255),                 -- 기저질환
    medication  VARCHAR(255),                 -- 복용약
    memo        TEXT,                         -- 기타 병력
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES member_info(id)
);
```

> `member_id`를 PRIMARY KEY로 삼아 별도 `id`를 생략하는 방식도 가능하다.

### 1-2. `notification` 테이블 신설 (알림 이력)

`fall_log`가 "센서가 감지한 사건"이라면, `notification`은 "그 사건을 **누구에게 / 어떤 방식으로** 알렸고 읽었는지"를 남기는 **알림 전달·수신 기록**이다. 앱의 알림함(종 아이콘) 데이터로 쓰인다.

- 알림의 **종류(what)** 와 **전송 방식(how)** 은 다른 축이므로 `notification_type`과 `delivery_type`으로 분리한다.
- 한 번의 감지(`fall_log` 1건)에 여러 알림이 붙을 수 있어 **fall_log : notification = 1 : N**.

```
낙상 감지 → fall_log 저장(FALL_EVENT)
        → Push 발송 → notification (type=FALL, delivery=PUSH)
        → SMS  발송 → notification (type=FALL, delivery=SMS)
```

```sql
CREATE TABLE notification (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_id         BIGINT NOT NULL,   -- FK → member_info.id (대상 피보호자)
    user_id           BIGINT,            -- FK → users.id (수신자) *아래 주의 참고
    log_id            BIGINT,            -- FK → fall_log.id (연결된 감지, nullable)
    notification_type ENUM('FALL', 'WARNING', 'DEVICE_OFFLINE', 'EMERGENCY') NOT NULL,
    delivery_type     ENUM('PUSH', 'SMS') NOT NULL,
    is_read           BOOLEAN DEFAULT FALSE,
    sent_at           TIMESTAMP,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES member_info(id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (log_id)    REFERENCES fall_log(id)
);
```

컬럼 의미:

| 컬럼 | 뜻 |
| --- | --- |
| `member_id` | 누구에 관한 알림인가 (피보호자) |
| `user_id` | 누구에게 보냈나 (수신자) |
| `log_id` | 어떤 감지 때문인가 (없을 수 있음) |
| `notification_type` | 무슨 내용인가 |
| `delivery_type` | 어떻게 보냈나 |
| `is_read` | 읽었나 |
| `sent_at` | 언제 보냈나 |

> **주의 1 — `log_id` nullable**: `DEVICE_OFFLINE` 알림은 센서 감지가 아니라 스케줄러가 `device_last_seen`으로 판단하므로 대응되는 log가 없을 수 있다. 따라서 `log_id`는 nullable이 맞다.
>
> **주의 2 — `user_id` NOT NULL 재검토**: 피보호자(Member)는 가족(users) 없이 기관(organization)에만 속할 수 있다. 기관 소속 피보호자 알림은 수신자가 user가 아닐 수 있으므로 `user_id NOT NULL`은 재검토가 필요하다.
>
> **주의 3 — 발송 로직 부재**: 현재 코드에 실제 푸시/문자 발송(FCM·SMS 연동)은 없다. 이 테이블은 "발송 이력을 남길 자리"이며, 실제 발송 기능은 별도 구현이 필요하다.

### 1-3. `member_info` — `device_last_seen` 컬럼 추가 (기기 온/오프라인)

`device_online` BOOLEAN을 저장하지 않고 `device_last_seen`으로 **API에서 계산**한다. (저장 시 스케줄러와 `last_seen`/`online` 불일치 문제가 생길 수 있어서)

```sql
ALTER TABLE member_info
    ADD COLUMN device_last_seen DATETIME;
```

- `/api/device/data` 수신 시마다 `device_last_seen`을 현재 시각으로 갱신 (→ `updateFromDevice()`에 한 줄 추가)
- 온라인 여부는 응답 생성 시 계산:

```java
// 예: 5분 기준
boolean deviceOnline = member.getDeviceLastSeen() != null
    && member.getDeviceLastSeen().isAfter(LocalDateTime.now().minusMinutes(5));
```

---

## 2. 추가 API 목록

### 2-1. 보호자 앱 (`ROLE_USER`)

| Endpoint | Method | 설명 |
| --- | --- | --- |
| `/api/wards/me/health` | GET | 피보호자 건강 정보 조회 |
| `/api/wards/me/health` | PUT | 건강 정보 등록/수정 |
| `/api/wards/me/logs` | GET | 낙상 이력 조회 (`?page=0&size=20` 또는 `?from=&to=`) |
| `/api/wards/me/notifications` | GET | 알림 수신 이력 조회 |
| `/api/wards/me/notifications/{id}/read` | PATCH | 알림 읽음 처리 |
| `/api/wards/me/notifications/read-all` | PATCH | 알림 전체 읽음 처리 |
| `/api/wards/me/emergency-log` | POST | 119 신고 버튼 클릭 이력 저장 |

> - 건강정보는 생성보다 수정이 주 동작이라 `POST` 대신 `PUT`이 REST 원칙에 부합.
> - `emergency-log`는 실제 통화 성공 여부를 백엔드가 알 수 없으므로 "버튼을 눌렀다"는 이력 기록임을 이름에 드러냄.

### 2-2. 사회복지사 웹 (`ROLE_ADMIN`)

| Endpoint | Method | 설명 |
| --- | --- | --- |
| `/api/targets/{targetId}` | GET | 피보호자 상세 (건강정보·기기 상태 포함) |
| `/api/targets/{targetId}/health` | GET | 건강 정보 조회 |
| `/api/targets/{targetId}/logs` | GET | 낙상 이력 조회 (페이지네이션/기간) |
| `/api/reports/{targetId}` | GET | AI 월간 리포트 (`?month=2026-07`) |

> `/api/targets/{targetId}` 상세 응답에 `deviceOnline`, `deviceLastSeen`, `health`를 한 번에 포함하면 별도 `/device-status` 엔드포인트가 불필요하다.

```json
// GET /api/targets/{targetId} 응답 예시
{
  "name": "홍길동",
  "age": 80,
  "address": "서울시 ...",
  "deviceOnline": true,
  "deviceLastSeen": "2026-07-11T10:32:00",
  "health": {
    "disease": "고혈압, 당뇨",
    "medication": "메트포르민",
    "memo": "낙상 이력 있음"
  }
}
```

---

## 3. 권한(Role) 기반 접근 제어

API마다 역할 기반 접근 제어를 적용한다. **현재 코드의 Role 명칭(`USER`/`ADMIN`)** 기준으로 작성.

| Role | 접근 가능 경로 |
| --- | --- |
| `ROLE_USER` (보호자) | `/api/wards/me/**` |
| `ROLE_ADMIN` (기관) | `/api/targets/**`, `/api/reports/**` |
| 공통 (인증 불필요) | `/api/auth/**`, `/api/org/login`, `/api/org/signup`, `/api/device/data` |

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/org/login", "/api/org/signup").permitAll()
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/device/data").permitAll()
    .requestMatchers("/api/wards/**").hasRole("USER")        // 기존 permitAll → 보안 구멍 차단
    .requestMatchers("/api/targets/**").hasRole("ADMIN")
    .requestMatchers("/api/reports/**").hasRole("ADMIN")
    .anyRequest().authenticated()
);
```

> `hasRole("USER")`는 실제로 `ROLE_USER` 권한을 요구한다(접두사 `ROLE_` 자동 부여). 현재 토큰이 `ROLE_USER`/`ROLE_ADMIN`으로 발급되므로 명칭만 맞으면 그대로 동작한다.

---

## 4. AI 기반 정기 리포트 설계

별도 report 테이블 없이 `fall_log` 집계 데이터를 AI에 전달해 자연어 리포트를 생성한다. 데이터가 많아져 느려지면 그때 배치 캐싱을 도입한다.

### 처리 흐름

```
GET /api/reports/{targetId}?month=2026-07
    ↓ ① fall_log에서 해당 기간 집계
    ↓ ② 구조화된 프롬프트로 변환
    ↓ ③ Generative AI API 호출
    ↓ ④ 자연어 리포트 반환
```

### 집계 항목

```java
ReportSummary {
    String targetName;         // 피보호자 이름
    String period;             // 기간 (예: 2026년 7월)
    int    fallCount;          // 낙상 발생 횟수
    int    warningCount;       // WARNING 발생 횟수
    int    deviceOfflineCount; // 기기 오프라인 횟수
    String mostActiveTime;     // 활동 많은 시간대
    String leastActiveTime;    // 활동 적은 시간대
    int    noActivityDays;     // 하루 종일 움직임 없던 일수
}
```

### 응답 예시

```json
{
  "targetName": "홍길동",
  "period": "2026년 7월",
  "summary": { "fallCount": 2, "warningCount": 5, "deviceOfflineCount": 1 },
  "aiReport": "홍길동 어르신의 7월 케어 리포트입니다.\n\n이번 달에는 낙상이 2회 감지되었으며...",
  "generatedAt": "2026-07-11T10:00:00"
}
```

### 선행 과제 (중요)

현재 `fall_log`에는 상태변화(FALL_EVENT)·센서고장(SENSOR_FAILURE)만 적재된다. 리포트의 `mostActiveTime` / `leastActiveTime` / `noActivityDays` / `deviceOfflineCount`는 **지금 데이터로 계산 불가**하다. 활동 시간대·오프라인 이력을 먼저 로그에 쌓는 작업이 선행되어야 한다.

### 성능 고려사항

- AI 호출 포함이라 응답이 느릴 수 있음 → **비동기 처리** 또는 **로딩 UI** 필요
- 동일 월 리포트는 **Redis 캐싱**(TTL 1시간)으로 중복 호출 방지
- 데이터 증가 시 배치 스케줄러로 매월 1일 자동 생성 후 별도 테이블 저장 방식으로 전환 가능

---

## 5. 우선순위

| 순위 | 기능 | 난이도 | 비고 |
| --- | --- | --- | --- |
| 0 | **Role 명칭 정리 + `/api/wards/**` 권한 설정** | 낮음 | 이후 모든 API 권한의 전제 |
| 1 | 낙상 이력 조회 (보호자 + 사회복지사) | 낮음 | 기존 `fall_log` 활용, API + 페이지네이션 |
| 2 | 기기 상태 표시 | 낮음 | `device_last_seen` 추가, API 계산 |
| 3 | 119 연결 버튼 | 낮음 | 프론트 딥링크 + emergency-log API |
| 4 | 알림 수신 이력 | 중간 | `notification` 테이블 신설 |
| 5 | 기저질환 / 병력 | 중간 | `member_health` 테이블 신설 |
| 6 | AI 정기 리포트 | 높음 | 집계 쿼리 + 로그 데이터 선행 + AI 연동 |

> 원본 기획 우선순위(1~6)에 **0순위(정합성 정리)** 를 추가했다. 0번이 안 맞으면 이후 권한 설정이 전부 꼬인다.
