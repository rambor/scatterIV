package version4.FormFactors;

import java.util.ArrayList;
import java.util.List;

/**
 * What the relative intensities of the lamellar orders say about the bilayer.
 *
 * <p>The positions of the orders give only the repeat distance. Their <i>intensities</i> are the bilayer's own
 * form factor sampled at q<sub>h</sub> = 2&pi;h/d, so they carry the internal structure of the bilayer: how far
 * apart the two headgroup layers sit, and how sharply defined they are. That matters here because the water layer
 * otherwise comes from subtracting a lipid length the user typed in; the orders measure the bilayer instead of
 * assuming it.
 *
 * <p>For a powder of stacks the measured intensity carries a Lorentz factor,
 * <pre>
 *   I_h  proportional to  |F(q_h)|^2 / q_h^2      so      |F(q_h)|  proportional to  q_h sqrt(I_h)
 * </pre>
 * and a symmetric bilayer with two Gaussian headgroup layers a distance d_HH apart has
 * <pre>
 *   F(q) = 2 cos(q d_HH / 2) exp(-q^2 sigma^2 / 2)
 * </pre>
 * The cosine puts zeros in the series: an order that is weak or missing is usually sitting on one, which is what
 * fixes d_HH. The Gaussian width damps the higher orders and is what stops the series going on for ever.
 *
 * <p>Two orders cannot determine both parameters, so a fit is only offered from three, and even then it is
 * reported as indicative: a full electron-density reconstruction needs the phases of F, which SAXS does not give
 * and which swelling series are used to recover.
 */
final class LnpLamellar {

    /** one order and what it implies */
    static final class Order {
        final String hkl;
        final double q, area, structureFactor;    // |F(q_h)|, on an arbitrary common scale
        Order(String hkl, double q, double area, double f) {
            this.hkl = hkl;
            this.q = q;
            this.area = area;
            this.structureFactor = f;
        }
    }

    static final class Profile {
        final List<Order> orders = new ArrayList<>();
        /** head-to-head distance across the bilayer, Angstrom; NaN when it could not be fitted */
        double headToHead = Double.NaN;
        /** width of a headgroup layer, Angstrom */
        double headWidth = Double.NaN;
        /** rms fractional residual of the fitted order ratios */
        double residual = Double.NaN;
        double repeat;
        /** every {head-to-head, headgroup width, misfit} that fits the orders; the ambiguity is real, not noise */
        final List<double[]> solutions = new ArrayList<>();
        final List<String> notes = new ArrayList<>();

        /** water between bilayers implied by the fitted bilayer rather than by an assumed lipid length */
        double waterLayer() { return Double.isNaN(headToHead) ? Double.NaN : repeat - headToHead; }
    }

    private LnpLamellar() { }

    /**
     * @param q    position of each observed order
     * @param area integrated intensity of each order
     * @param d    the lamellar repeat distance
     */
    static Profile analyse(double[] q, double[] area, double d) {
        Profile p = new Profile();
        p.repeat = d;
        double f0 = 0;
        for (int i = 0; i < q.length; i++) {
            if (!(q[i] > 0) || !(area[i] > 0)) continue;
            double f = q[i] * Math.sqrt(area[i]);          // Lorentz-corrected amplitude
            if (f0 == 0) f0 = f;
            int order = (int) Math.round(q[i] * d / (2 * Math.PI));
            p.orders.add(new Order(String.format("(00%d)", Math.max(order, 1)), q[i], area[i], f));
        }
        if (p.orders.size() < 2) {
            p.notes.add("Only one order is observed, so the intensities say nothing about the bilayer itself.");
            return p;
        }
        if (p.orders.size() < 3) {
            p.notes.add("Two orders cannot separate the headgroup separation from the headgroup width; a third "
                    + "order would fix both.");
            return p;
        }

        // Grid search over the two parameters, matching the ratios to the first order. Scattering gives |F| and
        // not its sign, and |cos(pi h t / d)| is unchanged when t is replaced by d - t, so a bilayer thickness and
        // the water gap that completes the repeat fit the orders equally well. That is the lamellar phase problem,
        // and no amount of fitting resolves it: every solution that fits is kept and the one nearest the expected
        // bilayer is marked, which is the choice a swelling series or SANS contrast variation would make properly.
        double obs0 = p.orders.get(0).structureFactor;
        double best = Double.POSITIVE_INFINITY;
        List<double[]> found = new ArrayList<>();
        for (double dhh = 0.08 * d; dhh <= 0.95 * d; dhh += 0.1) {
            for (double sigma = 1.0; sigma <= 9.0; sigma += 0.1) {
                double m0 = model(p.orders.get(0).q, dhh, sigma);
                if (Math.abs(m0) < 1e-6) continue;
                double sum = 0;
                int used = 0;
                for (int i = 1; i < p.orders.size(); i++) {
                    double obs = p.orders.get(i).structureFactor / obs0;
                    double mod = Math.abs(model(p.orders.get(i).q, dhh, sigma) / m0);
                    sum += (obs - mod) * (obs - mod);
                    used++;
                }
                double rms = Math.sqrt(sum / Math.max(1, used));
                best = Math.min(best, rms);
                found.add(new double[]{dhh, sigma, rms});
            }
        }
        // every distinct thickness that fits about as well as the best one
        double tolerance = Math.max(2 * best, 0.05);
        for (double[] f : found) {
            if (f[2] > tolerance) continue;
            boolean near = false;
            for (double[] k : p.solutions) if (Math.abs(k[0] - f[0]) < 3) { near = true; if (f[2] < k[2]) { k[0] = f[0]; k[1] = f[1]; k[2] = f[2]; } }
            if (!near) p.solutions.add(new double[]{f[0], f[1], f[2]});
        }
        p.solutions.sort((x, y) -> Double.compare(x[2], y[2]));
        while (p.solutions.size() > 4) p.solutions.remove(p.solutions.size() - 1);
        p.residual = best;
        if (!p.solutions.isEmpty()) {
            p.headToHead = p.solutions.get(0)[0];
            p.headWidth = p.solutions.get(0)[1];
        }
        if (best > 0.25)
            p.notes.add("The order intensities are not well described by a symmetric two-headgroup bilayer; treat "
                    + "any thickness below as a rough indication only.");
        return p;
    }

    /**
     * Picks the solution closest to an expected bilayer thickness, which is how the sign ambiguity is settled in
     * practice. Returns the chosen {thickness, width, misfit} or null when nothing was fitted.
     */
    static double[] choose(Profile p, double expectedThickness) {
        if (p.solutions.isEmpty()) return null;
        if (!(expectedThickness > 0)) return p.solutions.get(0);
        double[] best = null;
        for (double[] s : p.solutions)
            if (best == null || Math.abs(s[0] - expectedThickness) < Math.abs(best[0] - expectedThickness)) best = s;
        return best;
    }

    private static double model(double q, double dHH, double sigma) {
        return 2 * Math.cos(q * dHH / 2) * Math.exp(-q * q * sigma * sigma / 2);
    }

    static String describe(Profile p, double lipidLength) {
        StringBuilder sb = new StringBuilder("  WHAT THE ORDER INTENSITIES SAY ABOUT THE BILAYER\n");
        sb.append(String.format("    %-8s %10s %12s %14s%n", "hkl", "q (Å⁻¹)", "area", "|F| (rel.)"));
        double f0 = p.orders.isEmpty() ? 1 : p.orders.get(0).structureFactor;
        for (Order o : p.orders)
            sb.append(String.format("    %-8s %10.4f %12.4g %14.3f%n", o.hkl, o.q, o.area, o.structureFactor / f0));
        sb.append("    |F| is the Lorentz-corrected amplitude q·√I, the bilayer form factor sampled at each order.\n"
                + "    A weak order means q sits near a zero of F, not that the measurement is poor.\n");

        double[] chosen = choose(p, 2 * lipidLength);
        if (chosen != null) {
            sb.append(String.format("%n    Head-to-head distance   %6.1f Å   (%.2f nm)%n", chosen[0], chosen[0] / 10));
            sb.append(String.format("    Headgroup width σ       %6.1f Å%n", chosen[1]));
            sb.append(String.format("    Water between bilayers  %6.1f Å   (%.2f nm) = d − head-to-head%n",
                    p.repeat - chosen[0], (p.repeat - chosen[0]) / 10));
            sb.append(String.format("    rms misfit of the ratios %5.0f %%%n", 100 * chosen[2]));
            if (lipidLength > 0)
                sb.append(String.format("    Twice the lipid length entered is %.1f Å, which is what picked this "
                        + "solution.%n", 2 * lipidLength));
            if (p.solutions.size() > 1) {
                sb.append("\n    THE SIGN OF F IS NOT MEASURED, so other thicknesses fit the same intensities:\n");
                for (double[] s2 : p.solutions)
                    sb.append(String.format("      %6.1f Å  (water %5.1f Å)   misfit %3.0f %%%s%n",
                            s2[0], p.repeat - s2[0], 100 * s2[2], s2 == chosen ? "   <- chosen" : ""));
                sb.append("    A thickness t and the gap d − t give identical |F| at every order, so scattering\n"
                        + "    alone cannot separate bilayer from water. Settle it with a swelling series, with\n"
                        + "    SANS contrast variation, or with a composition you trust.\n");
            }
        }
        for (String n : p.notes) sb.append("    ! ").append(n).append('\n');
        return sb.toString();
    }
}
