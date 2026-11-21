package com.tablilla.apocaliptica;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Ayudas para efectos: particulas visibles de lejos y sonidos fuertes. */
public final class Fx {
    private Fx() {}

    public static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z,
                                 int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static void sound(ServerLevel level, double x, double y, double z, SoundEvent sound,
                             float volume, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.AMBIENT, volume, pitch);
    }
}
