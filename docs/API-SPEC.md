# API 명세서 (프론트 연동용)

> 낙상감지 IoT 시스템 백엔드 API
> **✅ 구현완료** = 지금 호출 가능 / **🔜 예정** = 계약만 확정, 목 데이터로 선구현 가능
> 🔜 항목은 구현 시 변경될 수 있으며, 변경 시 사전 공유합니다.

## 공통 사항

**인증**
```
Authorization: Bearer <token>
```

**권한**

| Role | 접근 경로 |
| --- | --- |
| `ROLE_ADMIN` (기관/사회복지사) | `/api/targets/**`, `/api/reports/**`, `/api/org/me` |
| `ROLE_USER` (보호자/가족) | `/api/wards/**` |
| 인증 불필요 | `/api/org/login`, `/api/org/signup`, `/api/auth/**`, `/api/device/data` |

**에러 응답 (공통 포맷)**
```json
{ "status": 404, "code": "M001", "message": "연결된 피보호자를 찾을 수 없습니다." }
```

| 코드 | HTTP | 의미 | 프론트 처리 |
| --- | --- | --- | --- |
| - | 401 | 토큰 없음/만료/무효 | 로그인 페이지 이동 |
| - | 403 | 권한 없음 (역할 불일치) | "접근 권한 없음" 안내 |
| `A001` | 401 | 로그인 실패 | "아이디 또는 비밀번호를 확인해주세요" |
| `O002` | 409 | 기관 ID 중복 | "이미 사용 중인 아이디입니다" |
| `M001` | 404 | 피보호자 없음 / 타 기관 접근 | "존재하지 않습니다" |
| `C003` | 400 | 잘못된 조회 기간 (from > to) | 날짜 확인 안내 |
| `D001` | 404 | 미등록 디바이스 | (기기 전용) |
| `D002` | 400 | 잘못된 event_type | (기기 전용) |

**시각 포맷**: `"2026-07-22T14:23:00"` (타임존 표기 없음, **항상 KST 기준**). 서버 타임존이 `Asia/Seoul`로 고정되어 있어 배포 환경과 무관하게 한국 시각입니다. ISO-8601 오프셋(`+09:00`) 표기 전환은 별도 이슈 예정.

---

# 1. 인증 / 계정

## ✅ 기관 로그인
```
POST /api/org/login
```
```json
// 요청
{ "orgId": "welfare01", "password": "****" }
// 응답 200
{ "token": "eyJhbGciOi..." }
```
- 실패 시 **401 `A001`** — 아이디 없음/비밀번호 불일치를 구분하지 않음 (계정 존재 여부 노출 방지)

## ✅ 기관 회원가입
```
POST /api/org/signup
```
```json
{ "orgId": "welfare01", "password": "****", "name": "○○종합사회복지관" }
```
- 중복 시 **409 `O002`**

## ✅ 로그인한 기관 정보 (헤더 이름 표시용)
```
GET /api/org/me
```
```json
{ "name": "○○종합사회복지관", "orgId": "welfare01" }
```

## ✅ 소셜 로그인 (보호자)
```
GET  /api/auth/{google|kakao}            → 소셜 로그인 페이지로 리다이렉트
GET  /api/auth/{provider}/callback       → 콜백 후 프론트로 리다이렉트
     리다이렉트 URL: {FRONTEND_URL}?token=xxx&isNewUser=true|false
POST /api/auth/login                     → { code, provider } 전달 시 JSON 응답
```
- `isNewUser=true` → 피보호자 등록 화면으로, `false` → 홈으로

---

# 2. 사회복지사 웹 (ROLE_ADMIN)

## ✅ 통합 관제 대시보드
```
GET /api/targets
```
```json
{
  "stats": { "total": 10, "safe": 6, "warning": 2, "danger": 2 },
  "members": [
    {
      "id": 1,
      "name": "김철수",
      "age": 78,
      "address": "서울시 강남구 대치동 123",
      "phone": "010-1111-2222",
      "status": "DANGER",
      "vibrator": true,
      "radar": true,
      "thermal": true,
      "deviceOnline": true,
      "deviceLastSeen": "2026-07-22T14:21:03"
    }
  ]
}
```

**필드 설명**

| 필드 | 설명 |
| --- | --- |
| `status` | `SAFE` / `WARNING` / `DANGER` (※ `EMERGENCY` 아님) |
| `vibrator`/`radar`/`thermal` | 각 센서 정상 여부 (false = 고장, **기기 첫 수신 전에는 `null`**) |
| `deviceOnline` | 기기 연결 여부 (서버 계산값, 최근 5분 기준) |
| `deviceLastSeen` | 마지막 기기 신호 수신 시각(서버 기준), **미수신 시 `null`** → "N분 전" 계산용 |

**기기 상태 3분기 처리 (필수)**

| 조건 | 표시 |
| --- | --- |
| `deviceLastSeen === null` | "연결 대기 중" — 기기 신호를 받은 적 없음 |
| `deviceOnline === false` | "오프라인" + 센서값 흐리게 + "마지막 수신 N분 전" |
| `deviceOnline === true` | "온라인" |

> ⚠️ 오프라인일 때 `status`는 **마지막 수신값 그대로**입니다. `SAFE`로 보여도 현재 상태가 아니므로 시각적으로 반드시 구분해주세요.
>
> 📌 **테스트 시 참고**: 기존에 등록된 피보호자는 `deviceLastSeen`이 모두 `null`입니다. 기기가 `/api/device/data`로 신호를 한 번 보내기 전까지는 "연결 대기 중"으로 표시되니, 전부 오프라인으로 보여도 정상입니다.
>
> 📌 **센서값도 `null`일 수 있습니다**: 신규 등록 직후에는 `vibrator`/`radar`/`thermal`이 모두 `null`입니다. 기기와 통신한 적이 없어 센서 상태를 알 수 없기 때문이며, "정상"으로 표시하면 실제로 고장난 기기도 초록불로 보이게 되어 의도적으로 비워둡니다. 이 경우 "확인 전" 또는 "-" 로 표시해주세요.

## ✅ 긴급 상태만 조회
```
GET /api/targets/emergencies
```
- `members[]`와 동일한 객체의 배열 (status=DANGER만)

## ✅ 피보호자 상세
```
GET /api/targets/{targetId}
```
```json
{
  "id": 1, "name": "김철수", "age": 78,
  "address": "서울시 강남구 대치동 123",
  "phone": "010-1111-2222",
  "deviceMac": "AA:BB:CC:DD:EE:01",
  "status": "DANGER",
  "vibrator": true, "radar": true, "thermal": true,
  "deviceOnline": true,
  "deviceLastSeen": "2026-07-22T14:21:03"
}
```
- 타 기관 피보호자 접근 시 **404 `M001`** (존재 여부를 노출하지 않기 위해 "없음"과 동일 응답)

## ✅ 피보호자 등록 / 삭제
```
POST   /api/targets          { name, age, address, contact, deviceMac }
DELETE /api/targets/{targetId}
```

## ✅ 낙상 이력 조회
```
GET /api/targets/{targetId}/logs?page=0&size=20&from=2026-07-01&to=2026-07-22
```
```json
{
  "content": [
    {
      "id": 12,
      "detectedAt": "2026-07-22T14:23:00",
      "status": "DANGER",
      "logType": "FALL_EVENT",
      "sensorDetail": null
    },
    {
      "id": 11,
      "detectedAt": "2026-07-21T09:40:00",
      "status": "SAFE",
      "logType": "SENSOR_FAILURE",
      "sensorDetail": "radar"
    }
  ],
  "page": 0, "size": 20,
  "totalElements": 42, "totalPages": 3, "last": false
}
```

**파라미터** (전부 선택)

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `page` | 0 | 페이지 번호 (0부터) |
| `size` | 20 | 페이지 크기 |
| `from` | - | 시작일 `YYYY-MM-DD` |
| `to` | - | 종료일 `YYYY-MM-DD` |

**from/to 조합 규칙**

| from | to | 결과 |
| --- | --- | --- |
| 없음 | 없음 | 전체 (최신순) |
| 있음 | 있음 | `from` 00:00:00 ~ `to` 23:59:59 (양 끝 포함) |
| 있음 | 없음 | `from`부터 현재까지 |
| 없음 | 있음 | 처음부터 `to` 23:59:59까지 |

- `from > to` → **400 `C003`** (달력 UI에서 선택 자체를 막아주세요)
- `from`/`to`와 `page`/`size`는 함께 사용 가능
- 정렬은 `detectedAt` 최신순 고정

**logType 매핑**

| 값 | 표시 | 비고 |
| --- | --- | --- |
| `FALL_EVENT` | 낙상 감지 | |
| `SENSOR_FAILURE` | 센서 이상 | `sensorDetail`에 `vibrator`/`radar`/`thermal` |
| `EMERGENCY_CALL` | 119 신고 | 보호자가 버튼을 누른 이력 |
| `DEVICE_OFFLINE` 🔜 | 기기 연결 끊김 | 미정 |
| `ACTIVE` 🔜 | 정상 활동 | 미정 |

---

# 3. 보호자 앱 (ROLE_USER)

## ✅ 피보호자 등록
```
POST /api/wards/me
```
```json
{
  "name": "김철수",
  "birthDate": "1948-03-15",
  "address": "서울시 ...",
  "phone": "010-1111-2222",      // 형식: 010-XXXX-XXXX
  "relationship": "아버지",
  "deviceMac": "AA:BB:CC:DD:EE:01"  // 형식: AA:BB:CC:DD:EE:FF
}
```
- 형식 위반 시 **400 `C001`**

## ✅ 홈 요약
```
GET /api/wards/me/summary
```
```json
{
  "wardName": "김철수",
  "relationship": "아버지",
  "phone": "010-1111-2222",
  "status": "SAFE",
  "totalActivityMinutes": 0,   // 미구현, 현재 0 고정
  "lastActivityMinutes": 0,    // 미구현, 현재 0 고정
  "deviceOnline": true,
  "deviceLastSeen": "2026-07-22T14:21:03"
}
```

## ✅ 센서 상태
```
GET /api/wards/me/sensors
```
```json
{
  "status": "SAFE",
  "vibrator": true, "radar": true, "thermal": true,
  "deviceOnline": true,
  "deviceLastSeen": "2026-07-22T14:21:03"
}
```

## ✅ 비상연락망
```
GET  /api/wards/me/contacts
POST /api/wards/me/contacts   { name, phone, relationship }
```
```json
// 응답
[{ "contactId": 1, "name": "김영희", "phone": "010-2222-3333",
   "relationship": "딸", "priority": 0 }]
```

## ✅ 낙상 이력 조회
```
GET /api/wards/me/logs?page=0&size=20&from=&to=
```
- 응답 구조·규칙은 사회복지사 웹의 이력 조회와 **완전히 동일**

## ✅ 119 신고 이력 저장
```
POST /api/wards/me/emergency-log
```
```json
// 요청 본문 없음
// 응답 200
{ "logId": 33, "detectedAt": "2026-07-22T14:23:00" }
```
> ⚠️ **이 API 응답을 기다리지 마세요.** 119 연결은 생명과 직결되므로 통화 시도가 최우선입니다.
> 버튼 클릭 → **즉시 `tel:119` 실행** → 로깅은 백그라운드(fire-and-forget)로 호출하는 순서로 구현해주세요.
> 로깅이 실패/지연되어도 통화 흐름에 영향이 없어야 하며, 에러 토스트도 띄우지 않는 것을 권장합니다.

- 실제 통화 성공 여부는 서버가 알 수 없으므로 "버튼을 눌렀다"는 이력만 기록됩니다
- 저장된 이력은 낙상 이력 조회에 `logType: "EMERGENCY_CALL"` 로 함께 노출됩니다
- 오프라인 시 재전송 큐/중복 방지(멱등키)는 현재 미지원 — 필요 시 별도 논의

---

# 4. 🔜 예정 API (계약 확정 — 목 데이터로 선구현 가능)

> 아래는 아직 구현 전이며, 필드명/구조는 이 명세를 기준으로 작업 예정입니다.

## 🔜 알림함 (이슈 #25)
```
GET   /api/wards/me/notifications?page=0&size=20
PATCH /api/wards/me/notifications/{id}/read
PATCH /api/wards/me/notifications/read-all
```
```json
{
  "unreadCount": 4,          // 헤더 종 아이콘 뱃지용
  "content": [
    {
      "id": 7,
      "notificationType": "FALL",   // FALL / WARNING / DEVICE_OFFLINE / EMERGENCY
      "memberName": "김철수",
      "logId": 12,                  // 관련 이력 (없으면 null)
      "isRead": false,
      "createdAt": "2026-07-22T14:23:05"
    }
  ],
  "page": 0, "size": 20, "totalElements": 12, "totalPages": 1, "last": true
}
```

## 🔜 건강 정보 (이슈 #26)
```
GET   /api/wards/me/health
PUT   /api/wards/me/health      (전체 등록/수정)
PATCH /api/wards/me/health      (부분 수정)
GET   /api/targets/{targetId}/health   (ROLE_ADMIN)
```
```json
{
  "disease": "고혈압, 당뇨",
  "medication": "메트포르민, 암로디핀",
  "memo": "2024년 낙상 이력 있음. 왼쪽 고관절 수술.",
  "updatedBy": "김복지사",
  "updatedByType": "ORGANIZATION",   // USER / ORGANIZATION
  "updatedAt": "2026-07-22T10:00:00"
}
```
> **PATCH 규칙**: `null`인 필드는 수정하지 않음(기존 값 유지), 빈 문자열 `""`은 값 비우기. 안 바꿀 필드는 아예 보내지 마세요.

## 🔜 AI 월간 리포트 (이슈 #27)
```
GET /api/reports/{targetId}?month=2026-07
```
```json
{
  "targetName": "김철수",
  "period": "2026년 7월",
  "summary": {
    "fallCount": 2,
    "warningCount": 5,
    "deviceOfflineCount": 1,
    "mostActiveTime": "10-12시",
    "leastActiveTime": "03시",
    "noActivityDays": 1
  },
  "aiReport": "김철수 어르신의 2026년 7월 케어 리포트입니다.\n\n...",
  "generatedAt": "2026-07-22T10:00:00"
}
```
> AI 호출로 **응답이 수 초 이상 걸릴 수 있습니다.** 로딩 UI 필수.

---

# 5. ❌ 아직 제공 불가 (목업에서 제외 필요)

| 목업 요소 | 사유 |
| --- | --- |
| **배터리 잔량 %** | 기기가 배터리 값을 전송하지 않음 → 기기팀 프로토콜 협의 필요 |
| **낙상 상세** ("침실 · 신뢰도 높음") | 위치·신뢰도 데이터 미수집 (기기/AI 모델 확인 필요) |
| **"활동 부재 3시간"** 등 상태 사유 | `status` 외에 사유(reason) 필드가 없음 |
| **이력 요약 배지** (낙상 12 / 경고 5 / 오프라인 1) | 이력 집계 API 미구현 |
| **비밀번호 찾기** | 미구현 |

> 위 항목은 UI 자리만 잡아두고 비활성/숨김 처리 후, 데이터 확보 시 연결하는 방식을 권장합니다.

---

# 6. ⚠️ 기존 구현 영향 (확인 필요)

| 변경 | 영향 |
| --- | --- |
| `/api/wards/**` 인증 필수화 | 이전엔 토큰 없이 호출 가능 → 토큰 없이 부르던 코드는 **401** |
| 로그인 실패 응답 | 500 → **401** 로 변경, 분기 처리 확인 |
| `members[]` 연락처 키 | `contact` → **`phone`** 으로 변경 |
| 미인증 응답 | 403 → **401** (로그인 리다이렉트 판단용) |
