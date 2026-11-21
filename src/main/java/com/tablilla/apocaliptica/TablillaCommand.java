package com.tablilla.apocaliptica;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class TablillaCommand {
    private TablillaCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tablilla")
                .then(Commands.literal("give").executes(ctx -> give(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("select")
                        .then(Commands.argument("laser", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Laser.readyIds(), builder))
                                .executes(ctx -> select(ctx.getSource(), StringArgumentType.getString(ctx, "laser")))))
                .then(Commands.literal("terrain")
                        .then(Commands.literal("on").executes(ctx -> terrain(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> terrain(ctx.getSource(), false)))));
    }

    private static int give(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ItemStack stack = new ItemStack(ModItems.TABLILLA.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        src.sendSuccess(() -> Component.translatable("msg.tablilla.given"), false);
        return 1;
    }

    private static int select(CommandSourceStack src, String id) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Laser laser = Laser.byId(id);
        if (laser == null || !laser.ready) {
            src.sendFailure(Component.translatable("msg.tablilla.not_ready"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TABLILLA.get())) stack = player.getOffhandItem();
        if (!stack.is(ModItems.TABLILLA.get())) {
            src.sendFailure(Component.translatable("msg.tablilla.need_tablet"));
            return 0;
        }
        TabletItem.select(stack, laser);
        src.sendSuccess(() -> Component.translatable("msg.tablilla.selected",
                Component.translatable(laser.nameKey())), false);
        return 1;
    }

    private static int stop(CommandSourceStack src) {
        StrikeManager.stop();
        src.sendSuccess(() -> Component.translatable("msg.tablilla.stopped"), false);
        return 1;
    }

    private static int terrain(CommandSourceStack src, boolean on) {
        StrikeManager.terrainDamage = on;
        src.sendSuccess(() -> Component.translatable(on ? "msg.tablilla.terrain_on" : "msg.tablilla.terrain_off"), false);
        return 1;
    }
}
