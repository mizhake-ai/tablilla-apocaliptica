package com.tablilla.apocaliptica.client;

import com.tablilla.apocaliptica.TablillaMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TablillaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping WHEEL_KEY = new KeyMapping(
            "key.tablilla_apocaliptica.wheel", GLFW.GLFW_KEY_R, "key.categories.tablilla_apocaliptica");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(WHEEL_KEY);
    }
}
