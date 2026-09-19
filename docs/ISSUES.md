# Back-End 확장 이슈 초안

> `docs/Back-End-Add.md` 기획을 GitHub 이슈로 분할한 초안.
> 현재 코드 기준(테이블 `member_info`/`users`/`fall_log`, Role `USER`/`ADMIN`).
> **#0 권한 정합성은 완료됨** (commit `bf4c611`).
>
> 라벨 예시: `priority:1~6`, `size:S/M/L`, `db-change`
> 착수 순서: #1 → #2 → #3 → #4 → #5 → #6(에픽)

---

## #1 낙상 이력 조회 API (보호자 + 사회복지사)

**라벨**: `priority:1` `size:S`
**의존**: 없음 (기존 `fall_log` 활용)

### 배경
`fall_log`에 이미 상태변화/센서고장 이력이 쌓이고 있으나, 조회 API가 없다. 보호자 앱과 사회복지사 웹 양쪽에서 이력을 볼 수 있어야 한다.

### API
| Endpoint | Method | Role | 설명 |
| --- | --- | --- | --- |
| `/api/wards/me/logs` | GET | USER | 내 피보호자 낙상 이력 |
| `/api/targets/{targetId}/logs` | GET | ADMIN | 특정 피보호자 낙상 이력 |

- 파라미터: `?page=0&size=20` (기본) 또는 `?from=YYYY-MM-DD&to=YYYY-MM-DD` (기간)
- 정렬: `detectedAt` 최신순

### 작업 체크리스트
- [ ] `LogRepository`: `findByMemberOrderByDetectedAtDesc(Member, Pageable)`, `findByMemberAndDetectedAtBetweenOrderByDetectedAtDesc(...)`
- [ ] `LogResponse` DTO (id, detectedAt, status, logType, sensorDetail)
- [ ] 페이지 응답 래퍼 (`content, page, size, totalElements, totalPages, last`)
- [ ] `WardService.getLogs()` / `MemberService.getLogs(orgId, targetId, ...)` (기관은 소속 검증 포함)
- [ ] 컨트롤러 2개 엔드포인트 추가

### 완료 조건
- [ ] 보호자 토큰으로 `/api/wards/me/logs` 페이지네이션 조회 성공
- [ ] 기관 토큰으로 `/api/targets/{id}/logs` 조회 성공, 타 기관 피보호자 접근 시 차단
- [ ] `from/to` 기간 필터 동작

---

## #2 기기 온라인 상태 표시

**라벨**: `priority:2` `size:S` `db-change`
**의존**: 없음

### 배경
디바이스(라즈베리파이) 연결 여부를 화면에 표시해야 한다. `device_online`을 저장하면 스케줄러와 불일치가 생기므로, 마지막 수신 시각만 저장하고 온라인 여부는 API에서 계산한다.

### 작업 체크리스트
- [ ] `member_info`에 `device_last_seen DATETIME` 컬럼 추가 (`Member` 엔티티 필드)
- [ ] `Member.updateFromDevice()`에서 `deviceLastSeen = now()` 갱신 + 등록 시 초기화
- [ ] `Member.isDeviceOnline()` 헬퍼 (예: 최근 5분 이내면 online)
- [ ] 응답 DTO에 `deviceOnline`, `deviceLastSeen` 포함 — `MemberDetailResponse`(기관 상세), `MemberSummaryResponse.MemberInfo`, `WardSensorResponse`

### 완료 조건
- [ ] `/api/device/data` 수신 후 `deviceLastSeen`이 갱신됨
- [ ] 상세/요약 응답에 `deviceOnline`(계산값), `deviceLastSeen` 노출
- [ ] 5분 이상 미수신 시 `deviceOnline=false`

---

## #3 119 신고 버튼 이력 저장

**라벨**: `priority:3` `size:S`
**의존**: 없음

### 배경
보호자 앱의 119 연결 버튼 클릭 이력을 남긴다. 실제 통화 성공 여부는 백엔드가 알 수 없으므로 "버튼을 눌렀다"는 이력만 기록한다.

### API
| Endpoint | Method | Role | 설명 |
| --- | --- | --- | --- |
| `/api/wards/me/emergency-log` | POST | USER | 119 버튼 클릭 이력 저장 |

### 작업 체크리스트
- [ ] `LogType`에 `EMERGENCY_CALL` 추가
- [ ] `WardService.addEmergencyLog()` — 현재 피보호자로 `fall_log` 1건 저장(logType=EMERGENCY_CALL, status=현재 상태)
- [ ] 컨트롤러 엔드포인트 추가

### 완료 조건
- [ ] POST 호출 시 `fall_log`에 `EMERGENCY_CALL` 이력 저장됨
- [ ] (선택) #1 이력 조회에서 함께 노출

> 참고: 별도 테이블 없이 기존 `fall_log` 재사용. 추후 신고 이력을 분리해야 하면 테이블 신설로 전환.

---

## #4 알림 수신 이력 (notification)

**라벨**: `priority:4` `size:M` `db-change`
**의존**: 없음 (단, 실제 발송 연동은 스코프 밖 — 아래 참고)

### 배경
알림함(종 아이콘) 기능. `fall_log`가 "사건 기록"이라면 `notification`은 "누구에게/어떻게 알렸고 읽었는지"의 전달·수신 기록이다. `fall_log : notification = 1 : N`.

### DB
`notification` 테이블 신설:
- `member_id`(→member_info), `user_id`(→users, **nullable 검토**), `log_id`(→fall_log, nullable)
- `notification_type` (FALL/WARNING/DEVICE_OFFLINE/EMERGENCY), `delivery_type` (PUSH/SMS)
- `is_read`, `sent_at`, `created_at`

> **결정 필요 1**: `user_id` — 기관 소속 피보호자는 수신자가 user가 아닐 수 있음. NOT NULL 여부 확정.
> **결정 필요 2**: 이 이슈 스코프 = "테이블 + 조회/읽음 API"만. 실제 PUSH/SMS 발송(FCM 등) 연동은 **별도 이슈**.

### API
| Endpoint | Method | Role | 설명 |
| --- | --- | --- | --- |
| `/api/wards/me/notifications` | GET | USER | 알림 목록 |
| `/api/wards/me/notifications/{id}/read` | PATCH | USER | 읽음 처리 |
| `/api/wards/me/notifications/read-all` | PATCH | USER | 전체 읽음 처리 |

### 작업 체크리스트
- [ ] `Notification` 엔티티 + `NotificationType`/`DeliveryType` enum
- [ ] `NotificationRepository` (수신자별 최신순, 미읽음 카운트)
- [ ] `NotificationResponse` DTO
- [ ] 서비스: 목록 조회 / 단건 읽음 / 전체 읽음 (본인 소유 검증)
- [ ] 컨트롤러 3개 엔드포인트

### 완료 조건
- [ ] 목록 조회 시 본인 알림만 최신순 반환
- [ ] 읽음/전체읽음 후 `is_read` 반영
- [ ] 타인 알림 read 시도 차단

---

## #5 기저질환 / 병력 (member_health)

**라벨**: `priority:5` `size:M` `db-change`
**의존**: 없음

### 배경
기저질환·복용약·병력은 민감정보이므로 `member_info`와 분리한 1:1 테이블로 관리한다.

### DB
`member_health` 테이블 신설: `member_id`(UNIQUE→member_info), `disease`, `medication`, `memo`, `created_at`, `updated_at`

### API
| Endpoint | Method | Role | 설명 |
| --- | --- | --- | --- |
| `/api/wards/me/health` | GET | USER | 건강정보 조회 |
| `/api/wards/me/health` | PUT | USER | 건강정보 등록/수정 |
| `/api/targets/{targetId}/health` | GET | ADMIN | 건강정보 조회 |

> 생성보다 수정이 주 동작이므로 `PUT` 사용(upsert).

### 작업 체크리스트
- [ ] `MemberHealth` 엔티티 (member 1:1)
- [ ] `MemberHealthRepository` (`findByMember`)
- [ ] `HealthResponse` / `HealthUpsertRequest` DTO
- [ ] 서비스: 조회 / upsert (없으면 생성, 있으면 수정)
- [ ] 컨트롤러 엔드포인트 (ward 2 + target 1)
- [ ] (연계) `/api/targets/{id}` 상세 응답에 `health` 포함 여부 결정

### 완료 조건
- [ ] PUT으로 최초 등록·재수정 모두 동작
- [ ] 보호자/기관 각각 조회 성공, 권한 분리 확인

---

## #6 AI 기반 정기 리포트 (에픽)

**라벨**: `priority:6` `size:L` `epic`
**의존**: 6-1 → 6-2 → 6-3 → 6-4 순

### 배경
`fall_log` 집계 데이터를 AI에 전달해 월간 자연어 리포트를 생성한다. 한 이슈로 담기엔 커서 하위 이슈로 분할한다.

### 하위 이슈

**6-1 집계용 로그 데이터 적재 (선행, 중요)**
- 현재 `fall_log`엔 활동 시간대·기기 오프라인 이벤트가 없음 → 리포트의 `mostActiveTime`/`noActivityDays`/`deviceOfflineCount` 계산 불가
- [ ] 어떤 이벤트를 로그로 남길지 정의 및 적재 로직 추가

**6-2 집계 쿼리 + ReportSummary 구성**
- [ ] 월별 집계 쿼리 (fallCount, warningCount, deviceOfflineCount, 활동 시간대 등)
- [ ] `ReportSummary` 조립

**6-3 Generative AI 연동**
- [ ] 프롬프트 구조화 → AI API 호출 → 자연어 리포트
- [ ] `GET /api/reports/{targetId}?month=YYYY-MM` (ROLE_ADMIN)

**6-4 캐싱/비동기**
- [ ] 동일 월 리포트 Redis 캐싱 (TTL 1시간)
- [ ] 비동기 처리 또는 로딩 UI 대응
- [ ] (확장) 배치 스케줄러 월 1회 사전 생성

### 완료 조건 (에픽)
- [ ] `/api/reports/{id}?month=` 호출 시 집계 + AI 리포트 응답
- [ ] 동일 월 재호출 시 캐시 히트
