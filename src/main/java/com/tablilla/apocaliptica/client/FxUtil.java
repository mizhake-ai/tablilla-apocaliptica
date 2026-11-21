package com.tablilla.apocaliptica.client;

import com.tablilla.apocaliptica.ModSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Particulas visibles de lejos y sonidos locales. */
public final class FxUtil {
    public static final double TAU = Math.PI * 2.0;

    private FxUtil() {}

    public static void p(ClientLevel level, ParticleOptions type, double x, double y, double z,
                         double vx, double vy, double vz) {
        level.addAlwaysVisibleParticle(type, x, y, z, vx, vy, vz);
    }

    public static void snd(ClientLevel level, String name, double x, double y, double z, float volume, float pitch) {
        SoundEvent e = ModSounds.get(name);
        if (e != null) level.playLocalSound(x, y, z, e, SoundSource.AMBIENT, volume, pitch, false);
    }
}
