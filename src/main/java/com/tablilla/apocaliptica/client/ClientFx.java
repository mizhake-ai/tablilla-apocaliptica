package com.tablilla.apocaliptica.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
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
    private static boolean hullBroken = false;

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

    private static RenderLevelStageEvent.Stage stageOf(int n) {
        switch (n) {
            case 1:
                return RenderLevelStageEvent.Stage.AFTER_WEATHER;
            case 2:
                return RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
            case 3:
                return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
            default:
                return RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        }
    }

    private static RenderType glowType() {
        switch (FxSettings.glowType) {
            case 1:
                return RenderType.gui();
            case 2:
                return RenderType.debugQuads();
            default:
                return RenderType.lightning();
        }
    }

    public static void handle(FxPacket p) {
        if (p.laser < 0) {
            ACTIVE.clear();
            Debris.clear();
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
            case DOWNFALL:
                return new DownfallFx();
            case METEOR:
                return new MeteorFx(p.seed, p.scale);
            case MOTHERSHIP:
                return new MothershipFx();
            case PLANETKILLER:
                return new PlanetKillerFx();
            case NUCLEAR:
                return new NuclearFx();
            case SINKHOLE:
                return new SinkholeFx(p.seed, p.scale);
            case PAYLOAD:
                return new PayloadFx();
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
            Debris.clear();
            return;
        }
        if (mc.isPaused()) return;
        // primero se mueven los bloques ya lanzados, luego los efectos crean los nuevos
        Debris.tick(mc.level);
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
        if (event.getStage() != stageOf(FxSettings.stage)) return;
        if (ACTIVE.isEmpty() && !Debris.any()) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        // sin niebla: la niebla de distancia borra los rayos y la nave lejana (se ven como a traves de agua o cristal)
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        try {
            if (FxSettings.noFog) FogRenderer.setupNoFog();
            drawAll(ps, cam, pt, buf, mc);
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private static void drawAll(PoseStack ps, Vec3 cam, float pt, MultiBufferSource.BufferSource buf, Minecraft mc) {
        // 1) geometria solida con texturas de bloques (nave, agujero negro)
        if (!hullBroken) {
            try {
                boolean any = false;
                for (Active a : ACTIVE) {
                    if (a.fx.usesHull()) any = true;
                }
                if (any) {
                    RenderType rt = RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS);
                    VertexConsumer hull = buf.getBuffer(rt);
                    for (Active a : ACTIVE) {
                        if (!a.fx.usesHull()) continue;
                        ps.pushPose();
                        ps.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
                        FxCtx.camX = cam.x - a.x;
                        FxCtx.camY = cam.y - a.y;
                        FxCtx.camZ = cam.z - a.z;
                        a.fx.renderHull(hull, ps.last(), a.t + pt, a.seed, a.scale);
                        ps.popPose();
                    }
                    buf.endBatch(rt);
                }
            } catch (Throwable e) {
                hullBroken = true;
            }
        }

        // 2) bloques y arboles voladores
        Debris.render(ps, cam, pt, buf);

        // 3) geometria oscura simple (sombras)
        VertexConsumer solid = buf.getBuffer(RenderType.gui());
        for (Active a : ACTIVE) {
            ps.pushPose();
            ps.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
            Matrix4f m = ps.last().pose();
            FxCtx.camX = cam.x - a.x;
            FxCtx.camY = cam.y - a.y;
            FxCtx.camZ = cam.z - a.z;
            a.fx.renderSolid(solid, m, a.t + pt, a.seed, a.scale);
            ps.popPose();
        }
        buf.endBatch(RenderType.gui());

        // 4) luz
        RenderType glowRt = glowType();
        VertexConsumer glow = buf.getBuffer(glowRt);
        for (Active a : ACTIVE) {
            ps.pushPose();
            ps.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
            Matrix4f m = ps.last().pose();
            FxCtx.camX = cam.x - a.x;
            FxCtx.camY = cam.y - a.y;
            FxCtx.camZ = cam.z - a.z;
            a.fx.renderGlow(glow, m, a.t + pt, a.seed, a.scale);
            ps.popPose();
        }
        buf.endBatch(glowRt);
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
        for (Active a : ACTIVE) {
            float fl = a.fx.flare(a.t);
            if (fl > 0.01F) drawFlare(g, mc, a, w, h, fl);
        }
    }

    /** Destello de lente: una linea horizontal de luz que cruza la pantalla a la altura del punto brillante. */
    private static void drawFlare(GuiGraphics g, Minecraft mc, Active a, int w, int h, float k) {
        double dx = a.x - mc.player.getX();
        double dy = a.y + a.fx.flareHeight() - mc.player.getEyeY();
        double dz = a.z - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double dist = Math.sqrt(horiz * horiz + dy * dy);
        if (dist < 2.0) return;
        double tYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double tPitch = -Math.toDegrees(Math.atan2(dy, Math.max(horiz, 0.001)));
        double yd = Mth.wrapDegrees(tYaw - mc.player.getYRot());
        double pd = tPitch - mc.player.getXRot();
        if (Math.abs(yd) > 80.0 || Math.abs(pd) > 80.0) return;
        double fov = Math.max(30.0, mc.options.fov().get());
        double px = (h / 2.0) / Math.tan(Math.toRadians(fov / 2.0));
        int fx = (int) (w / 2.0 + Math.tan(Math.toRadians(yd)) * px);
        int fy = (int) (h / 2.0 + Math.tan(Math.toRadians(pd)) * px);
        float fade = near(a, mc, 700.0F * a.scale);
        k *= 0.35F + 0.65F * fade;
        int rgb = a.fx.flareColor() & 0xFFFFFF;

        int segs = 48;
        for (int i = 0; i < segs; i++) {
            int x0 = i * w / segs;
            int x1 = (i + 1) * w / segs;
            double xm = (x0 + x1) * 0.5;
            double fall = Math.max(0.0, 1.0 - Math.abs(xm - fx) / (w * 0.65));
            float al = k * (float) (0.18 + 0.82 * fall);
            int aGlow = (int) (Mth.clamp(al * 0.30F, 0.0F, 1.0F) * 255.0F);
            int aCore = (int) (Mth.clamp(al * 0.95F, 0.0F, 1.0F) * 255.0F);
            if (aGlow > 0) g.fill(x0, fy - 4, x1, fy + 4, (aGlow << 24) | rgb);
            if (aCore > 0) g.fill(x0, fy - 1, x1, fy + 1, (aCore << 24) | 0xFFFFFF);
        }
        for (int n = 1; n <= 6; n++) {
            int size = n * 6;
            int al = (int) (Mth.clamp(k * 0.55F / n, 0.0F, 1.0F) * 255.0F);
            if (al > 0) g.fill(fx - size, fy - size / 2, fx + size, fy + size / 2, (al << 24) | rgb);
        }
    }
}
