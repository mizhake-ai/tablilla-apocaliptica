package com.tablilla.apocaliptica.client;

import com.tablilla.apocaliptica.ModItems;
import com.tablilla.apocaliptica.TablillaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TablillaMod.MOD_ID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    public static boolean holdsTablet(Player player) {
        return player.getMainHandItem().is(ModItems.TABLILLA.get())
                || player.getOffhandItem().is(ModItems.TABLILLA.get());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (ClientModEvents.WHEEL_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null && holdsTablet(mc.player)) {
                mc.setScreen(new LaserWheelScreen());
            }
        }
    }
}
