package com.tablilla.apocaliptica;

import java.util.Random;

/** Plan de la lluvia de meteoros: 72 meteoros y, al final, uno del tamano de una colina. Servidor y cliente lo calculan igual. */
public final class MeteorPlan {
    public static final int RAIN = 72;
    public static final int COUNT = RAIN + 1; // el ultimo es el meteoro de la extincion
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
    public final boolean[] big = new boolean[COUNT];

    public MeteorPlan(long seed, float scale) {
        Random r = new Random(seed);
        double az = r.nextDouble() * Math.PI * 2;
        double area = 150.0 * scale;
        for (int i = 0; i < RAIN; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(r.nextDouble()) * area;
            ox[i] = Math.cos(a) * d;
            oz[i] = Math.sin(a) * d;
            double az2 = az + r.nextGaussian() * 0.12;
            dirX[i] = Math.cos(az2);
            dirZ[i] = Math.sin(az2);
            start[i] = 40 + i * 5 + r.nextInt(5);
            flight[i] = 45 + r.nextInt(25);
            big[i] = i % 12 == 11;
            rad[i] = (2.6 + r.nextDouble() * 2.4) * (big[i] ? 1.8 : 1.0) * Math.max(0.6, scale);
            crater[i] = (int) Math.round((8 + r.nextInt(8)) * (big[i] ? 2.1 : 1.0) * scale);
        }
        int g = COUNT - 1;
        ox[g] = 0.0;
        oz[g] = 0.0;
        dirX[g] = Math.cos(az);
        dirZ[g] = Math.sin(az);
        start[g] = 440;
        flight[g] = 110;
        big[g] = true;
        rad[g] = 22.0 * Math.max(0.6, scale);
        crater[g] = (int) Math.round(62 * scale);
    }

    public boolean giant(int i) {
        return i == COUNT - 1;
    }

    public int impactTick(int i) {
        return start[i] + flight[i];
    }
}
