#!/usr/bin/env bash
#
# 라즈베리파이 기기 시뮬레이터
#
# 서버 입장에서 기기는 "정해진 모양의 JSON 을 주기적으로 보내는 무언가" 일 뿐이므로,
# 같은 요청을 같은 간격으로 보내면 실제 기기와 구분되지 않는다.
#
# 실기기로는 만들 수 없는 상황(정확한 유실률, 초 단위 단절, 특정 센서만 고장)을
# 재현하기 위한 도구다. 기기 없이 서버 동작을 확인할 때도 쓴다.
#
# 기기 실제 동작 (하드웨어 담당 확인, 2026-09):
#   - HEARTBEAT 5초 주기, 상태·센서 변화 시 즉시 EVENT
#   - EVENT 만 재시도 (같은 seq 로 최대 4회, 지수 백오프). HEARTBEAT 은 재시도 없음
#   - 버퍼링 없음 (끊긴 동안의 데이터는 복구돼도 오지 않음)
#   - sensor_health 는 HEARTBEAT 에도 매번 실림

set -uo pipefail

URL="http://localhost:8080/api/device/data"
DEVICE_ID=""
INTERVAL=5
DURATION=60
LOSS=0
FAIL_SENSOR=""
FAIL_AT=0
DIE_AFTER=0
EVENT_RETRIES=1
BATTERY=85
RSSI=-55
DRY_RUN=0
EVENTS=()

usage() {
    cat <<'USAGE'
사용법: tools/device-sim.sh --device <device_id> [옵션]

필수
  --device <id>        등록된 device_mac 과 문자열이 완전히 일치해야 한다
                       (콜론·대소문자 포함. 다르면 서버가 404 D001 로 거부한다)

기본 동작
  --interval <초>      하트비트 주기 (기본 5 — 실제 기기값)
  --duration <초>      전체 실행 시간 (기본 60)
  --url <주소>         기본 http://localhost:8080/api/device/data

시나리오
  --loss <퍼센트>      이 확률로 하트비트를 보내지 않는다 (기본 0)
                       실제로도 HEARTBEAT 은 재시도가 없어 단발 유실이 정상이다
  --fail <센서>        vibrator | radar | thermal — 해당 센서를 FAIL 로 보고
  --fail-at <초>       고장이 시작되는 시점 (기본 0 = 처음부터)
  --die-after <초>     이 시점부터 전송을 멈춘다 (서버의 단절 감지를 관찰용)
                       스크립트는 --duration 까지 살아서 남은 시간을 세어준다
  --event <초>:<상태>  지정 시각에 EVENT 를 보낸다. SAFE|WARNING|DANGER
                       여러 번 쓸 수 있다.  예) --event 10:WARNING --event 20:DANGER
  --event-retries <n>  EVENT 를 같은 내용으로 n 번 연속 보낸다 (기본 1)
                       기기의 재시도(최대 4회)를 흉내내 서버의 중복 억제를 확인한다

기타
  --battery <0-100>    기본 85       --rssi <음수>   기본 -55
  --dry-run            보내지 않고 payload 만 출력한다

예시
  # 정상 동작 확인
  tools/device-sim.sh --device 5C:8A:AE:C6:DF:BE --duration 30

  # 단절 감지: 30초 보내다 멈추고, 서버가 알아채는지 본다
  tools/device-sim.sh --device 5C:8A:AE:C6:DF:BE --duration 90 --die-after 30

  # 임계값 실험: 10% 유실 상태에서 오탐이 나는지
  tools/device-sim.sh --device 5C:8A:AE:C6:DF:BE --duration 300 --loss 10

  # 센서 고장: 20초부터 레이더 고장. 기록이 1줄만 남아야 한다
  tools/device-sim.sh --device 5C:8A:AE:C6:DF:BE --duration 60 --fail radar --fail-at 20

  # 알림 억제: 같은 EVENT 를 4번 재전송해도 알림은 1건이어야 한다
  tools/device-sim.sh --device 5C:8A:AE:C6:DF:BE --duration 20 --event 5:DANGER --event-retries 4
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device)        DEVICE_ID="$2"; shift 2 ;;
        --url)           URL="$2"; shift 2 ;;
        --interval)      INTERVAL="$2"; shift 2 ;;
        --duration)      DURATION="$2"; shift 2 ;;
        --loss)          LOSS="$2"; shift 2 ;;
        --fail)          FAIL_SENSOR="$2"; shift 2 ;;
        --fail-at)       FAIL_AT="$2"; shift 2 ;;
        --die-after)     DIE_AFTER="$2"; shift 2 ;;
        --event)         EVENTS+=("$2"); shift 2 ;;
        --event-retries) EVENT_RETRIES="$2"; shift 2 ;;
        --battery)       BATTERY="$2"; shift 2 ;;
        --rssi)          RSSI="$2"; shift 2 ;;
        --dry-run)       DRY_RUN=1; shift ;;
        -h|--help)       usage; exit 0 ;;
        *) echo "알 수 없는 옵션: $1" >&2; echo; usage; exit 1 ;;
    esac
done

if [[ -z "$DEVICE_ID" ]]; then
    echo "오류: --device 는 필수다. 피보호자 등록 시의 device_mac 과 같아야 한다." >&2
    echo; usage; exit 1
fi
if [[ -n "$FAIL_SENSOR" && ! "$FAIL_SENSOR" =~ ^(vibrator|radar|thermal)$ ]]; then
    echo "오류: --fail 은 vibrator | radar | thermal 중 하나여야 한다 (받은 값: $FAIL_SENSOR)" >&2
    exit 1
fi

SENT=0; SKIPPED=0; FAILED=0; EVENTS_SENT=0

# 경과 초에 따라 sensor_health JSON 을 만든다.
sensor_health() {
    local elapsed="$1"
    local v="OK" r="OK" t="OK"
    if [[ -n "$FAIL_SENSOR" && "$elapsed" -ge "$FAIL_AT" ]]; then
        case "$FAIL_SENSOR" in
            vibrator) v="FAIL" ;;
            radar)    r="FAIL" ;;
            thermal)  t="FAIL" ;;
        esac
    fi
    printf '{"vibrator":"%s","radar":"%s","thermal":"%s"}' "$v" "$r" "$t"
}

# $1 = payload, $2 = 화면에 찍을 라벨
send() {
    local payload="$1" label="$2"
    local stamp; stamp=$(date '+%H:%M:%S')

    if [[ "$DRY_RUN" -eq 1 ]]; then
        printf '%s  %-26s %s\n' "$stamp" "$label" "$payload"
        SENT=$((SENT + 1))
        return
    fi

    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL" \
                -H 'Content-Type: application/json' -d "$payload" --max-time 5)

    if [[ "$code" == "200" ]]; then
        printf '%s  %-26s HTTP %s\n' "$stamp" "$label" "$code"
        SENT=$((SENT + 1))
    else
        # 서버가 거부한 이유는 응답 본문에 있다. device_id 불일치가 가장 흔하다.
        printf '%s  %-26s HTTP %s  <- 거부됨\n' "$stamp" "$label" "$code"
        FAILED=$((FAILED + 1))
    fi
}

echo "기기 시뮬레이터 시작"
echo "  대상      : $URL"
echo "  device_id : $DEVICE_ID"
printf '  주기      : %s초,  실행 %s초\n' "$INTERVAL" "$DURATION"
[[ "$LOSS" -gt 0 ]]        && echo "  유실률    : ${LOSS}%"
[[ -n "$FAIL_SENSOR" ]]    && echo "  센서 고장 : $FAIL_SENSOR (${FAIL_AT}초부터)"
[[ "$DIE_AFTER" -gt 0 ]]   && echo "  단절      : ${DIE_AFTER}초 뒤 전송 중단"
[[ "${#EVENTS[@]}" -gt 0 ]] && echo "  EVENT     : ${EVENTS[*]}  (각 ${EVENT_RETRIES}회 전송)"
[[ "$DRY_RUN" -eq 1 ]]     && echo "  ** dry-run: 실제로 보내지 않는다 **"
echo

START=$(date +%s)
ELAPSED=0
NEXT_HEARTBEAT=0
DIED_NOTICE=0

# 1초 간격으로 돌면서 하트비트는 INTERVAL 마다만 보낸다.
# 루프 자체를 INTERVAL 로 돌리면 EVENT 가 하트비트 주기에 붙어버리는데,
# 실제 기기는 상태가 바뀌는 즉시 EVENT 를 보내므로 감지 지연 측정이 그만큼 어긋난다.
while [[ "$ELAPSED" -lt "$DURATION" ]]; do
    ELAPSED=$(( $(date +%s) - START ))

    # 지정 시각에 도달한 EVENT (상태 변화 시 즉시 전송)
    for i in "${!EVENTS[@]}"; do
        entry="${EVENTS[$i]}"
        [[ -z "$entry" ]] && continue
        at="${entry%%:*}"; status="${entry##*:}"
        if [[ "$ELAPSED" -ge "$at" ]]; then
            EVENTS[$i]=""
            for ((n = 1; n <= EVENT_RETRIES; n++)); do
                payload=$(printf '{"device_id":"%s","report_type":"EVENT","event_type":"%s","sensor_health":%s,"device":{"battery_pct":%s,"rssi":%s}}' \
                          "$DEVICE_ID" "$status" "$(sensor_health "$ELAPSED")" "$BATTERY" "$RSSI")
                label="EVENT $status"
                [[ "$EVENT_RETRIES" -gt 1 ]] && label="EVENT $status 재전송 $n/$EVENT_RETRIES"
                send "$payload" "$label"
                EVENTS_SENT=$((EVENTS_SENT + 1))
            done
        fi
    done

    if [[ "$DIE_AFTER" -gt 0 && "$ELAPSED" -ge "$DIE_AFTER" ]]; then
        if [[ "$DIED_NOTICE" -eq 0 ]]; then
            printf '%s  %-26s (이후 무신호 - 서버가 단절을 감지해야 한다)\n' \
                   "$(date '+%H:%M:%S')" "*** 전송 중단 ***"
            DIED_NOTICE=1
        fi
    elif [[ "$ELAPSED" -ge "$NEXT_HEARTBEAT" ]]; then
        NEXT_HEARTBEAT=$((NEXT_HEARTBEAT + INTERVAL))

        # 유실 흉내: HEARTBEAT 은 실패해도 재시도하지 않으므로 그냥 거른다
        if [[ "$LOSS" -gt 0 && $((RANDOM % 100)) -lt "$LOSS" ]]; then
            printf '%s  %-26s (유실 흉내)\n' "$(date '+%H:%M:%S')" "HEARTBEAT 건너뜀"
            SKIPPED=$((SKIPPED + 1))
        else
            payload=$(printf '{"device_id":"%s","report_type":"HEARTBEAT","sensor_health":%s,"device":{"battery_pct":%s,"rssi":%s}}' \
                      "$DEVICE_ID" "$(sensor_health "$ELAPSED")" "$BATTERY" "$RSSI")
            send "$payload" "HEARTBEAT"
        fi
    fi

    /bin/sleep 1
done

echo
echo "종료 (${DURATION}초)"
echo "  전송 성공 : $SENT   (EVENT $EVENTS_SENT 건 포함)"
echo "  유실 흉내 : $SKIPPED"
echo "  거부/실패 : $FAILED"
if [[ "$FAILED" -gt 0 ]]; then
    echo
    echo "  거부가 있다면 device_id 가 등록된 device_mac 과 다른 경우가 대부분이다."
    echo "  서버 로그의 \"등록되지 않은 device_id\" 줄에 서버가 받은 값이 찍혀 있다."
fi
