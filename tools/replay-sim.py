"""
유실 시퀀스 재생기 — 수치 실험(docs/paper-sim.py)이 만든 유실 시퀀스를 실제 서버에 그대로 재생한다.

device-sim.sh 는 유실을 $RANDOM 으로 그때그때 정하고 1초 루프라 전송 간격이 5~6초로 흔들린다.
그래서 "모형이 예측한 결과와 서버 결과가 시행마다 같은가" 를 따질 수 없었다.
이 도구는 시퀀스를 입력받아 시작 시각 + i×5초의 절대 시각에 맞춰 보내고,
단말 여러 대를 동시에 띄워 한 번에 여러 시행을 돌린다.

  setup : 실험용 기관을 새로 만들고 단말 N대를 피보호자로 등록한다.
          기관 계정은 매번 무작위로 만들고 어디에도 저장하지 않는다 (등록 직후 쓸모가 없다).
  run   : 시퀀스 파일의 시행 i 를 단말 <prefix>-i 로 재생한다. 전송 기록을 CSV 로 남긴다.

실행 (보통은 tools/replay-run.sh 가 부른다)
  python3 tools/replay-sim.py setup --prefix RPL-T20 --count 30
  python3 tools/replay-sim.py run   --prefix RPL-T20 --seqs seqs.json --out sends.csv
"""
import argparse, csv, json, secrets, threading, time, urllib.request, urllib.error

BASE = "http://localhost:8080"


def post(path, body, token=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="POST",
                                 headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def mac(prefix, i):
    return f"{prefix}-{i:02d}"


def setup(args):
    org_id, password = "replay-" + secrets.token_hex(4), secrets.token_urlsafe(16)
    code, body = post("/api/org/signup", {"orgId": org_id, "password": password, "name": "재생 실험"})
    if code != 200:
        raise SystemExit(f"기관 가입 실패 {code}: {body}")
    code, body = post("/api/org/login", {"orgId": org_id, "password": password})
    if code != 200:
        raise SystemExit(f"기관 로그인 실패 {code}: {body}")
    token = json.loads(body)["token"]
    ids = {}
    for i in range(1, args.count + 1):
        code, body = post("/api/targets", {"name": f"재생{i:02d}", "contact": f"0100000{i:04d}",
                                           "deviceMac": mac(args.prefix, i)}, token)
        if code != 200:
            raise SystemExit(f"{mac(args.prefix, i)} 등록 실패 {code}: {body}")
        ids[body.rsplit(":", 1)[1].strip()] = mac(args.prefix, i)   # "피보호자 등록 완료. ID: 12"
    # 서버의 끊김 로그는 memberId 만 남긴다(기기 식별자는 일부러 뺐다). 대조하려면 이 대응표가 필요하다.
    with open(args.members, "w") as f:
        json.dump(ids, f, indent=1)
    print(f"단말 {args.count}대 등록: {mac(args.prefix, 1)} ~ {mac(args.prefix, args.count)}")


def run(args):
    with open(args.seqs) as f:
        spec = json.load(f)
    period, trials = spec["period"], spec["trials"][: args.count]
    t0 = time.time() + 3
    rows, lock = [], threading.Lock()

    # 단말마다 시작 위상을 period 안에서 고르게 벌린다. 서버 확인 주기에 대한 위상이
    # 시행마다 달라져 수치 실험의 "확인 위상 U[0,5)" 와 같은 조건이 된다.
    offsets = {t["id"]: (t["id"] - 1) * period / len(trials) for t in trials}

    def device(trial):
        dev, start = mac(args.prefix, trial["id"]), t0 + offsets[trial["id"]]
        for j, lost in enumerate(trial["losses"]):
            target = start + j * period
            time.sleep(max(0.0, target - time.time()))
            if lost == "1":
                code, sent = "LOST", time.time()
            else:
                sent = time.time()
                code, _ = post("/api/device/data", {
                    "device_id": dev, "report_type": "HEARTBEAT",
                    "sensor_health": {"vibrator": "OK", "radar": "OK", "thermal": "OK"},
                    "device": {"battery_pct": 85, "rssi": -55}})
            with lock:
                rows.append({"trial": trial["id"], "device": dev, "tx": j, "scheduled": f"{target:.3f}",
                             "sent": f"{sent:.3f}", "result": code})
        with lock:  # 31번째 전송 예정 시각 = 실제 중단 시각 (paper-sim 의 STOP)
            rows.append({"trial": trial["id"], "device": dev, "tx": len(trial["losses"]),
                         "scheduled": f"{start + len(trial['losses']) * period:.3f}", "sent": "",
                         "result": "STOP"})

    threads = [threading.Thread(target=device, args=(t,)) for t in trials]
    print(f"재생 시작: 단말 {len(trials)}대, 시행당 {spec['n_tx']}회 전송 후 침묵")
    for th in threads:
        th.start()
    for th in threads:
        th.join()

    rows.sort(key=lambda r: (r["trial"], r["tx"]))
    with open(args.out, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    bad = [r for r in rows if r["result"] not in ("LOST", "STOP", 200)]
    print(f"재생 종료: 전송 기록 {args.out}" + (f"  ** 거부 {len(bad)}건 **" if bad else ""))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("setup"); s.add_argument("--prefix", required=True); s.add_argument("--count", type=int, default=30)
    s.add_argument("--members", required=True, help="memberId → 단말 대응표를 저장할 경로")
    r = sub.add_parser("run"); r.add_argument("--prefix", required=True); r.add_argument("--seqs", required=True)
    r.add_argument("--out", required=True); r.add_argument("--count", type=int, default=30)
    a = ap.parse_args()
    setup(a) if a.cmd == "setup" else run(a)
