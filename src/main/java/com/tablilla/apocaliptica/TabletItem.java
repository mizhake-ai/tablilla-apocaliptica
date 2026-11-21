package com.tablilla.apocaliptica;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** La tablilla: R abre el menu, clic derecho mantenido ~1 segundo sobre un objetivo y cae el ataque. */
public class TabletItem extends Item {
    private static final int CHARGE_TICKS = 20;
    public static final String TAG_LASER = "laser";

    public TabletItem(Properties properties) {
        super(properties);
    }

    public static Laser selected(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            Laser l = Laser.byId(tag.getString(TAG_LASER));
            if (l != null) return l;
        }
        return Laser.PLASMA;
    }

    public static void select(ItemStack stack, Laser laser) {
        stack.getOrCreateTag().putString(TAG_LASER, laser.id);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tablilla.laser",
                Component.translatable(selected(stack).nameKey())).withStyle(ChatFormatting.GOLD));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (StrikeManager.isBusy()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("msg.tablilla.busy"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        int used = getUseDuration(stack) - remaining;

        if (used < CHARGE_TICKS) {
            int pct = used * 100 / CHARGE_TICKS;
            player.displayClientMessage(Component.translatable("msg.tablilla.charging", pct), true);
        } else if (used == CHARGE_TICKS) {
            fire((ServerLevel) level, player, stack);
            player.stopUsingItem();
            player.getCooldowns().addCooldown(this, 40);
        }
    }

    private void fire(ServerLevel level, Player player, ItemStack stack) {
        Laser laser = selected(stack);
        if (!laser.ready) {
            player.displayClientMessage(Component.translatable("msg.tablilla.not_ready"), true);
            return;
        }
        HitResult hit = player.pick(256.0D, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("msg.tablilla.no_target"), true);
            return;
        }
        BlockPos target = ((BlockHitResult) hit).getBlockPos();
        Strike strike = laser.create(level, target);
        if (strike == null) {
            player.displayClientMessage(Component.translatable("msg.tablilla.not_ready"), true);
            return;
        }
        StrikeManager.start(strike);
        player.displayClientMessage(Component.translatable("msg.tablilla.fired"), true);
    }
}
