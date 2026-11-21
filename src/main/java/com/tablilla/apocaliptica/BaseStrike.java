package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/** Base comun: lleva la cuenta de ticks y avisa a los clientes para los efectos visuales. */
public abstract class BaseStrike implements Strike {
    protected final ServerLevel level;
    protected final BlockPos center;   // bloque apuntado
    protected final RandomSource rnd;
    protected final long seed;
    protected final float scale;
    protected int t = 0;

    protected BaseStrike(ServerLevel level, BlockPos center) {
        this.level = level;
        this.center = center;
        this.rnd = level.getRandom();
        this.seed = this.rnd.nextLong();
        this.scale = (float) StrikeManager.scale;
    }

    protected abstract Laser laser();

    protected abstract int duration();

    protected abstract void serverTick();

    protected double cx() {
        return center.getX() + 0.5;
    }

    protected double cz() {
        return center.getZ() + 0.5;
    }

    /** Y de la superficie (la cara de arriba del bloque apuntado). */
    protected double cy() {
        return center.getY() + 1.0;
    }

    @Override
    public void start() {
        Net.sendFx(laser(), center.above(), seed, scale);
    }

    @Override
    public void cancel() {
        Net.sendStop();
    }

    @Override
    public boolean tick() {
        t++;
        serverTick();
        return t >= duration();
    }
}
