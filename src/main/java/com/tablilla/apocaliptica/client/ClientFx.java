package com.tablilla.apocaliptica.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.FxPacket;
import com.tablilla.apocaliptica.Laser;
import com.tablilla.apocaliptica.TablillaMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Motor de efectos del cliente: recibe los avisos del servidor y dibuja todo. */
@Mod.EventBusSubscriber(modid = TablillaMod.MOD_ID, value = Dist.CLIENT)
public final class ClientFx {
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final RandomSource RNG = RandomSource.create();

    private static final class Active {
        final FxEffect fx;
        final double x;
        final double y;
        final double z;
        final long seed;
        final float scale;
        int t = 0;

        Active(FxEffect fx, double x, double y, double z, long seed, float scale) {
            this.fx = fx;
            this.x = x;
            this.y = y;
            this.z = z;
            this.seed = seed;
            this.scale = scale;
        }
    }

    private ClientFx() {}

    public static void handle(FxPacket p) {
        if (p.laser < 0) {
            ACTIVE.clear();
            return;
        }
        Laser[] all = Laser.values();
        if (p.laser >= all.length) return;
        FxEffect fx = create(all[p.laser], p);
        if (fx == null) return;
        ACTIVE.add(new Active(fx, p.pos.getX() + 0.5, p.pos.getY(), p.pos.getZ() + 0.5, p.seed, p.scale));
    }

    private static FxEffect create(Laser laser, FxPacket p) {
        switch (laser) {
            case PLASMA:
                return new PlasmaFx();
            case GODLY:
                return new GodlyFx();
            case BLACKHOLE:
                return new BlackHoleFx();
            case METEOR:
                return new MeteorFx(p.seed, p.scale);
            case MOTHERSHIP:
                return new MothershipFx();
            default:
                return null;
        }
    }

    /** 1 = encima del efecto, 0 = fuera de alcance. */
    private static float near(Active a, Minecraft mc, float range) {
        if (mc.player == null) return 0.0F;
        double dx = mc.player.getX() - a.x;
        double dy = mc.player.getY() - a.y;
        double dz = mc.player.getZ() - a.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return (float) Mth.clamp(1.0 - d / range, 0.0, 1.0);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            a.t++;
            a.fx.tick(mc.level, a.x, a.y, a.z, a.t, RNG, a.scale, a.seed);
            if (a.t >= a.fx.duration()) it.remove();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        VertexConsumer solid = buf.getBuffer(RenderType.gui());
        for (Active a : ACTIVE) {
            ps.pushPose();
            ps.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
            Matrix4f m = ps.last().pose();
            a.fx.renderSolid(solid, m, a.t + pt, a.seed, a.scale);
            ps.popPose();
        }
        buf.endBatch(RenderType.gui());

        VertexConsumer glow = buf.getBuffer(RenderType.lightning());
        for (Active a : ACTIVE) {
            ps.pushPose();
            ps.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
            Matrix4f m = ps.last().pose();
            a.fx.renderGlow(glow, m, a.t + pt, a.seed, a.scale);
            ps.popPose();
        }
        buf.endBatch(RenderType.lightning());
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float amp = 0.0F;
        for (Active a : ACTIVE) {
            amp += a.fx.shake(a.t) * near(a, mc, 380.0F * a.scale);
        }
        if (amp <= 0.001F) return;
        amp = Math.min(amp, 6.0F);
        double tt = mc.level.getGameTime() + event.getPartialTick();
        float yaw = (float) (Math.sin(tt * 2.3) + 0.6 * Math.sin(tt * 5.1)) * amp * 0.5F;
        float pitch = (float) (Math.cos(tt * 2.9) + 0.6 * Math.sin(tt * 4.3)) * amp * 0.5F;
        float roll = (float) Math.sin(tt * 3.7) * amp * 0.35F;
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiEvent.Pre event) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        Vec3 look = mc.player.getLookAngle();

        for (Active a : ACTIVE) {
            int tint = a.fx.tint(a.t);
            int ta = (tint >>> 24) & 255;
            if (ta > 0) {
                int al = (int) (ta * near(a, mc, 650.0F * a.scale));
                if (al > 0) g.fill(0, 0, w, h, (al << 24) | (tint & 0xFFFFFF));
            }
            float flash = a.fx.flash(a.t);
            if (flash > 0.01F) {
                Vec3 dir = new Vec3(a.x - mc.player.getX(), a.y + 40 - mc.player.getEyeY(), a.z - mc.player.getZ());
                double len = dir.length();
                double facing = len < 1.0 ? 1.0 : Math.max(0.0, look.dot(dir.scale(1.0 / len)));
                float k = flash * near(a, mc, 520.0F * a.scale) * (float) (0.35 + 0.65 * facing);
                int al = (int) (Mth.clamp(k, 0.0F, 1.0F) * 255.0F);
                if (al > 0) g.fill(0, 0, w, h, (al << 24) | 0xFFFFFF);
            }
        }
    }
}
