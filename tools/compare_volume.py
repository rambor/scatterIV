#!/usr/bin/env python3
"""
Compare particle-volume (or mass) estimators on the CSV from version4.Calibration.PorodCalibration.

Estimators (each fitted on the 80% fit set, scored on the 20% test set, split by structure):
  Porod raw        V_p integrated to qmax, no correction (not fitted)
  Porod + corr     ln(V/V_p) = quadratic in features D (see calibrate_porod.py)
  Qr power law     ln V = a + b ln Q_R,             Q_R = V_c^2 / Rg, V_c integrated to qmax
  Qr + qmax        ln V = a + b ln Q_R + c u + d u^2,  u = 1/(qmax Rg)
  Qr(inf) + qmax   as above with V_c from the model extrapolated to infinite q
  Qr + shape       ln V = a + b ln Q_R + quadratic in features D
  Porod + Qr       ln V = a + b ln V_p + c ln Q_R + quadratic in features D

usage: python3 compare_volume.py porod.csv [--method GNOM-like] [--target ses|mw] [--train all|pdb|synthetic]
"""
import argparse, csv, hashlib, itertools, math, sys
import numpy as np

L = math.log


def quad(F):
    cols = [np.ones(len(F))]
    k = F.shape[1]
    cols += [F[:, i] for i in range(k)]
    cols += [F[:, i]*F[:, j] for i, j in itertools.combinations_with_replacement(range(k), 2)]
    return np.column_stack(cols)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("csv")
    p.add_argument("--method", default="GNOM-like")
    p.add_argument("--target", choices=["ses", "mw"], default="ses")
    p.add_argument("--train", choices=["all", "pdb", "synthetic"], default="all")
    a = p.parse_args()

    rows = []
    with open(a.csv, newline="") as fh:
        for r in csv.DictReader(fh):
            if r["method"] != a.method or r["solved"] != "1" or not r.get("vc_fit_trunc"):
                continue
            try:
                q = float(r["qmax"]); rg = float(r["rg_fit"]); d = float(r["dmax_true"])
                pk = float(r["r_peak_fit"]); ra = float(r["r_avg_fit"]); vp = float(r["vp_fit"])
                vcT = float(r["vc_fit_trunc"]); vcI = float(r["vc_fit_inf"])
                vt = float(r["v_ses"]) if a.target == "ses" else float(r["mw_da"])
            except (ValueError, KeyError):
                continue
            vals = (q, rg, d, pk, ra, vp, vcT, vcI, vt)
            if not all(math.isfinite(x) and x > 0 for x in vals):
                continue
            rows.append((r["id"], r["kind"], round(float(r.get("qmax_nominal") or q), 3)) + vals)
    if len(rows) < 50:
        sys.exit(f"only {len(rows)} usable rows (the CSV needs the vc_* columns: rerun the harness)")

    ids = np.array([x[0] for x in rows]); kind = np.array([x[1] for x in rows]); qn = np.array([x[2] for x in rows])
    q, rg, d, pk, ra, vp, vcT, vcI, vt = (np.array([x[i] for x in rows]) for i in range(3, 12))
    y = np.log(vt)
    test = np.array([int(hashlib.md5(i.encode()).hexdigest(), 16) % 5 == 0 for i in ids])
    train = ~test & ((kind == a.train) if a.train != "all" else True)

    # equal weight per dmax/Rg bin and per kind, so neither the compact domains nor one shape family dominate
    dr = d/rg
    bins = np.digitize(dr, [2.6, 2.8, 3.0, 3.2, 3.4])
    w = np.zeros(len(y))
    for kk in set(kind):
        for b in range(6):
            m = train & (bins == b) & (kind == kk)
            if m.any():
                w[m] = 1.0/m.sum()

    u = 1/(q*rg)
    featD = np.column_stack([u, 1/(q*pk), np.log(dr), np.log(ra/rg)])
    qrT, qrI = vcT**2/rg, vcI**2/rg
    designs = {
        "Porod + corr": (np.column_stack([quad(featD)]), np.log(vp)),          # offset ln V_p
        "Qr power law": (np.column_stack([np.ones_like(y), np.log(qrT)]), 0),
        "Qr + qmax": (np.column_stack([np.ones_like(y), np.log(qrT), u, u*u]), 0),
        "Qr(inf) + qmax": (np.column_stack([np.ones_like(y), np.log(qrI), u, u*u]), 0),
        "Qr + shape": (np.column_stack([np.log(qrT), quad(featD)]), 0),
        "Porod + Qr": (np.column_stack([np.log(vp), np.log(qrT), quad(featD)]), 0),
    }
    preds = {"Porod raw": np.log(vp) + (0 if a.target == "ses" else -np.median(np.log(vp[train]) - y[train]))}
    coefs = {}
    for name, (A, off) in designs.items():
        sw = np.sqrt(w[train])
        c, *_ = np.linalg.lstsq(A[train]*sw[:, None], (y - off)[train]*sw, rcond=None)
        preds[name] = A @ c + off
        coefs[name] = c

    def stats(name, m):
        e = np.exp(preds[name][m] - y[m]) - 1
        return f"{100*np.median(np.abs(e)):5.1f} {100*np.percentile(np.abs(e), 90):5.1f} {100*np.median(e):+5.1f}"

    print(f"{len(set(ids))} structures, method {a.method}, target {a.target}, trained on {a.train}; "
          "test set: median |error| %, 90th pct %, median bias %\n")
    groups = [("CATH", test & (kind == "pdb")), ("synthetic", test & (kind == "synthetic"))]
    rgb = [(0, 15), (15, 20), (20, 25), (25, 1e9)]
    print(f"{'estimator':16s}" + "".join(f" | {g:^17s}" for g, m in groups if m.any())
          + "".join(f" | CATH Rg {lo:.0f}-{hi if hi < 1e8 else 'inf'}".ljust(20) for lo, hi in rgb))
    for name in preds:
        line = f"{name:16s}"
        for g, m in groups:
            if m.any():
                line += f" | {stats(name, m)}"
        for lo, hi in rgb:
            m = test & (kind == "pdb") & (rg >= lo) & (rg < hi)
            line += f" | {stats(name, m):17s}" if m.sum() > 10 else f" | {'-':17s}"
        print(line)

    print("\nby qmax, CATH test set (median |error| %):")
    print(f"{'estimator':16s}" + "".join(f" {x:>6.2f}" for x in sorted(set(qn))))
    for name in preds:
        print(f"{name:16s}" + "".join(
            f" {100*np.median(np.abs(np.exp(preds[name][m] - y[m]) - 1)):6.1f}"
            for x in sorted(set(qn)) for m in [test & (kind == 'pdb') & (qn == x)] if m.any()))

    c = coefs["Qr power law"]
    print(f"\nQr power law: V = {math.exp(c[0]):.4g} * Q_R^{c[1]:.4f}   (Q_R = V_c^2/Rg, V_c to qmax)")
    c = coefs["Qr + qmax"]
    print(f"Qr + qmax:    ln V = {c[0]:.4f} + {c[1]:.4f} ln Q_R + {c[2]:.4f} u + {c[3]:.4f} u^2,  u = 1/(qmax Rg)")


if __name__ == "__main__":
    main()
