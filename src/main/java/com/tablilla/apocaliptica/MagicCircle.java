package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Geometria del circulo magico del Juicio Divino. La usan el servidor (para colocar bloques de oro y diamante
 * donde se juntan las lineas) y el cliente (para dibujar las lineas de luz). Todo en unidades de RADIUS.
 */
public final class MagicCircle {
    public static final double RADIUS = 110.0;
    public static final double HEX_R = 0.94;       // hexagono exterior (circulo blanco)
    public static final double SMALL_R = 0.12;     // circulos pequeños en los vertices del hexagono
    public static final double TEXT_IN = 0.74;     // anillo de texto
    public static final double TEXT_OUT = 0.80;
    public static final double STAR_R = 0.60;      // hexagrama interior
    public static final double RING_R = 0.655;     // anillo del hexagrama
    public static final double POINT_R = 0.52;     // 3 puntos
    public static final double POINT_SIZE = 0.05;
    public static final double ANG0 = Math.PI / 2.0;

    private MagicCircle() {}

    public static double hexAngle(int k) {
        return ANG0 + k * Math.PI / 3.0;
    }

    public static double pointAngle(int k) {
        return ANG0 + k * Math.PI * 2.0 / 3.0 + Math.PI / 3.0;
    }

    /** Puntos donde se juntan las lineas: {x, z, tipo}. tipo 1 = diamante, 0 = oro. */
    public static List<double[]> junctions(double radius) {
        List<double[]> out = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            double a = hexAngle(k);
            out.add(new double[] {Math.cos(a) * HEX_R * radius, Math.sin(a) * HEX_R * radius, 0.0});
            double b = hexAngle(k) + Math.PI / 6.0;
            out.add(new double[] {Math.cos(b) * STAR_R * radius, Math.sin(b) * STAR_R * radius, 0.0});
        }
        for (int k = 0; k < 3; k++) {
            double a = pointAngle(k);
            out.add(new double[] {Math.cos(a) * POINT_R * radius, Math.sin(a) * POINT_R * radius, 1.0});
        }
        return out;
    }

    // ------------------------------------------------------------------ lineas de bloques

    private static void addPoint(Map<Long, double[]> out, double x, double z, boolean wide) {
        int ix = (int) Math.round(x);
        int iz = (int) Math.round(z);
        for (int dx = 0; dx <= (wide ? 1 : 0); dx++) {
            for (int dz = 0; dz <= (wide ? 1 : 0); dz++) {
                long key = ((long) (ix + dx) << 32) ^ ((iz + dz) & 0xFFFFFFFFL);
                out.putIfAbsent(key, new double[] {ix + dx, iz + dz, 0.0});
            }
        }
    }

    private static void addLine(Map<Long, double[]> out, double x0, double z0, double x1, double z1, boolean wide) {
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
        int n = Math.max(1, (int) Math.ceil(len / 0.8));
        for (int i = 0; i <= n; i++) {
            double k = (double) i / n;
            addPoint(out, x0 + (x1 - x0) * k, z0 + (z1 - z0) * k, wide);
        }
    }

    private static void addCircle(Map<Long, double[]> out, double cx, double cz, double r, boolean wide) {
        int n = Math.max(24, (int) Math.ceil(Math.PI * 2 * r / 0.8));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            addPoint(out, cx + Math.cos(a) * r, cz + Math.sin(a) * r, wide);
        }
    }

    /**
     * Todos los bloques de las lineas del circulo magico, en el orden en que se "dibujan": {x, z, diamante}.
     * Donde se cruzan las lineas hay buena probabilidad de bloques de diamante; en el resto, muy poca.
     */
    public static List<double[]> blockLines(double radius, long seed) {
        Map<Long, double[]> out = new LinkedHashMap<>();
        addCircle(out, 0, 0, radius, true);
        double hexR = HEX_R * radius;
        for (int k = 0; k < 6; k++) {
            double a0 = hexAngle(k);
            double a1 = hexAngle(k + 1);
            addLine(out, Math.cos(a0) * hexR, Math.sin(a0) * hexR, Math.cos(a1) * hexR, Math.sin(a1) * hexR, false);
            addCircle(out, Math.cos(a0) * hexR, Math.sin(a0) * hexR, SMALL_R * radius, false);
        }
        addCircle(out, 0, 0, TEXT_IN * radius, false);
        addCircle(out, 0, 0, TEXT_OUT * radius, false);
        addCircle(out, 0, 0, RING_R * radius, true);
        double starR = STAR_R * radius;
        for (int k = 0; k < 6; k++) {
            double a0 = hexAngle(k) + Math.PI / 6.0;
            double a1 = hexAngle(k + 2) + Math.PI / 6.0;
            addLine(out, Math.cos(a0) * starR, Math.sin(a0) * starR, Math.cos(a1) * starR, Math.sin(a1) * starR, false);
        }
        for (int k = 0; k < 3; k++) {
            double a = pointAngle(k);
            addCircle(out, Math.cos(a) * POINT_R * radius, Math.sin(a) * POINT_R * radius, POINT_SIZE * radius, false);
        }
        // diamantes: casi todos donde se cruzan las lineas, muy pocos en el resto
        Random r = new Random(seed);
        List<double[]> junc = junctions(radius);
        for (double[] p : out.values()) {
            double chance = 0.012;
            for (double[] j : junc) {
                double dx = p[0] - j[0];
                double dz = p[1] - j[1];
                if (dx * dx + dz * dz <= 6.0) {
                    chance = 0.5;
                    break;
                }
            }
            if (r.nextDouble() < chance) p[2] = 1.0;
        }
        return new ArrayList<>(out.values());
    }
}
