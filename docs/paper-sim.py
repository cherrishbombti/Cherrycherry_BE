"""
논문 4.3 절 수치 실험 — 동일 유실 시퀀스에서의 임계값 비교.

서버를 실행하지 않고, 3.4 에 서술한 판정 규칙만을 모형화해 수행한다.
  - 전송 주기 P = 5초, 생존 구간 150초 (30회 전송), 이후 완전 침묵
  - 각 전송은 확률 0.4 로 독립 유실
  - 서버는 5초 주기로 확인, 확인 시각의 위상은 U[0, 5) 에서 무작위
  - (현재 시각 - 마지막 수신 시각) > T 이고 아직 통지하지 않았으면 알림, 통지 표시
  - 수신이 재개되면 통지 표시를 되돌림  (= Member.offlineNotified)

실행: python3 docs/paper-sim.py
"""
import random, statistics
from fractions import Fraction as F

P, N_TX, LOSS, STOP, POLL = 5.0, 30, 0.4, 150.0, 5.0
TRIALS, SEED = 20000, 20260917


def simulate(losses, phase, T):
    """한 유실 시퀀스에 임계값 T 를 적용. (오탐 건수, 실단절 알림 시각, 그때의 마지막 수신 시각)"""
    recv = [i * P for i in range(N_TX) if not losses[i]]
    if not recv:
        return None
    ev = [(t, "rx") for t in recv]
    ev += [(phase + j * POLL, "tick") for j in range(int((260 - phase) // POLL) + 1)]
    ev.sort(key=lambda e: (e[0], 0 if e[1] == "rx" else 1))

    last, notified, fp, true_at, last_at = None, False, 0, None, None
    for t, kind in ev:
        if kind == "rx":
            last, notified = t, False
        elif last is not None and (t - last) > T and not notified:
            notified = True
            if t < STOP:
                fp += 1                      # 단말이 아직 살아 있는데 판정 → 오탐
            elif true_at is None:
                true_at, last_at = t, last   # 실제 단절에 대한 통지
    return fp, true_at, last_at


def analytic(n, k, p=F(2, 5)):
    """(지정 위치 k회 연속 누락 확률, 구간 내 1회 이상 확률, 극대 run 기대 개수)"""
    dp, hit = [F(0)] * k, F(0)
    dp[0] = F(1)
    for _ in range(n):
        nd = [F(0)] * k
        for j in range(k):
            if not dp[j]:
                continue
            nd[0] += dp[j] * (1 - p)
            if j + 1 == k:
                hit += dp[j] * p
            else:
                nd[j + 1] += dp[j] * p
        dp = nd
    return p**k, hit, p**k + (n - k) * (1 - p) * p**k


def main():
    random.seed(SEED)
    ks = (1, 2, 4, 8)
    res = {k: {"fp": [], "masked": [], "d_stop": [], "d_seen": []} for k in ks}
    n = 0
    for _ in range(TRIALS):
        losses = [random.random() < LOSS for _ in range(N_TX)]
        phase = random.uniform(0, POLL)
        out = {k: simulate(losses, phase, k * P) for k in ks}
        if any(v is None for v in out.values()):
            continue
        n += 1
        for k, (fp, ta, la) in out.items():
            res[k]["fp"].append(fp)
            res[k]["masked"].append(ta is None)
            if ta is not None:
                res[k]["d_stop"].append(ta - STOP)
                res[k]["d_seen"].append(ta - la)

    print(f"유실 시퀀스 {n}회 (P={P:.0f}s, 유실 {LOSS:.0%}, n={N_TX}, 확인주기 {POLL:.0f}s, seed={SEED})\n")
    print("표 3. 판정 모형 수치 실험")
    print(f"{'T':>5} {'평균 오탐':>9} {'이론 기대':>9} {'오탐 0건':>9} {'가림률':>8} {'이론 0.4^k':>11}")
    for k in ks:
        _, _, e = analytic(N_TX, k)
        r = res[k]
        print(f"{int(k*P):>4}s {statistics.mean(r['fp']):>9.2f} {float(e):>9.2f} "
              f"{r['fp'].count(0)/n:>9.1%} {sum(r['masked'])/n:>8.1%} {LOSS**k:>11.2%}")

    print("\n표 4. 감지 지연의 기준점")
    print(f"{'T':>5} {'마지막 수신 기준':>20} {'실제 중단 기준 평균':>20} {'실제 중단 기준 최대':>20}")
    for k in ks:
        ds, dn = res[k]["d_seen"], res[k]["d_stop"]
        print(f"{int(k*P):>4}s {min(ds):>8.1f} ~ {max(ds):<9.1f} "
              f"{statistics.mean(dn):>19.1f}s {max(dn):>19.1f}s")

    print("\n짝지은 비교 — 같은 시퀀스에서 임계값을 낮출 때 새로 가려지는 비율")
    for hi, lo in ((8, 4), (4, 2), (2, 1)):
        add = sum(1 for i in range(n) if res[lo]["masked"][i] and not res[hi]["masked"][i]) / n
        print(f"  {int(hi*P):>2}s → {int(lo*P):>2}s : {add:+.1%}p")


if __name__ == "__main__":
    main()
