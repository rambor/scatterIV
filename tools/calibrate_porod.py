#!/usr/bin/env python3
"""
Fit a Porod-volume correction from the CSV written by version4.Calibration.PorodCalibration.

    ln(V_true / V_p) = quadratic polynomial in features of the fitted P(r) (--features):
      A: 1/(qmax Rg), ln(dmax/Rg)
      B: 1/(qmax r_peak), ln(r_peak/dmax)
      C: 1/(qmax Rg), ln(dmax/Rg), ln(r_peak/Rg)
      D: 1/(qmax Rg), 1/(qmax r_peak), ln(dmax/Rg), ln(<r>/Rg)         (default)
dmax/Rg alone is a weak shape measure (a long rod saturates at sqrt(12) = 3.46); the P(r) peak
position, which sits near the smallest dimension for rods and discs, carries the thickness.
On 600 synthetic shapes (test set) the median error went 19% -> 6.4% (A), 4.8% (B), 4.3% (C), 3.7% (D).

Rg, r_peak and <r> are the IFT's and dmax the dmax used for the fit, so the correction uses only
quantities available from an experimental P(r). Each dmax/Rg bin gets equal total weight, so the many compact
domains do not decide the elongated end. Structures (not rows) are split 80/20 into fit / test sets.

usage: python3 calibrate_porod.py porod_calibration.csv [--method Moore-L2] [--target ses|vdw|mw]
                                  [--exact] [--kind all|pdb|synthetic] [--plot fig.png]
needs numpy; matplotlib only for --plot
"""
import argparse, csv, hashlib, itertools, math, sys
import numpy as np

BINS = [0, 2.6, 2.8, 3.0, 3.2, 3.4, 1e9]          # dmax/Rg edges

L = math.log
FEATURES = {   # name -> (Java expressions, python function of qmax, rg, dmax, r_peak, r_avg)
    "A": (["1.0/(qmax*rg)", "Math.log(dmax/rg)"],
          lambda q, rg, d, pk, ra: (1/(q*rg), L(d/rg))),
    "B": (["1.0/(qmax*rPeak)", "Math.log(rPeak/dmax)"],
          lambda q, rg, d, pk, ra: (1/(q*pk), L(pk/d))),
    "C": (["1.0/(qmax*rg)", "Math.log(dmax/rg)", "Math.log(rPeak/rg)"],
          lambda q, rg, d, pk, ra: (1/(q*rg), L(d/rg), L(pk/rg))),
    "D": (["1.0/(qmax*rg)", "1.0/(qmax*rPeak)", "Math.log(dmax/rg)", "Math.log(rAvg/rg)"],
          lambda q, rg, d, pk, ra: (1/(q*rg), 1/(q*pk), L(d/rg), L(ra/rg))),
}


def load(path, a):
    X, Y, D, ids, kinds, qs = [], [], [], [], [], []
    with open(path, newline="") as fh:
        for r in csv.DictReader(fh):
            if r["method"] != a.method or r["solved"] != "1":
                continue
            if a.kind != "all" and r["kind"] != a.kind:
                continue
            try:
                vp = float(r["vp_exact_trunc"] if a.exact else r["vp_fit"])
                rg = float(r["rg_true"] if a.exact else r["rg_fit"])
                dmax = float(r["dmax_true"])
                qmax = float(r["qmax"])
                qnom = round(float(r.get("qmax_nominal") or qmax), 3)
                if a.target == "ses":
                    vt = float(r["v_ses"])
                elif a.target == "vdw":
                    vt = float(r["v_vdw"])
                else:
                    vt = float(r["mw_da"])*1.212        # 0.73 cm^3/g partial specific volume, A^3 per Da
            except (ValueError, KeyError):
                continue
            if not (vp > 0 and rg > 0 and vt > 0):
                continue
            pk = float(r["r_peak_fit"]) if r.get("r_peak_fit") else float("nan")
            ra = float(r["r_avg_fit"]) if r.get("r_avg_fit") else float("nan")
            if a.exact:
                pk = float(r["r_peak_true"]) if r.get("r_peak_true") else pk
            feats = FEATURES[a.features][1](qmax, rg, dmax, pk, ra)
            if any(not math.isfinite(x) for x in feats):
                continue
            X.append(feats)
            Y.append(math.log(vt/vp))
            D.append(dmax/rg)
            ids.append(r["id"]); kinds.append(r["kind"]); qs.append(qnom)
    return np.array(X), np.array(Y), np.array(D), np.array(ids), np.array(kinds), np.array(qs)


def terms(k):
    """index tuples of the quadratic polynomial in k features: (), (i,), (i,j) with i <= j"""
    t = [()]
    t += [(i,) for i in range(k)]
    t += list(itertools.combinations_with_replacement(range(k), 2))
    return t


def design(X):
    return np.column_stack([np.prod(X[:, list(t)], axis=1) if t else np.ones(len(X)) for t in terms(X.shape[1])])


def bin_of(d):
    return np.digitize(d, BINS) - 1


def report(name, D, err):
    print(f"\n{name}: |V_corr/V_true - 1| by dmax/Rg bin")
    print(f"  {'dmax/Rg':>12} {'n':>6} {'median':>8} {'90th pct':>9}")
    b = bin_of(D)
    for k in range(len(BINS) - 1):
        m = b == k
        if m.sum() == 0:
            continue
        e = np.abs(err[m])
        print(f"  {BINS[k]:5.1f}-{BINS[k+1] if BINS[k+1] < 1e8 else float('inf'):<6.1f} {m.sum():6d} "
              f"{100*np.median(e):7.1f}% {100*np.percentile(e, 90):8.1f}%")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("csv")
    p.add_argument("--method", default="Moore-L2")
    p.add_argument("--target", choices=["ses", "vdw", "mw"], default="ses")
    p.add_argument("--exact", action="store_true", help="fit V_p from the noise-free I(q) (truncation only)")
    p.add_argument("--kind", choices=["all", "pdb", "synthetic"], default="all")
    p.add_argument("--features", choices=sorted(FEATURES), default="D")
    p.add_argument("--plot")
    a = p.parse_args()

    X, Y, D, ids, kinds, qs = load(a.csv, a)
    if len(Y) < 20:
        sys.exit(f"only {len(Y)} usable rows for method {a.method}")
    test = np.array([int(hashlib.md5(i.encode()).hexdigest(), 16) % 5 == 0 for i in ids])
    print(f"{len(Y)} rows ({len(set(ids))} structures), method {a.method}, target {a.target}"
          f"{', noise-free V_p' if a.exact else ''}, features {a.features}; {test.sum()} test rows")

    # equal total weight per dmax/Rg bin (fit set)
    b = bin_of(D)
    w = np.zeros(len(Y))
    for k in range(len(BINS) - 1):
        m = (b == k) & ~test
        if m.any():
            w[m] = 1.0/m.sum()
    A = design(X)
    fit = ~test
    sw = np.sqrt(w[fit])
    coef, *_ = np.linalg.lstsq(A[fit]*sw[:, None], Y[fit]*sw, rcond=None)

    pred = A @ coef
    before = np.exp(-Y) - 1                 # V_p/V_true - 1
    after = np.exp(pred - Y) - 1            # V_p*corr/V_true - 1
    report("uncorrected, test set", D[test], before[test])
    report("corrected, test set", D[test], after[test])

    print("\nby qmax (test set, median |error| uncorrected -> corrected)")
    for q in sorted(set(qs)):
        m = test & (qs == q)
        if m.any():
            print(f"  qmax {q:.2f}: {100*np.median(np.abs(before[m])):5.1f}% -> {100*np.median(np.abs(after[m])):5.1f}%  (n={m.sum()})")

    exprs = FEATURES[a.features][0]
    T = terms(len(exprs))
    print("\ncoefficients:")
    for t, c in zip(T, coef):
        print(f"  {'1' if not t else ' * '.join(f'f{i}' for i in t):10s} {c: .6e}")
    body = " + ".join(f"({c:.8e})" + "".join(f"*f[{i}]" for i in t) for t, c in zip(T, coef))
    print("\nJava (IndirectFT):\n"
          "    /** empirical Porod correction (tools/calibrate_porod.py, method " + a.method + ", features " + a.features + "):\n"
          "     *  V = V_p * porodCorrection(...); rg, rPeak, rAvg from the fitted P(r), dmax as used for the fit */\n"
          "    public static double porodCorrection(double qmax, double rg, double dmax, double rPeak, double rAvg) {\n"
          "        double[] f = {" + ", ".join(exprs) + "};\n"
          "        return Math.exp(" + body + ");\n"
          "    }")
    print("\nCheck the corrected errors by bin before adopting it, and look at the worst test cases:")
    worst = np.argsort(-np.abs(after*test))[:8]
    for i in worst:
        if test[i]:
            print(f"  {ids[i]:36s} qmax {qs[i]:.2f} dmax/Rg {D[i]:5.2f}  error {100*after[i]:+6.1f}%")

    if a.plot:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        fig, ax = plt.subplots(1, 2, figsize=(11, 4.2), sharey=True)
        for axis, e, t in ((ax[0], before, "uncorrected"), (ax[1], after, "corrected")):
            sc = axis.scatter(D[test], 100*e[test], c=qs[test], s=8, cmap="viridis")
            axis.axhline(0, color="k", lw=0.8)
            axis.set_xlabel("dmax / Rg"); axis.set_title(t)
        ax[0].set_ylabel("V_p / V_true - 1 (%)")
        fig.colorbar(sc, ax=ax, label="qmax")
        fig.savefig(a.plot, dpi=130, bbox_inches="tight")
        print(f"\nplot: {a.plot}")


if __name__ == "__main__":
    main()
