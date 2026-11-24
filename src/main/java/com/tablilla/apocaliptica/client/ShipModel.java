package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.sin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;

/**
 * La nave nodriza: un asteroide de roca con una seccion mecanica (motores, anillos, puntales) en la popa y
 * manchas de sculk (parte biologica) repartidas por la roca. Eje largo = X, proa = +X, popa = -X.
 * La comparten La Barca Celeste y La Caida. Se dibuja centrada en el origen.
 */
public final class ShipModel {
    public static final float HALF_LEN = 240.0F;
    public static final float ENGINE_X = -275.0F;
    public static final float PORT_Y = -60.0F;
    private static final float CELL = 8.0F;
    private static final float ROCK_X0 = -150.0F;
    private static final float ROCK_X1 = 232.0F;
    private static final float BY = 46.0F;
    private static final float BZ = 56.0F;
    private static final float[] RING_X = {-120.0F, -95.0F, -70.0F, -30.0F, 10.0F, 50.0F, 90.0F, 130.0F};

    // caras de roca expuestas: cx, cy, cz, direccion (0..5), sprite, brillo
    private final List<float[]> faces = new ArrayList<>();
    private final List<float[]> sculk = new ArrayList<>();
    private final List<float[]> antennas = new ArrayList<>();
    private boolean built = false;

    /** Mueve y gira el sistema de coordenadas (hay que deshacerlo con pop). */
    public static void push(PoseStack.Pose p, float x, float y, float z, float rz, float rx) {
        p.pose().translate(x, y, z).rotateZ(rz).rotateX(rx);
        p.normal().rotateZ(rz).rotateX(rx);
    }

    public static void pop(PoseStack.Pose p, float x, float y, float z, float rz, float rx) {
        p.normal().rotateX(-rx).rotateZ(-rz);
        p.pose().rotateX(-rx).rotateZ(-rz).translate(-x, -y, -z);
    }

    public static void push(Matrix4f m, float x, float y, float z, float rz, float rx) {
        m.translate(x, y, z).rotateZ(rz).rotateX(rx);
    }

    public static void pop(Matrix4f m, float x, float y, float z, float rz, float rx) {
        m.rotateX(-rx).rotateZ(-rz).translate(-x, -y, -z);
    }

    private static float hash01(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0F;
    }

    /** Perfil de la roca (0..1) a lo largo del eje. */
    private static float prof(float x) {
        float t = (x - ROCK_X0) / (ROCK_X1 - ROCK_X0);
        if (t <= 0.0F || t >= 1.0F) return 0.0F;
        return (float) Math.pow(4.0F * t * (1.0F - t), 0.55);
    }

    /** Altura de la panza de roca en x (sin escala). */
    public static float bellyY(float x) {
        return -BY * prof(x) * 0.95F;
    }

    /** Altura del lomo de roca en x (sin escala). */
    public static float topY(float x) {
        return BY * prof(x) * 0.95F;
    }

    private boolean solid(int ix, int iy, int iz) {
        float x = ix * CELL;
        float y = iy * CELL;
        float z = iz * CELL;
        float p = prof(x);
        if (p <= 0.02F) return false;
        float n = 0.80F + 0.34F * hash01(ix, iy, iz) + 0.10F * sin(x * 0.05F + 1.0F) + 0.08F * sin(z * 0.07F + y * 0.03F);
        float a = y / (p * BY);
        float b = z / (p * BZ);
        return a * a + b * b < n * n;
    }

    private void build() {
        built = true;
        Random r = new Random(4242L);
        int x0 = (int) Math.floor(ROCK_X0 / CELL) - 1;
        int x1 = (int) Math.ceil(ROCK_X1 / CELL) + 1;
        int yr = (int) Math.ceil(BY * 1.5F / CELL);
        int zr = (int) Math.ceil(BZ * 1.5F / CELL);
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int ix = x0; ix <= x1; ix++) {
            for (int iy = -yr; iy <= yr; iy++) {
                for (int iz = -zr; iz <= zr; iz++) {
                    if (!solid(ix, iy, iz)) continue;
                    for (int d = 0; d < 6; d++) {
                        if (solid(ix + dirs[d][0], iy + dirs[d][1], iz + dirs[d][2])) continue;
                        float roll = r.nextFloat();
                        int sp;
                        float shade = 0.55F + 0.45F * hash01(ix + d, iy, iz);
                        if (roll < 0.055F) {
                            sp = 6; // sculk
                            sculk.add(new float[] {ix * CELL + dirs[d][0] * CELL * 0.55F, iy * CELL + dirs[d][1] * CELL * 0.55F, iz * CELL + dirs[d][2] * CELL * 0.55F});
                        } else if (roll < 0.065F) {
                            sp = 7; // sensor de sculk
                        } else {
                            sp = r.nextInt(6);
                        }
                        faces.add(new float[] {ix * CELL, iy * CELL, iz * CELL, d, sp, shade});
                        if (d == 2 && r.nextFloat() < 0.035F) antennas.add(new float[] {ix * CELL, iy * CELL + CELL * 0.5F, iz * CELL});
                    }
                }
            }
        }
    }

    private static float faceLight(int d) {
        switch (d) {
            case 2:
                return 1.0F;
            case 3:
                return 0.5F;
            case 0:
                return 0.85F;
            case 1:
                return 0.7F;
            case 4:
                return 0.8F;
            default:
                return 0.66F;
        }
    }

    private void rockFace(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float[] f, float q, float dark) {
        float h = CELL * 0.5F * q;
        float cx = f[0] * q;
        float cy = f[1] * q;
        float cz = f[2] * q;
        int d = (int) f[3];
        float k = f[5] * faceLight(d) * dark;
        float cr = k * 0.95F;
        float cg = k * 0.88F;
        float cb = k * 0.82F;
        if (f[4] >= 6.0F) {
            cr = k * 0.55F;
            cg = k * 0.95F;
            cb = k * 0.95F;
        }
        switch (d) {
            case 0:
                HullGeo.quad(vc, pose, sp, cx + h, cy - h, cz - h, cx + h, cy - h, cz + h, cx + h, cy + h, cz + h, cx + h, cy + h, cz - h, 1, 0, 0, cr, cg, cb);
                break;
            case 1:
                HullGeo.quad(vc, pose, sp, cx - h, cy - h, cz - h, cx - h, cy - h, cz + h, cx - h, cy + h, cz + h, cx - h, cy + h, cz - h, -1, 0, 0, cr, cg, cb);
                break;
            case 2:
                HullGeo.quad(vc, pose, sp, cx - h, cy + h, cz - h, cx + h, cy + h, cz - h, cx + h, cy + h, cz + h, cx - h, cy + h, cz + h, 0, 1, 0, cr, cg, cb);
                break;
            case 3:
                HullGeo.quad(vc, pose, sp, cx - h, cy - h, cz - h, cx + h, cy - h, cz - h, cx + h, cy - h, cz + h, cx - h, cy - h, cz + h, 0, -1, 0, cr, cg, cb);
                break;
            case 4:
                HullGeo.quad(vc, pose, sp, cx - h, cy - h, cz + h, cx + h, cy - h, cz + h, cx + h, cy + h, cz + h, cx - h, cy + h, cz + h, 0, 0, 1, cr, cg, cb);
                break;
            default:
                HullGeo.quad(vc, pose, sp, cx - h, cy - h, cz - h, cx + h, cy - h, cz - h, cx + h, cy + h, cz - h, cx - h, cy + h, cz - h, 0, 0, -1, cr, cg, cb);
                break;
        }
    }

    /** Casco solido centrado en el origen. burn = 0..1 oscurece (quemado). */
    public void hull(VertexConsumer vc, PoseStack.Pose pose, long seed, float q, float burn) {
        if (!built) build();
        float dark = 1.0F - 0.5F * burn;
        TextureAtlasSprite[] rock = {
                HullGeo.sprite(Blocks.DRIPSTONE_BLOCK), HullGeo.sprite(Blocks.COBBLED_DEEPSLATE),
                HullGeo.sprite(Blocks.BLACKSTONE), HullGeo.sprite(Blocks.TUFF),
                HullGeo.sprite(Blocks.SMOOTH_BASALT), HullGeo.sprite(Blocks.BROWN_TERRACOTTA),
                HullGeo.sprite(Blocks.SCULK), HullGeo.sprite(Blocks.SCULK_SENSOR)};
        TextureAtlasSprite[] metal = {
                HullGeo.sprite(Blocks.IRON_BLOCK), HullGeo.sprite(Blocks.POLISHED_DEEPSLATE),
                HullGeo.sprite(Blocks.WEATHERED_COPPER), HullGeo.sprite(Blocks.OXIDIZED_COPPER),
                HullGeo.sprite(Blocks.DEEPSLATE_TILES)};

        for (float[] f : faces) rockFace(vc, pose, rock[(int) f[4]], f, q, dark);

        // ---- motor principal (popa)
        float md = dark;
        push(pose, -150.0F * q, 0, 0, (float) (Math.PI / 2.0), 0);
        HullGeo.frustum(vc, pose, metal, 0, 0, 0, 0, 95.0F * q, 36.0F * q, 27.0F * q, 16, 2, 4, 0.95F * md, 0.0F);
        HullGeo.frustum(vc, pose, metal, 0, 0, 0, -6.0F * q, 6.0F * q, 43.0F * q, 43.0F * q, 16, 2, 1, 1.0F * md, 0.0F);
        HullGeo.frustum(vc, pose, metal, 0, 0, 0, 95.0F * q, 125.0F * q, 27.0F * q, 40.0F * q, 16, 2, 2, 0.85F * md, 0.0F);
        pop(pose, -150.0F * q, 0, 0, (float) (Math.PI / 2.0), 0);

        // ---- 4 toberas laterales
        for (int i = 0; i < 4; i++) {
            float oy = (i % 2 == 0 ? 1 : -1) * 26.0F * q;
            float oz = (i < 2 ? 1 : -1) * 26.0F * q;
            push(pose, -215.0F * q, oy, oz, (float) (Math.PI / 2.0), 0);
            HullGeo.frustum(vc, pose, metal, 0, 0, 0, 0, 22.0F * q, 9.0F * q, 11.0F * q, 12, 1, 2, 0.9F * md, 0.0F);
            HullGeo.frustum(vc, pose, metal, 0, 0, 0, 22.0F * q, 44.0F * q, 11.0F * q, 17.0F * q, 12, 1, 2, 0.8F * md, 0.0F);
            pop(pose, -215.0F * q, oy, oz, (float) (Math.PI / 2.0), 0);
        }

        // ---- anillos metalicos alrededor de la roca
        for (float rx : RING_X) {
            float rr = Math.max(BY, BZ) * prof(rx) + 4.0F;
            if (rr < 12.0F) continue;
            push(pose, rx * q, 0, 0, (float) (Math.PI / 2.0), 0);
            HullGeo.frustum(vc, pose, metal, 0, 0, 0, -3.0F * q, 3.0F * q, rr * q, rr * q, 20, 1, 1, 0.9F * md, 0.0F);
            pop(pose, rx * q, 0, 0, (float) (Math.PI / 2.0), 0);
        }

        // ---- puntales largos entre el motor y la roca
        for (int i = 0; i < 6; i++) {
            float a = i * TAU / 6 + 0.3F;
            HullGeo.box(vc, pose, metal[i % 3], -130.0F * q, cos(a) * 40.0F * q, sin(a) * 40.0F * q, 55.0F * q, 2.2F * q, 2.2F * q,
                    0.85F * md, 0.87F * md, 0.9F * md, false, false);
        }

        // ---- antenas sobre la roca
        for (float[] a : antennas) {
            HullGeo.box(vc, pose, metal[1], a[0] * q, (a[1] + 9.0F) * q, a[2] * q, 1.4F * q, 9.0F * q, 1.4F * q,
                    0.8F * md, 0.82F * md, 0.88F * md, true, false);
        }

        // ---- canon del vientre
        HullGeo.box(vc, pose, metal[0], 0, (PORT_Y + 6.0F) * q, 0, 14.0F * q, 6.0F * q, 14.0F * q, 0.9F * md, 0.92F * md, 0.97F * md, false, false);
        HullGeo.box(vc, pose, metal[4], 0, (PORT_Y - 1.0F) * q, 0, 8.0F * q, 2.0F * q, 8.0F * q, 0.5F * md, 0.5F * md, 0.55F * md, false, true);
    }

    private static void dot(VertexConsumer vc, Matrix4f m, float x, float y, float z, float s, float cr, float cg, float cb, float a) {
        FxGeo.quad(vc, m, x - s, y, z, a, x + s, y, z, a, x + s, y + s * 1.6F, z, a, x - s, y + s * 1.6F, z, a, cr, cg, cb);
        FxGeo.quad(vc, m, x, y, z - s, a, x, y, z + s, a, x, y + s * 1.6F, z + s, a, x, y + s * 1.6F, z - s, a, cr, cg, cb);
    }

    /**
     * Luces centradas en el origen: toberas, luces electronicas de los anillos y brillo de las manchas de sculk.
     * (gr, gg, gb) = color de las luces; engine = fuerza de los motores (0..1); damage 0..1 apaga luces al azar.
     */
    public void lights(VertexConsumer vc, Matrix4f m, float t, float q, float gr, float gg, float gb,
                       float engine, float damage) {
        if (!built) build();
        Random rnd = new Random(99L);

        // luces electronicas en los anillos
        for (float rx : RING_X) {
            float rr = Math.max(BY, BZ) * prof(rx) + 4.0F;
            if (rr < 12.0F) continue;
            int n = Math.max(8, (int) (rr * 0.45F));
            for (int i = 0; i < n; i++) {
                if (rnd.nextFloat() < damage * 0.6F) continue;
                float a = i * TAU / n + rx * 0.1F;
                float on = 0.5F + 0.5F * sin(t * 0.15F + i * 1.3F + rx);
                dot(vc, m, rx * q, cos(a) * rr * q, sin(a) * rr * q, 1.3F * q, gr, gg, gb, 0.9F * on);
            }
        }

        // brillo biologico del sculk
        float pulse = 0.55F + 0.45F * sin(t * 0.07F);
        for (int i = 0; i < sculk.size(); i++) {
            if (i % 2 == 1) continue;
            float[] s = sculk.get(i);
            dot(vc, m, s[0] * q, s[1] * q, s[2] * q, 2.2F * q, 0.2F, 0.95F, 0.9F, 0.55F * pulse * (1.0F - 0.6F * damage));
        }

        // luz del canon del vientre
        FxGeo.ring(vc, m, 0, (PORT_Y - 3.0F) * q, 0, 0.0F, 9.0F * q, 20, gr, gg, gb, 0.5F + 0.4F * engine, 0.3F);

        // brillo de las toberas y de la tobera central
        float flick = 0.8F + 0.2F * sin(t * 0.9F);
        for (int i = 0; i < 4; i++) {
            float oy = (i % 2 == 0 ? 1 : -1) * 26.0F * q;
            float oz = (i < 2 ? 1 : -1) * 26.0F * q;
            float rad = 15.0F * q * (0.4F + 0.6F * engine) * flick;
            FxGeo.sphere(vc, m, -258.0F * q, oy, oz, rad * 0.5F, 6, 10, 1.0F, 1.0F, 1.0F, 0.9F * engine);
            FxGeo.sphere(vc, m, -258.0F * q, oy, oz, rad, 6, 10, gr, gg, gb, 0.45F * engine);
        }
        float crad = 30.0F * q * (0.4F + 0.6F * engine) * flick;
        FxGeo.sphere(vc, m, -280.0F * q, 0, 0, crad * 0.5F, 6, 10, 1.0F, 1.0F, 1.0F, 0.9F * engine);
        FxGeo.sphere(vc, m, -280.0F * q, 0, 0, crad, 8, 12, gr, gg, gb, 0.5F * engine);
        FxGeo.sphere(vc, m, -280.0F * q, 0, 0, crad * 1.7F, 8, 12, gr, gg, gb, 0.14F * engine);
        // estela del motor
        if (engine > 0.05F) {
            FxGeo.tube(vc, m, -280.0F * q, 0, 0, -280.0F * q - 260.0F * q * engine, 0, 0, 24.0F * q, 3.0F * q, 12, gr, gg, gb, 0.40F * engine, 0.0F);
            FxGeo.tube(vc, m, -280.0F * q, 0, 0, -280.0F * q - 150.0F * q * engine, 0, 0, 11.0F * q, 2.0F * q, 10, 1.0F, 1.0F, 1.0F, 0.55F * engine, 0.0F);
        }
    }
}
