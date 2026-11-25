package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Plan de La Sima: una zona irregular (no un circulo perfecto) dividida en losas que se hunden una tras otra. Servidor y cliente lo calculan igual. */
public final class SinkholePlan {
    public static final int CELL = 10;
    public static final int START = 140;
    public static final int SPAN = 560;

    public final int count;
    public final int[] px;
    public final int[] pz;
    public final int[] tick;
    public final double radius;

    public SinkholePlan(long seed, float scale) {
        Random r = new Random(seed ^ 0xA5A5L);
        this.radius = 112.0 * scale;
        double p1 = r.nextDouble() * 6.283;
        double p2 = r.nextDouble() * 6.283;
        double p3 = r.nextDouble() * 6.283;
        double psi = r.nextDouble() * 6.283;
        int range = (int) Math.ceil(radius * 1.3 / CELL) + 1;
        List<int[]> cells = new ArrayList<>();
        for (int gx = -range; gx <= range; gx++) {
            for (int gz = -range; gz <= range; gz++) {
                double x = gx * CELL + CELL * 0.5;
                double z = gz * CELL + CELL * 0.5;
                double th = Math.atan2(z, x);
                double rt = radius * (1.0 + 0.16 * Math.sin(3 * th + p1) + 0.10 * Math.sin(5 * th + p2) + 0.06 * Math.sin(8 * th + p3));
                if (Math.sqrt(x * x + z * z) > rt) continue;
                double u = ((x * Math.cos(psi) + z * Math.sin(psi)) / (radius * 1.3) + 1.0) * 0.5;
                u = Math.max(0.0, Math.min(1.0, u));
                int tk = START + (int) (SPAN * (0.6 * u + 0.4 * r.nextDouble()));
                cells.add(new int[] {(int) Math.round(x), (int) Math.round(z), tk});
            }
        }
        cells.sort((a, b) -> Integer.compare(a[2], b[2]));
        count = cells.size();
        px = new int[count];
        pz = new int[count];
        tick = new int[count];
        for (int i = 0; i < count; i++) {
            px[i] = cells.get(i)[0];
            pz[i] = cells.get(i)[1];
            tick[i] = cells.get(i)[2];
        }
    }
}
