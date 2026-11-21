package com.tablilla.apocaliptica.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * Un efecto visual de un ataque. Las coordenadas de dibujo son locales: (0,0,0) es el punto de impacto
 * en la superficie, +Y es hacia arriba.
 */
public interface FxEffect {
    int duration();

    /** Se llama una vez por tick del cliente (t empieza en 1): particulas y sonidos. */
    void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float scale, long seed);

    /** Geometria opaca/oscura. */
    default void renderSolid(VertexConsumer vc, Matrix4f m, float t, long seed, float scale) {}

    /** Geometria luminosa (aditiva). */
    default void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float scale) {}

    /** Temblor de camara en grados. */
    default float shake(float t) {
        return 0.0F;
    }

    /** Destello blanco de pantalla, 0 a 1. */
    default float flash(float t) {
        return 0.0F;
    }

    /** Capa de color sobre la pantalla (ARGB). Alfa 0 = nada. */
    default int tint(float t) {
        return 0;
    }
}
