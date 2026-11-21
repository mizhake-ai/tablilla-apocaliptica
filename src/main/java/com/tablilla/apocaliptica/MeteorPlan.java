package com.tablilla.apocaliptica;

import java.util.Random;

/** Plan de la lluvia de meteoros. Servidor y cliente lo calculan igual a partir de la misma semilla. */
public final class MeteorPlan {
    public static final int COUNT = 36;
    public static final double HORIZ = 150.0;
    public static final double HEIGHT = 300.0;

    public final double[] ox = new double[COUNT];
    public final double[] oz = new double[COUNT];
    public final double[] dirX = new double[COUNT];
    public final double[] dirZ = new double[COUNT];
    public final double[] rad = new double[COUNT];
    public final int[] crater = new int[COUNT];
    public final int[] start = new int[COUNT];
    public final int[] flight = new int[COUNT];

    public MeteorPlan(long seed, float scale) {
        Random r = new Random(seed);
        double az = r.nextDouble() * Math.PI * 2;
        double area = 85.0 * scale;
        for (int i = 0; i < COUNT; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(r.nextDouble()) * area;
            ox[i] = Math.cos(a) * d;
            oz[i] = Math.sin(a) * d;
            double az2 = az + r.nextGaussian() * 0.12;
            dirX[i] = Math.cos(az2);
            dirZ[i] = Math.sin(az2);
            start[i] = 40 + i * 7 + r.nextInt(6);
            flight[i] = 45 + r.nextInt(25);
            boolean big = i % 12 == 11;
            rad[i] = (2.6 + r.nextDouble() * 2.4) * (big ? 1.7 : 1.0) * Math.max(0.6, scale);
            crater[i] = (int) Math.round((6 + r.nextInt(6)) * (big ? 2.0 : 1.0) * scale);
        }
    }

    public int impactTick(int i) {
        return start[i] + flight[i];
    }
}
