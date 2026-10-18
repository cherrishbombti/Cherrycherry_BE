#!/usr/bin/env bash
#
# 재생 실험 전체 실행 — 임계값 5/10/20/40초 각각에 대해 서버를 다시 빌드해 띄우고,
# 수치 실험의 유실 시퀀스 N개를 단말 N대로 동시에 재생한 뒤 모형과 대조한다.
#
# 임계값은 코드 상수(Member.ONLINE_THRESHOLD_SECONDS)라 조건마다 바꿔 빌드한다.
# 끝나면(중간에 실패해도) 원래 값으로 되돌린다.
#
# 로컬 전용이다. .env 의 DB 로 붙고, 실험 중 생기는 끊김 알림이 실제 푸시로 나가지 않도록
# FIREBASE_ENABLED=false 로 띄운다. 단말·기관은 매 조건 새로 만들어 이전 데이터와 섞이지 않는다.
#
# 실행: tools/replay-run.sh [시행 수, 기본 30]      소요: 조건당 약 4~5분
#       CONDITIONS="5" tools/replay-run.sh 3         (일부 조건만 빠르게 확인)

set -euo pipefail
cd "$(dirname "$0")/.."

N="${1:-30}"
RUN="$(date +%Y%m%d-%H%M)"
OUT="docs/experiment-logs/replay/$RUN"
MEMBER="src/main/java/com/example/cherry_be/domain/member/entity/Member.java"
BACKUP="$(mktemp)"
SERVER_PID=""

cp "$MEMBER" "$BACKUP"
cleanup() {
    [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null && wait "$SERVER_PID" 2>/dev/null
    cp "$BACKUP" "$MEMBER" && rm -f "$BACKUP"
    echo "Member.java 원복 완료"
}
trap cleanup EXIT

if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
    echo "8080 포트에 이미 서버가 떠 있다. 먼저 내려야 한다." >&2; exit 1
fi

mkdir -p "$OUT"
python3 docs/paper-sim.py --export "$N" "$OUT/seqs.json"

for T in ${CONDITIONS:-5 10 20 40}; do
    echo; echo "===== 임계값 ${T}초 ====="
    D="$OUT/T$T"; mkdir -p "$D"
    sed -i '' -E "s/(ONLINE_THRESHOLD_SECONDS = )[0-9]+/\1$T/" "$MEMBER"
    ./gradlew -q bootJar -x test
    JAR="$(find build/libs -name '*.jar' ! -name '*-plain.jar' | head -1)"

    ( set -a; source .env; set +a; FIREBASE_ENABLED=false exec java -jar "$JAR" ) > "$D/server.log" 2>&1 &
    SERVER_PID=$!
    for _ in $(seq 120); do
        grep -q "Started Application" "$D/server.log" && break
        kill -0 "$SERVER_PID" 2>/dev/null || { echo "서버 기동 실패: $D/server.log" >&2; exit 1; }
        sleep 1
    done

    PREFIX="RPL-$RUN-T$T"
    python3 tools/replay-sim.py setup --prefix "$PREFIX" --count "$N" --members "$D/members.json"
    python3 tools/replay-sim.py run --prefix "$PREFIX" --seqs "$OUT/seqs.json" --out "$D/sends.csv" --count "$N"

    # 마지막 단말의 중단 후 판정까지 기다린다: 임계값 + 확인 주기 + 여유
    sleep $((T + 15))
    kill "$SERVER_PID"; wait "$SERVER_PID" 2>/dev/null || true; SERVER_PID=""

    # 원본은 SQL 로그까지 있어 수십 MB 가 된다. 판정에 쓰는 줄만 남긴다.
    grep -E "Started Application|기기 끊김 감지" "$D/server.log" | sed -E 's/\x1b\[[0-9;]*m//g' > "$D/server.trim" || true
    mv "$D/server.trim" "$D/server.log"
done

echo
python3 tools/replay-compare.py "$OUT" | tee "$OUT/results.md"
