package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TablillaMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        CHANNEL.registerMessage(0, FxPacket.class, FxPacket::encode, FxPacket::decode, FxPacket::handle);
    }

    public static void sendFx(Laser laser, BlockPos pos, long seed, float scale) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new FxPacket(laser.ordinal(), pos, seed, scale));
    }

    public static void sendStop() {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new FxPacket(-1, BlockPos.ZERO, 0L, 1.0F));
    }
}
