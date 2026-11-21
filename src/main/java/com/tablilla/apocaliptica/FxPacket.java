package com.tablilla.apocaliptica;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Avisa a los clientes: empieza (o se detiene) el efecto visual de un ataque. laser = -1 significa "detener todo". */
public class FxPacket {
    public final int laser;
    public final BlockPos pos;
    public final long seed;
    public final float scale;

    public FxPacket(int laser, BlockPos pos, long seed, float scale) {
        this.laser = laser;
        this.pos = pos;
        this.seed = seed;
        this.scale = scale;
    }

    public static void encode(FxPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.laser + 1);
        buf.writeBlockPos(p.pos);
        buf.writeLong(p.seed);
        buf.writeFloat(p.scale);
    }

    public static FxPacket decode(FriendlyByteBuf buf) {
        int laser = buf.readVarInt() - 1;
        BlockPos pos = buf.readBlockPos();
        long seed = buf.readLong();
        float scale = buf.readFloat();
        return new FxPacket(laser, pos, seed, scale);
    }

    public static void handle(FxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tablilla.apocaliptica.client.ClientFx.handle(p)));
        ctx.get().setPacketHandled(true);
    }
}
