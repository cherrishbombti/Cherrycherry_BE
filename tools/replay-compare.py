"""
재생 실험 대조 — 시행마다 모형 예측과 서버 판정을 맞대어 본다.

입력 디렉터리 구조 (tools/replay-run.sh 가 만든다)
  <dir>/seqs.json            paper-sim.py --export 결과 (시퀀스 + 시행별 모형 예측)
  <dir>/T<초>/server.log     서버 로그 ("기기 끊김 감지 - memberId" 줄)
  <dir>/T<초>/members.json   memberId → 단말 대응표 (서버 로그에는 기기 식별자가 없다)
  <dir>/T<초>/sends.csv      재생기의 전송 기록 (STOP 행 = 실제 중단 시각)

판정 분류는 paper-sim.py 와 같다. 실제 중단 시각보다 이른 판정은 오탐, 이후의 첫 판정은
실제 단절 통지이며, 이후 판정이 없으면 가림이다.

실행: python3 tools/replay-compare.py docs/experiment-logs/replay/<실행시각>
"""
import csv, json, re, statistics, sys
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

KST = ZoneInfo("Asia/Seoul")
LINE = re.compile(r"^(\S+)\s+INFO .*기기 끊김 감지 - memberId: (\d+)")
SLACK = 0.5   # 스케줄러 fixedDelay 는 실행 시간만큼 주기가 늘어나므로 상한에 둔 여유(초)


def epoch(ts):
    dt = datetime.fromisoformat(ts)
    return (dt if dt.tzinfo else dt.replace(tzinfo=KST)).timestamp()


def condition(d, T, trials):
    stops, sent = {}, {}
    with open(d / "sends.csv") as f:
        for r in csv.DictReader(f):
            if r["result"] == "STOP":
                stops[r["device"]] = float(r["scheduled"])
            elif r["result"] != "LOST":
                sent[(r["device"], int(r["tx"]))] = float(r["sent"])
    members = json.load(open(d / "members.json"))

    def last_receive(dev, at):
        # 서버의 deviceLastSeen = 판정 시각 이전의 마지막 성공 전송 (전송 직후 수신, 지연 수 ms)
        return max(s for (dv, _), s in sent.items() if dv == dev and s < at)

    dets = {}
    for line in open(d / "server.log", encoding="utf-8", errors="replace"):
        m = LINE.match(re.sub(r"\x1b\[[0-9;]*m", "", line).strip())
        dev = members.get(m.group(2)) if m else None
        if dev:
            at = epoch(m.group(1))
            dets.setdefault(dev, []).append((at, last_receive(dev, at)))

    out = []
    for t in trials:
        dev = next(k for k in stops if k.endswith(f"-{t['id']:02d}"))
        pred, stop = t["pred"][str(T)], stops[dev]
        ds = sorted(dets.get(dev, []))
        fp = sum(1 for at, _ in ds if at < stop)
        true = [(at, last) for at, last in ds if at >= stop]
        masked = not true
        seen = stop_d = None
        last_ok = True
        if true:
            at, last = true[0]
            seen, stop_d = at - last, at - stop
            # 서버가 본 마지막 수신이 모형이 예측한 마지막 성공 전송과 같은 전송인지
            last_ok = abs(last - sent[(dev, pred["last_rx"])]) < 1.0
        in_band = seen is None or (T < seen <= T + 5 + SLACK)
        ok = fp == pred["fp"] and masked == pred["masked"] and in_band and last_ok
        out.append({"id": t["id"], "fp": fp, "fp_pred": pred["fp"], "masked": masked,
                    "masked_pred": pred["masked"], "seen": seen, "stop": stop_d, "ok": ok})
    return out


def main(d):
    d = Path(d)
    trials = json.load(open(d / "seqs.json"))["trials"]
    print(f"재생 실험 대조 — 시행 {len(trials)}회/조건 (수치 실험과 같은 시퀀스)\n")
    print("| 임계값 | 시행 | 오탐 모형 | 오탐 서버 | 가림 모형 | 가림 서버 | 시행별 일치 | 지연(마지막 수신 기준) | 지연(중단 기준, 평균) |")
    print("|---|---|---|---|---|---|---|---|---|")
    misses = []
    for T in (5, 10, 20, 40):
        if not (d / f"T{T}").exists():
            continue
        rows = condition(d / f"T{T}", T, trials)
        seen = [r["seen"] for r in rows if r["seen"] is not None]
        stop = [r["stop"] for r in rows if r["stop"] is not None]
        band = f"{min(seen):.1f}~{max(seen):.1f}초" if seen else "—"
        avg = f"{statistics.mean(stop):.1f}초" if stop else "—"
        print(f"| {T}초 | {len(rows)} | {sum(r['fp_pred'] for r in rows)}건 | {sum(r['fp'] for r in rows)}건 "
              f"| {sum(r['masked_pred'] for r in rows)}회 | {sum(r['masked'] for r in rows)}회 "
              f"| {sum(r['ok'] for r in rows)}/{len(rows)} | {band} | {avg} |")
        misses += [(T, r) for r in rows if not r["ok"]]
    if misses:
        print("\n불일치 시행")
        for T, r in misses:
            print(f"  T={T}초 시행 {r['id']}: {r}")
    else:
        print("\n모든 시행에서 오탐 건수·가림 여부·지연 구간이 모형과 일치했다.")


if __name__ == "__main__":
    main(sys.argv[1])
