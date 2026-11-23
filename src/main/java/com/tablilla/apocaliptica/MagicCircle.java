package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.List;

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
}
