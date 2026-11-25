package com.tablilla.apocaliptica.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.tablilla.apocaliptica.TablillaMod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Comando de cliente para probar como se ven los efectos con tu shader:
 * /tablillafx stage 0-3, /tablillafx glow 0-2, /tablillafx fog 0|1, /tablillafx info
 */
@Mod.EventBusSubscriber(modid = TablillaMod.MOD_ID, value = Dist.CLIENT)
public final class FxCommand {
    private FxCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("tablillafx")
                .then(Commands.literal("stage").then(Commands.argument("n", IntegerArgumentType.integer(0, 3)).executes(c -> {
                    FxSettings.stage = IntegerArgumentType.getInteger(c, "n");
                    c.getSource().sendSuccess(() -> Component.literal("Etapa de dibujo: " + FxSettings.stage), false);
                    return 1;
                })))
                .then(Commands.literal("glow").then(Commands.argument("n", IntegerArgumentType.integer(0, 2)).executes(c -> {
                    FxSettings.glowType = IntegerArgumentType.getInteger(c, "n");
                    c.getSource().sendSuccess(() -> Component.literal("Tipo de luz: " + FxSettings.glowType), false);
                    return 1;
                })))
                .then(Commands.literal("fog").then(Commands.argument("n", IntegerArgumentType.integer(0, 1)).executes(c -> {
                    FxSettings.noFog = IntegerArgumentType.getInteger(c, "n") == 1;
                    c.getSource().sendSuccess(() -> Component.literal("Sin niebla: " + FxSettings.noFog), false);
                    return 1;
                })))
                .then(Commands.literal("info").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("stage=" + FxSettings.stage + " glow=" + FxSettings.glowType + " sinNiebla=" + FxSettings.noFog), false);
                    return 1;
                })));
    }
}
