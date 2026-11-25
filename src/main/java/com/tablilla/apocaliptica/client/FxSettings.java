package com.tablilla.apocaliptica.client;

/** Ajustes de dibujo de los efectos (se cambian en el juego con /tablillafx para probar la compatibilidad con shaders). */
public final class FxSettings {
    /** Etapa del render donde se dibuja: 0 = despues de particulas, 1 = despues del clima, 2 = despues de bloques translucidos, 3 = despues de entidades. */
    public static int stage = 0;
    /** Tipo de render de la luz: 0 = lightning (aditivo), 1 = gui (translucido normal), 2 = debug quads. */
    public static int glowType = 0;
    /** Sin niebla de distancia al dibujar los efectos. */
    public static boolean noFog = true;

    private FxSettings() {}
}
