package version4.Calibration;

import org.jfree.data.xy.XYSeries;
import version4.InverseTransform.DmaxSelector;
import version4.InverseTransform.IndirectFT;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/**
 * Porod-volume calibration harness.
 *
 * For every structure (PDB-format files in a directory, or streamed from a .tar / .tar.gz / .tgz
 * archive such as the CATH domain set, without unpacking), and optionally synthetic shapes:
 *  1. solvent-excluded volume V_ses on a fine grid (atoms at van der Waals radii, rolled probe);
 *  2. the SES body as a uniform-density particle, coarse-grained into weighted cells, and its
 *     noise-free I(q) from the pair-distance histogram;
 *  3. for each qmax: noisy data -> Scatter's IFT methods at the true dmax -> V_p from the P(r)
 *     model integrated to qmax, plus V_p from the noise-free I(q) truncated at the same qmax
 *     (separates the truncation bias from the IFT bias);
 *  4. one CSV row per structure x qmax x method (resumable: ids already in the CSV are skipped).
 *
 * Fit the correction afterwards with tools/calibrate_porod.py.
 *
 * Usage (classpath = Scatter's compiled classes + lib/*):
 *   java -cp out:lib/* version4.Calibration.PorodCalibration <dir|archive> [options]
 *     --out FILE         CSV output (default porod_calibration.csv)
 *     --threads N        worker threads (default: cores - 1)
 *     --qmax LIST        comma list (default 0.20,0.25,0.30,0.40,0.50)
 *     --methods LIST     DmaxSelector method names (default Moore-L2,GNOM-like)
 *     --noise X          relative error at low q (default 0.01)
 *     --background X     error floor as a fraction of I(0) (default 1e-3)
 *     --fine A           fine grid for the SES volume (default 1.0 Angstrom)
 *     --probe A          solvent probe radius (default 1.4)
 *     --max-cells N      coarse cells for the scattering calculation (default 12000; checked against an analytic sphere: truncated V_p within 1%)
 *     --every K          use every K-th structure (pilot runs; default 1)
 *     --limit N          stop after N structures
 *     --synthetic N      also run N synthetic shapes (ellipsoids, cylinders, discs, shells)
 *     --seed S           random seed (default 1)
 */
public class PorodCalibration {

    // ------------------------------------------------------------------ options
    static Path input;
    static String out = "porod_calibration.csv";
    static int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    static double[] qmaxList = {0.20, 0.25, 0.30, 0.40, 0.50};
    static String[] methodNames = {"Moore-L2", "GNOM-like"};
    static double noise = 0.01, background = 1e-3, fine = 1.0, probe = 1.4;
    static int maxCells = 12000, every = 1, limit = Integer.MAX_VALUE, synthetic = 0;
    static long seed = 1;
    /** synthetic shapes thinner than this half-thickness (a single helix, ~10 A across) are redrawn */
    static double MIN_HALF_THICKNESS = 5.0;

    static final String HEADER = "id,kind,n_atoms,n_res,mw_da,v_vdw,v_ses,rg_true,dmax_true,dmax_over_rg,"
            + "cell_A,r_peak_true,qmax_nominal,qmax,qmax_rg,method,solved,rg_fit,r_peak_fit,r_avg_fit,dmax_fit_over_rg_fit,i0_ratio,vp_exact_trunc,vp_fit,chi2,seconds,vc_exact_trunc,vc_fit_trunc,vc_fit_inf,qr_fit,mw_qr_fit";

    public static void main(String[] args) throws Exception {
        parse(args);
        Map<String, DmaxSelector.Method> all = DmaxSelector.defaultMethods();   // also sets the transforms' statics
        Map<String, DmaxSelector.Method> methods = new LinkedHashMap<>();
        for (String n : methodNames) {
            DmaxSelector.Method m = all.get(n);
            if (m == null) throw new IllegalArgumentException("unknown method " + n + "; known: " + all.keySet());
            methods.put(n, m);
        }

        Set<String> done = readDone(Paths.get(out));
        boolean fresh = !Files.exists(Paths.get(out)) || Files.size(Paths.get(out)) == 0;
        PrintWriter w = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(out, true), StandardCharsets.UTF_8)));
        if (fresh) { w.println(HEADER); w.flush(); }

        ExecutorService pool = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(threads*4), new ThreadPoolExecutor.CallerRunsPolicy());
        AtomicInteger submitted = new AtomicInteger(), finished = new AtomicInteger(), failed = new AtomicInteger();
        long t0 = System.currentTimeMillis();

        java.util.function.BiConsumer<String, Body> submit = (id, body) -> {
            submitted.incrementAndGet();
            pool.execute(() -> {
                long s = System.nanoTime();
                try {
                    List<String> rows = run(id, body, methods, s);
                    synchronized (w) { for (String r : rows) w.println(r); w.flush(); }
                } catch (Throwable e) {
                    failed.incrementAndGet();
                    System.err.println("FAILED " + id + ": " + e);
                }
                int f = finished.incrementAndGet();
                if (f % 50 == 0) {
                    double el = (System.currentTimeMillis() - t0)/1000.0;
                    System.err.printf("%d done (%d failed), %.1f s each on average, %.0f s elapsed%n", f, failed.get(), el*threads/f, el);
                }
            });
        };

        if (input != null) {
            int[] counter = {0};
            Visitor v = (name, bytes) -> {
                int k = counter[0]++;
                if (k % every != 0) return true;
                if (submitted.get() >= limit) return false;
                String id = idOf(name);
                if (done.contains(id)) return true;
                Body b = Body.fromPdb(bytes);
                if (b == null) return true;            // no protein atoms
                submit.accept(id, b);
                return true;
            };
            walk(input, v);
        }
        Random r = new Random(seed);
        for (int i = 0; i < synthetic; i++) {
            Body b = Body.synthetic(r, i);
            if (!done.contains(b.name)) submit.accept(b.name, b);
        }

        pool.shutdown();
        pool.awaitTermination(365, TimeUnit.DAYS);
        w.close();
        System.err.printf("finished: %d structures, %d failed, %.0f s%n", finished.get(), failed.get(),
                (System.currentTimeMillis() - t0)/1000.0);
    }

    // ------------------------------------------------------------------ one structure
    static List<String> run(String id, Body body, Map<String, DmaxSelector.Method> methods, long startNs) {
        Model m = new Model(body);
        double qTop = 0;
        for (double q : qmaxList) qTop = Math.max(qTop, q);

        // q grid: typical SAXS spacing, starting low enough for Guinier (q Rg <= 0.5)
        double qmin = Math.min(0.008, 0.5/m.rg), dq = 0.0015;
        int nq = (int) Math.ceil((qTop - qmin)/dq) + 1;
        double[] q = new double[nq], iq = new double[nq];
        for (int i = 0; i < nq; i++) { q[i] = qmin + i*dq; iq[i] = m.intensity(q[i]); }
        double i0 = m.intensity(0);

        Random rnd = new Random(seed*1_000_003L + id.hashCode());
        double[] noisy = new double[nq], sig = new double[nq];
        for (int i = 0; i < nq; i++) {
            sig[i] = noise*(iq[i] + background*i0);
            noisy[i] = iq[i] + sig[i]*rnd.nextGaussian();
        }

        List<String> rows = new ArrayList<>();
        for (double qmax : qmaxList) {
            int n = 0;
            while (n < nq && q[n] <= qmax + 1e-12) n++;
            if (n < 20) continue;
            double vpExact = m.porodVolumeExact(qmax);
            double vcExact = m.vcExact(qmax);
            XYSeries data = new XYSeries("qIq", false, false), err = new XYSeries("err", false, false);
            for (int i = 0; i < n; i++) { data.add(q[i], q[i]*noisy[i]); err.add(q[i], q[i]*sig[i]); }
            double qm = q[n - 1];

            for (Map.Entry<String, DmaxSelector.Method> e : methods.entrySet()) {
                long s = System.nanoTime();
                String solved = "0";
                double rgFit = Double.NaN, i0r = Double.NaN, vp = Double.NaN, chi = Double.NaN, peak = Double.NaN, rav = Double.NaN;
                double vcT = Double.NaN, vcI = Double.NaN, qr = Double.NaN, mwqr = Double.NaN;
                try {
                    IndirectFT ft = e.getValue().fit(data, err, m.dmax, qm, 1.0);
                    if (ft != null && ft.isSolved()) {
                        solved = "1";
                        rgFit = ft.getRg();
                        i0r = ft.getIZero()/i0;
                        vp = ft.getPorodVolume(qm).value;
                        chi = ft.getChiEstimate();
                        peak = peakOf(ft, m.dmax);
                        rav = ft.getRAverage();
                        vcT = vcTruncated(ft, m.dmax, qm);
                        vcI = ft.getVolumeOfCorrelation().value;
                        qr = ft.getQR().value;
                        mwqr = ft.getMolecularWeightQR().value;
                    }
                } catch (RuntimeException ex) {
                    solved = "0";
                }
                double secs = (System.nanoTime() - s)/1e9 + ((rows.isEmpty()) ? (s - startNs)/1e9 : 0);
                rows.add(String.join(",", id, body.kind, Integer.toString(body.nAtoms), Integer.toString(body.nRes),
                        f(body.mw), f(body.vVdw), f(body.vSes), f(m.rg), f(m.dmax), f(m.dmax/m.rg),
                        f(m.cell), f(m.rPeak), f(qmax), f(qm), f(qm*m.rg), e.getKey(), solved, f(rgFit), f(peak), f(rav), f(m.dmax/rgFit), f(i0r),
                        f(vpExact), f(vp), f(chi), String.format(Locale.ROOT, "%.3f", secs),
                        f(vcExact), f(vcT), f(vcI), f(qr), f(mwqr)));
            }
        }
        return rows;
    }

    /**
     * V_c = I(0) / int_0^L q I(q) dq of the fitted model, from its P(r):
     * I(0) = 4 pi int P dr and int_0^L q I dq = 4 pi int P(r) (1 - cos Lr) / r^2 dr (Simpson)
     */
    static double vcTruncated(IndirectFT ft, double dmax, double L) {
        int n = 2000;
        double h = dmax/n, a = 0, b = 0;
        for (int i = 0; i <= n; i++) {
            double r = i*h, wgt = (i == 0 || i == n) ? 1 : ((i & 1) == 1 ? 4 : 2), p;
            if (r <= 0 || r >= dmax) continue;
            try { p = ft.calculatePofRAtR(r, 1.0); } catch (RuntimeException e) { p = 0; }
            a += wgt*p;
            b += wgt*p*(1 - Math.cos(L*r))/(r*r);
        }
        return a/b;
    }

    /** r at the maximum of the fitted P(r) */
    static double peakOf(IndirectFT ft, double dmax) {
        double best = -Double.MAX_VALUE, at = Double.NaN;
        for (int i = 1; i < 400; i++) {
            double r = dmax*i/400.0, p;
            try { p = ft.calculatePofRAtR(r, 1.0); } catch (RuntimeException e) { continue; }
            if (p > best) { best = p; at = r; }
        }
        return at;
    }

    static String f(double v) { return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.6g", v); }

    // ------------------------------------------------------------------ fine-grid body
    /** occupancy of the particle on a fine cubic grid, plus composition */
    static final class Body {
        String name = "", kind = "pdb";
        int nAtoms, nRes;
        double mw = Double.NaN, vVdw = Double.NaN, vSes;
        double h;                    // fine grid spacing
        double ox, oy, oz;           // origin of voxel (0,0,0)
        int nx, ny, nz;
        BitSet in;                   // particle voxels

        int idx(int i, int j, int k) { return (i*ny + j)*nz + k; }

        static final Map<String, Double> RES_MASS = new HashMap<>();
        static {
            String[] t = {"ALA 71.08", "ARG 156.19", "ASN 114.10", "ASP 115.09", "CYS 103.14", "GLN 128.13", "GLU 129.12",
                    "GLY 57.05", "HIS 137.14", "ILE 113.16", "LEU 113.16", "LYS 128.17", "MET 131.19", "PHE 147.18",
                    "PRO 97.12", "SER 87.08", "THR 101.10", "TRP 186.21", "TYR 163.18", "VAL 99.13", "MSE 178.09",
                    "SEC 150.04", "PYL 237.30", "HSD 137.14", "HSE 137.14", "HIE 137.14", "HID 137.14", "CYX 103.14"};
            for (String s : t) { String[] p = s.split(" "); RES_MASS.put(p[0], Double.parseDouble(p[1])); }
        }

        static double vdw(String el) {
            switch (el) {
                case "C": return 1.70;
                case "N": return 1.55;
                case "O": return 1.52;
                case "S": return 1.80;
                case "SE": return 1.90;
                case "P": return 1.80;
                default: return 1.80;
            }
        }

        /** protein heavy atoms of the first model; null if none */
        static Body fromPdb(byte[] bytes) {
            List<double[]> xyz = new ArrayList<>();
            List<Double> rad = new ArrayList<>();
            Set<String> residues = new HashSet<>();
            double mass = 0;
            boolean unknownResidue = false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.ISO_8859_1))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("ENDMDL")) break;
                    if (!line.startsWith("ATOM")) continue;
                    if (line.length() < 54) continue;
                    char alt = line.charAt(16);
                    if (alt != ' ' && alt != 'A' && alt != '1') continue;
                    String resName = line.substring(17, 20).trim();
                    String el = (line.length() >= 78) ? line.substring(76, 78).trim().toUpperCase(Locale.ROOT) : "";
                    if (el.isEmpty()) {
                        String an = line.substring(12, 16).trim();
                        el = an.replaceAll("[^A-Za-z]", "");
                        el = el.isEmpty() ? "C" : el.substring(0, 1).toUpperCase(Locale.ROOT);
                    }
                    if (el.equals("H") || el.equals("D")) continue;
                    double x, y, z;
                    try {
                        x = Double.parseDouble(line.substring(30, 38).trim());
                        y = Double.parseDouble(line.substring(38, 46).trim());
                        z = Double.parseDouble(line.substring(46, 54).trim());
                    } catch (NumberFormatException e) { continue; }
                    xyz.add(new double[]{x, y, z});
                    rad.add(vdw(el));
                    String key = line.substring(21, 27);       // chain + resSeq + iCode
                    if (residues.add(key + resName)) {
                        Double m = RES_MASS.get(resName);
                        if (m == null) unknownResidue = true; else mass += m;
                    }
                }
            } catch (IOException e) {
                return null;
            }
            if (xyz.size() < 20) return null;
            Body b = new Body();
            b.nAtoms = xyz.size();
            b.nRes = residues.size();
            b.mw = unknownResidue ? Double.NaN : mass + 18.015;   // NaN when a non-standard residue is present
            b.h = fine;
            b.rasterize(xyz, rad);
            return b;
        }

        /** SES: union of (r_vdw + probe) spheres, eroded by the probe; also the vdW volume */
        void rasterize(List<double[]> xyz, List<Double> rad) {
            double pad = 1.80 + probe + 2*h;
            double minx = 1e9, miny = 1e9, minz = 1e9, maxx = -1e9, maxy = -1e9, maxz = -1e9;
            for (double[] p : xyz) {
                minx = Math.min(minx, p[0]); maxx = Math.max(maxx, p[0]);
                miny = Math.min(miny, p[1]); maxy = Math.max(maxy, p[1]);
                minz = Math.min(minz, p[2]); maxz = Math.max(maxz, p[2]);
            }
            ox = minx - pad; oy = miny - pad; oz = minz - pad;
            nx = (int) Math.ceil((maxx - minx + 2*pad)/h) + 1;
            ny = (int) Math.ceil((maxy - miny + 2*pad)/h) + 1;
            nz = (int) Math.ceil((maxz - minz + 2*pad)/h) + 1;
            BitSet dil = new BitSet(nx*ny*nz), vdwSet = new BitSet(nx*ny*nz);
            for (int a = 0; a < xyz.size(); a++) {
                double[] p = xyz.get(a);
                stamp(dil, p[0], p[1], p[2], rad.get(a) + probe, true);
                stamp(vdwSet, p[0], p[1], p[2], rad.get(a), true);
            }
            // erosion: every outside voxel touching the dilated body removes the probe sphere around it
            in = (BitSet) dil.clone();
            for (int i = 1; i < nx - 1; i++) for (int j = 1; j < ny - 1; j++) for (int k = 1; k < nz - 1; k++) {
                int id = idx(i, j, k);
                if (dil.get(id)) continue;
                if (dil.get(idx(i + 1, j, k)) || dil.get(idx(i - 1, j, k)) || dil.get(idx(i, j + 1, k))
                        || dil.get(idx(i, j - 1, k)) || dil.get(idx(i, j, k + 1)) || dil.get(idx(i, j, k - 1))) {
                    stamp(in, ox + i*h, oy + j*h, oz + k*h, probe, false);
                }
            }
            in.or(vdwSet);                                   // never smaller than the vdW body
            vSes = in.cardinality()*h*h*h;
            vVdw = vdwSet.cardinality()*h*h*h;
        }

        void stamp(BitSet s, double x, double y, double z, double r, boolean value) {
            int i0 = (int) Math.floor((x - r - ox)/h), i1 = (int) Math.ceil((x + r - ox)/h);
            int j0 = (int) Math.floor((y - r - oy)/h), j1 = (int) Math.ceil((y + r - oy)/h);
            int k0 = (int) Math.floor((z - r - oz)/h), k1 = (int) Math.ceil((z + r - oz)/h);
            double r2 = r*r;
            for (int i = Math.max(0, i0); i <= Math.min(nx - 1, i1); i++) {
                double dx = ox + i*h - x;
                for (int j = Math.max(0, j0); j <= Math.min(ny - 1, j1); j++) {
                    double dy = oy + j*h - y, dxy = dx*dx + dy*dy;
                    if (dxy > r2) continue;
                    for (int k = Math.max(0, k0); k <= Math.min(nz - 1, k1); k++) {
                        double dz = oz + k*h - z;
                        if (dxy + dz*dz <= r2) s.set(idx(i, j, k), value);
                    }
                }
            }
        }

        /** shapes with known volume spanning compact to very anisometric (dmax/Rg 2.6 to >10) */
        static Body synthetic(Random r, int i) {
            Body b = new Body();
            b.kind = "synthetic";
            double vol = Math.exp(Math.log(2e4) + r.nextDouble()*Math.log(4e5/2e4));   // 20,000 - 400,000 A^3
            String type = new String[]{"prolate", "oblate", "cylinder", "disc", "shell", "dumbbell"}[i % 6];
            double asp = Math.exp(r.nextDouble()*Math.log(12));                          // 1 - 12, limited below
            double res = Math.max(1.0, Math.cbrt(vol)/40);                               // keep the grid manageable
            b.h = res;
            final double a, c, rIn;
            switch (type) {
                case "prolate": a = Math.cbrt(3*vol/(4*Math.PI*asp)); c = asp*a; rIn = 0; break;
                case "oblate":  c = Math.cbrt(3*vol/(4*Math.PI*asp*asp)); a = asp*c; rIn = 0; break;
                case "cylinder": a = Math.cbrt(vol/(2*Math.PI*asp)); c = asp*a; rIn = 0; break;          // c = half length
                case "disc":    c = Math.cbrt(vol/(2*Math.PI*asp*asp)); a = asp*c; rIn = 0; break;        // a = radius
                case "shell": { double ro = Math.cbrt(3*vol/(4*Math.PI*(1 - Math.pow(0.4 + 0.4*r.nextDouble(), 3))));
                    a = ro; c = ro; rIn = ro*Math.cbrt(1 - 3*vol/(4*Math.PI*ro*ro*ro)); break; }
                default: { // two spheres joined by a thin rod
                    double rs = Math.cbrt(3*vol/(8*Math.PI)); a = rs; c = rs*(1.5 + 3*r.nextDouble()); rIn = 0.3*rs; }
            }
            if (Math.min(a, c) < MIN_HALF_THICKNESS && (type.equals("prolate") || type.equals("oblate") || type.equals("cylinder") || type.equals("disc"))) {
                return synthetic(r, i);                                  // thinner than a single helix: redraw
            }
            b.name = String.format(Locale.ROOT, "syn_%s_%04d_v%.0f_asp%.2f", type, i, vol, asp);
            double ext = Math.max(a, c) + 3*res;
            b.ox = b.oy = b.oz = -ext;
            b.nx = b.ny = b.nz = (int) Math.ceil(2*ext/res) + 1;
            b.in = new BitSet(b.nx*b.ny*b.nz);
            for (int ii = 0; ii < b.nx; ii++) for (int jj = 0; jj < b.ny; jj++) for (int kk = 0; kk < b.nz; kk++) {
                double x = b.ox + ii*res, y = b.oy + jj*res, z = b.oz + kk*res;
                boolean inside;
                double rho = Math.sqrt(x*x + y*y);
                switch (type) {
                    case "prolate": case "oblate": inside = (rho*rho)/(a*a) + z*z/(c*c) <= 1; break;
                    case "cylinder": case "disc": inside = rho <= a && Math.abs(z) <= c; break;
                    case "shell": { double d = Math.sqrt(x*x + y*y + z*z); inside = d <= a && d >= rIn; break; }
                    default: {
                        double d1 = Math.sqrt(rho*rho + (z - c)*(z - c)), d2 = Math.sqrt(rho*rho + (z + c)*(z + c));
                        inside = d1 <= a || d2 <= a || (rho <= rIn && Math.abs(z) <= c);
                    }
                }
                if (inside) b.in.set(b.idx(ii, jj, kk));
            }
            b.vSes = b.in.cardinality()*res*res*res;
            return b;
        }
    }

    // ------------------------------------------------------------------ uniform-density scattering model
    /**
     * The body coarse-grained into cells of side `cell`; each cell is a uniform sphere of the cell's
     * filled volume, placed at the centroid of its filled fine voxels. I(q) from the weighted
     * pair-distance histogram (Debye), normalized to I(0) = 1000.
     */
    static final class Model {
        final double cell, rg, dmax, rPeak;
        final double dr = 0.05;
        final double[] hist;          // weighted pair counts per distance bin (i != j)
        final double self;            // sum of w_i^2 (i == j)
        final double cellRadius, norm;

        Model(Body b) {
            // cell size: multiple of the fine grid, enough to keep the cell count under maxCells
            double v = b.in.cardinality()*b.h*b.h*b.h;
            int k = Math.max(2, (int) Math.ceil(Math.cbrt(v/maxCells)/b.h));
            if (k*b.h < 1.5) k = (int) Math.ceil(1.5/b.h);
            cell = k*b.h;
            int cx = (b.nx + k - 1)/k, cy = (b.ny + k - 1)/k, cz = (b.nz + k - 1)/k;
            int nc = cx*cy*cz;
            double[] sx = new double[nc], sy = new double[nc], sz = new double[nc];
            int[] cnt = new int[nc];
            for (int i = b.in.nextSetBit(0); i >= 0; i = b.in.nextSetBit(i + 1)) {
                int kk = i % b.nz, jj = (i/b.nz) % b.ny, ii = i/(b.nz*b.ny);
                int c = ((ii/k)*cy + jj/k)*cz + kk/k;
                sx[c] += b.ox + ii*b.h; sy[c] += b.oy + jj*b.h; sz[c] += b.oz + kk*b.h; cnt[c]++;
            }
            int m = 0;
            for (int c = 0; c < nc; c++) if (cnt[c] > 0) m++;
            double[] x = new double[m], y = new double[m], z = new double[m], w = new double[m];
            int t = 0;
            double full = (double) k*k*k;
            for (int c = 0; c < nc; c++) if (cnt[c] > 0) {
                x[t] = sx[c]/cnt[c]; y[t] = sy[c]/cnt[c]; z[t] = sz[c]/cnt[c]; w[t] = cnt[c]/full; t++;
            }
            cellRadius = cell*Math.cbrt(3/(4*Math.PI));

            double maxD = 0;
            double s2 = 0;
            for (int i = 0; i < m; i++) s2 += w[i]*w[i];
            // first pass for dmax of centroids
            for (int i = 0; i < m; i++) for (int j = i + 1; j < m; j++) {
                double dx = x[i] - x[j], dy = y[i] - y[j], dz = z[i] - z[j];
                double d2 = dx*dx + dy*dy + dz*dz;
                if (d2 > maxD) maxD = d2;
            }
            maxD = Math.sqrt(maxD);
            hist = new double[(int) (maxD/dr) + 2];
            for (int i = 0; i < m; i++) for (int j = i + 1; j < m; j++) {
                double dx = x[i] - x[j], dy = y[i] - y[j], dz = z[i] - z[j];
                hist[(int) (Math.sqrt(dx*dx + dy*dy + dz*dz)/dr + 0.5)] += 2*w[i]*w[j];
            }
            self = s2;
            double sw = 0;
            for (double wi : w) sw += wi;
            norm = 1000.0/(sw*sw);                                   // I(0) = 1000

            // Rg from the pair distribution (point cells + uniform-sphere self term 3/5 a^2 per cell)
            double num = 0, den = 0;
            for (int bi = 0; bi < hist.length; bi++) { double r = bi*dr; num += hist[bi]*r*r; den += hist[bi]; }
            den += self;
            double rg2 = 0.5*num/den + 0.6*cellRadius*cellRadius;
            rg = Math.sqrt(rg2);
            dmax = maxD + cell;                                     // centroids sit inside the surface cells
            // P(r) peak of the model: histogram smoothed over one cell
            int win = Math.max(1, (int) Math.round(cell/dr));
            double bestP = -1, at = 0;
            for (int bi = 0; bi < hist.length; bi++) {
                double sm = 0;
                for (int j = Math.max(0, bi - win); j <= Math.min(hist.length - 1, bi + win); j++) sm += hist[j];
                if (sm > bestP) { bestP = sm; at = bi*dr; }
            }
            rPeak = at;
        }

        double intensity(double q) {
            double f = 1;
            if (q > 0) { double u = q*cellRadius; f = 3*(Math.sin(u) - u*Math.cos(u))/(u*u*u); }
            double s = self;
            if (q == 0) { for (double h : hist) s += h; }
            else for (int bi = 1; bi < hist.length; bi++) {
                if (hist[bi] == 0) continue;
                double x = q*bi*dr;
                s += hist[bi]*Math.sin(x)/x;
            }
            return norm*f*f*s;
        }

        /** V_c = I(0) / int_0^L q I(q) dq of the noise-free model (Simpson) */
        double vcExact(double L) {
            int n = Math.max(200, (int) Math.ceil(L/0.002));
            if ((n & 1) == 1) n++;
            double h = L/n, sum = 0;
            for (int i = 0; i <= n; i++) {
                double q = i*h, wgt = (i == 0 || i == n) ? 1 : ((i & 1) == 1 ? 4 : 2);
                sum += wgt*q*intensity(q);
            }
            return intensity(0)/(sum*h/3);
        }

        /** 2 pi^2 I(0) / int_0^L q^2 I(q) dq of the noise-free model (Simpson) */
        double porodVolumeExact(double L) {
            int n = Math.max(200, (int) Math.ceil(L/0.002));
            if ((n & 1) == 1) n++;
            double h = L/n, sum = 0;
            for (int i = 0; i <= n; i++) {
                double q = i*h, wgt = (i == 0 || i == n) ? 1 : ((i & 1) == 1 ? 4 : 2);
                sum += wgt*q*q*intensity(q);
            }
            return 2*Math.PI*Math.PI*intensity(0)/(sum*h/3);
        }
    }

    // ------------------------------------------------------------------ input walking
    interface Visitor { boolean accept(String name, byte[] bytes) throws IOException; }

    static void walk(Path p, Visitor v) throws IOException {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isDirectory(p)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                List<Path> list = new ArrayList<>();
                for (Path c : ds) list.add(c);
                Collections.sort(list);
                for (Path c : list) { if (!walkOne(c, v)) return; }
            }
            return;
        }
        walkOne(p, v);
    }

    static boolean walkOne(Path p, Visitor v) throws IOException {
        if (Files.isDirectory(p)) { walk(p, v); return true; }
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz")) {
            try (InputStream raw = new BufferedInputStream(Files.newInputStream(p), 1 << 16)) {
                InputStream in = n.endsWith(".tar") ? raw : new GZIPInputStream(raw, 1 << 16);
                return readTar(in, v);
            }
        }
        byte[] b = Files.readAllBytes(p);
        if (n.endsWith(".gz")) b = gunzip(b);
        return v.accept(p.getFileName().toString(), b);
    }

    /** minimal ustar / GNU tar reader (regular files only; long names via GNU 'L' entries) */
    static boolean readTar(InputStream in, Visitor v) throws IOException {
        byte[] hdr = new byte[512];
        String longName = null;
        while (true) {
            if (!readFully(in, hdr)) return true;
            boolean zero = true;
            for (byte x : hdr) if (x != 0) { zero = false; break; }
            if (zero) return true;
            String name = cstr(hdr, 0, 100);
            String prefix = cstr(hdr, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            long size = octal(hdr, 124, 12);
            char type = (char) hdr[156];
            long padded = (size + 511)/512*512;
            if (type == 'L') {
                byte[] b = new byte[(int) padded];
                readFully(in, b);
                longName = cstr(b, 0, (int) size);
                continue;
            }
            if (longName != null) { name = longName; longName = null; }
            if ((type == '0' || type == '\0') && size > 0 && size < (1L << 30)) {
                byte[] b = new byte[(int) size];
                readFully(in, b);
                skip(in, padded - size);
                if (name.toLowerCase(Locale.ROOT).endsWith(".gz")) b = gunzip(b);
                if (!v.accept(name, b)) return false;
            } else {
                skip(in, padded);
            }
        }
    }

    static boolean readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int r = in.read(b, off, b.length - off);
            if (r < 0) { if (off == 0) return false; throw new EOFException("truncated tar"); }
            off += r;
        }
        return true;
    }

    static void skip(InputStream in, long n) throws IOException {
        byte[] buf = new byte[8192];
        while (n > 0) { int r = in.read(buf, 0, (int) Math.min(buf.length, n)); if (r < 0) throw new EOFException(); n -= r; }
    }

    static String cstr(byte[] b, int off, int len) {
        int e = off;
        while (e < off + len && e < b.length && b[e] != 0) e++;
        return new String(b, off, e - off, StandardCharsets.ISO_8859_1).trim();
    }

    static long octal(byte[] b, int off, int len) {
        if ((b[off] & 0x80) != 0) {                         // GNU base-256 size
            long v = 0;
            for (int i = off + 1; i < off + len; i++) v = (v << 8) | (b[i] & 0xff);
            return v;
        }
        String s = cstr(b, off, len);
        return s.isEmpty() ? 0 : Long.parseLong(s.trim(), 8);
    }

    static byte[] gunzip(byte[] b) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) {
            ByteArrayOutputStream o = new ByteArrayOutputStream(b.length*4);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            return o.toByteArray();
        }
    }

    static String idOf(String path) {
        String n = path.substring(path.lastIndexOf('/') + 1);
        for (String ext : new String[]{".gz", ".pdb", ".ent", ".cif"}) if (n.toLowerCase(Locale.ROOT).endsWith(ext)) n = n.substring(0, n.length() - ext.length());
        return n.replace(',', '_');
    }

    static Set<String> readDone(Path p) throws IOException {
        Set<String> s = new HashSet<>();
        if (!Files.exists(p)) return s;
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line = r.readLine();
            while ((line = r.readLine()) != null) {
                int c = line.indexOf(',');
                if (c > 0) s.add(line.substring(0, c));
            }
        }
        return s;
    }

    static void parse(String[] a) {
        for (int i = 0; i < a.length; i++) {
            String s = a[i];
            switch (s) {
                case "--out": out = a[++i]; break;
                case "--threads": threads = Integer.parseInt(a[++i]); break;
                case "--qmax": qmaxList = Arrays.stream(a[++i].split(",")).mapToDouble(Double::parseDouble).toArray(); break;
                case "--methods": methodNames = a[++i].split(","); break;
                case "--noise": noise = Double.parseDouble(a[++i]); break;
                case "--background": background = Double.parseDouble(a[++i]); break;
                case "--fine": fine = Double.parseDouble(a[++i]); break;
                case "--probe": probe = Double.parseDouble(a[++i]); break;
                case "--max-cells": maxCells = Integer.parseInt(a[++i]); break;
                case "--every": every = Integer.parseInt(a[++i]); break;
                case "--limit": limit = Integer.parseInt(a[++i]); break;
                case "--synthetic": synthetic = Integer.parseInt(a[++i]); break;
                case "--seed": seed = Long.parseLong(a[++i]); break;
                default:
                    if (s.startsWith("--")) throw new IllegalArgumentException("unknown option " + s);
                    input = Paths.get(s);
            }
        }
        if (input == null && synthetic == 0) {
            System.err.println("usage: PorodCalibration <dir|archive.tar.gz> [--synthetic N] [--out file.csv] [--threads N] ...");
            System.exit(1);
        }
    }
}
