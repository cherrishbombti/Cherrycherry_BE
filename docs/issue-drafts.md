# GitHub 이슈 작성용 초안 (로컬 참고용)

> 아래 각 블록을 GitHub 이슈 템플릿(✨/📌/🌱)에 그대로 붙여넣기.
> 유형(type): #1~#6 모두 **feature** (신규 기능). #0 권한 정합성만 refactor였고 이미 완료(commit `bf4c611`).
> 착수 순서: #1 → #2 → #3 → #4 → #5 → #6

---

## 이슈 1

**Title**: `[feature] 낙상 이력 조회 API (보호자/사회복지사)`
**Type**: feature

```markdown
## ✨ 기능 요약

fall_log에 쌓이는 낙상/센서 이력을 조회하는 API 추가.
보호자 앱과 사회복지사 웹 양쪽에서 페이지네이션·기간 조회를 지원한다.

## 📌 작업 내용

- [ ] LogRepository 조회 메서드 추가 (최신순 페이지네이션 / 기간 조회)
- [ ] LogResponse + 페이지 응답 DTO 작성
- [ ] GET /api/wards/me/logs (ROLE_USER)
- [ ] GET /api/targets/{targetId}/logs (ROLE_ADMIN, 소속 검증)
- [ ] ?page&size 및 ?from&to 파라미터 처리

## 🌱 참고 사항

- 기존 fall_log 테이블 그대로 활용 (DB 변경 없음)
- 정렬 기준: detectedAt 최신순
- 기관 조회 시 타 기관 피보호자 접근 차단 필수
```

---

## 이슈 2

**Title**: `[feature] 기기 온라인 상태 표시 (device_last_seen)`
**Type**: feature

```markdown
## ✨ 기능 요약

디바이스(라즈베리파이) 연결 여부를 화면에 표시.
device_online을 저장하지 않고 마지막 수신 시각만 저장, 온라인 여부는 API에서 계산한다.

## 📌 작업 내용

- [ ] member_info에 device_last_seen 컬럼 추가 (Member 엔티티 필드)
- [ ] updateFromDevice()에서 device_last_seen 갱신 + 등록 시 초기화
- [ ] Member.isDeviceOnline() 헬퍼 (최근 5분 기준)
- [ ] 상세/요약/센서 응답 DTO에 deviceOnline, deviceLastSeen 추가

## 🌱 참고 사항

- online 값을 저장하면 스케줄러와 불일치(last_seen=방금, online=false)가 생겨 계산 방식 채택
- /api/device/data 수신 시마다 갱신됨
- 온라인 판정 기준(5분)은 협의 후 조정 가능
```

---

## 이슈 3

**Title**: `[feature] 119 신고 버튼 클릭 이력 저장`
**Type**: feature

```markdown
## ✨ 기능 요약

보호자 앱의 119 연결 버튼 클릭 이력을 기록.
실제 통화 성공 여부는 알 수 없으므로 "버튼을 눌렀다"는 이력만 남긴다.

## 📌 작업 내용

- [ ] LogType에 EMERGENCY_CALL 추가
- [ ] WardService.addEmergencyLog() 구현 (현재 피보호자로 로그 저장)
- [ ] POST /api/wards/me/emergency-log (ROLE_USER)

## 🌱 참고 사항

- 별도 테이블 없이 기존 fall_log 재사용 (logType=EMERGENCY_CALL)
- 추후 신고 이력 분리가 필요하면 테이블 신설로 전환
- 이슈 1의 이력 조회에 함께 노출할지 여부 결정
```

---

## 이슈 4

**Title**: `[feature] 알림 수신 이력 (notification) — 테이블/조회/읽음`
**Type**: feature

```markdown
## ✨ 기능 요약

알림함(종 아이콘) 기능. 낙상 감지(fall_log)와 별개로,
"누구에게 / 어떤 방식으로 알렸고 읽었는지"의 전달·수신 이력을 관리한다. (fall_log : notification = 1 : N)

## 📌 작업 내용

- [ ] notification 테이블 + Notification 엔티티 (NotificationType/DeliveryType enum)
- [ ] NotificationRepository (수신자별 최신순, 미읽음 카운트)
- [ ] GET /api/wards/me/notifications (목록)
- [ ] PATCH /api/wards/me/notifications/{id}/read (단건 읽음)
- [ ] PATCH /api/wards/me/notifications/read-all (전체 읽음)

## 🌱 참고 사항

- 스코프는 "테이블 + 조회/읽음"까지. 실제 PUSH/SMS 발송(FCM 등)은 별도 이슈로 분리
- user_id NOT NULL 여부 결정 필요 — 기관 소속 피보호자는 수신자가 user가 아닐 수 있음
- log_id는 nullable (DEVICE_OFFLINE 등 감지 없는 알림 존재)
```

---

## 이슈 5

**Title**: `[feature] 기저질환/병력 관리 (member_health)`
**Type**: feature

```markdown
## ✨ 기능 요약

기저질환·복용약·병력(민감정보)을 member_info와 분리한 1:1 테이블로 관리.
조회와 등록/수정 API를 제공한다.

## 📌 작업 내용

- [ ] member_health 테이블 + MemberHealth 엔티티 (member 1:1, member_id UNIQUE)
- [ ] MemberHealthRepository (findByMember)
- [ ] HealthResponse / HealthUpsertRequest DTO
- [ ] GET /api/wards/me/health, PUT /api/wards/me/health (ROLE_USER)
- [ ] GET /api/targets/{targetId}/health (ROLE_ADMIN)

## 🌱 참고 사항

- 생성보다 수정이 주 동작이므로 PUT(upsert) 사용 — 없으면 생성, 있으면 수정
- /api/targets/{id} 상세 응답에 health를 포함할지 여부 함께 결정
- 민감정보이므로 접근 권한 분리 확인
```

---

## 이슈 6 (에픽)

**Title**: `[feature] AI 기반 월간 리포트 (에픽)`
**Type**: feature (epic)

```markdown
## ✨ 기능 요약

fall_log 집계 데이터를 AI에 전달해 월간 자연어 케어 리포트를 생성.
범위가 커서 하위 작업으로 분할한다.

## 📌 작업 내용

- [ ] (6-1) 집계용 로그 데이터 적재 — 활동 시간대/기기 오프라인 이벤트 로깅 (선행)
- [ ] (6-2) 월별 집계 쿼리 + ReportSummary 구성
- [ ] (6-3) Generative AI 연동 + GET /api/reports/{targetId}?month=YYYY-MM (ROLE_ADMIN)
- [ ] (6-4) Redis 캐싱(TTL 1시간) + 비동기/로딩 처리

## 🌱 참고 사항

- 현재 fall_log엔 활동 시간대/오프라인 이력이 없어 mostActiveTime/noActivityDays/deviceOfflineCount 계산 불가 → 6-1이 반드시 선행
- AI 호출로 응답이 느릴 수 있어 비동기 또는 로딩 UI 필요
- 하위 6-1~6-4는 별도 이슈로 쪼개서 연결(트래킹) 권장
```
