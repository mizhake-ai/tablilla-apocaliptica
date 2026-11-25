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
 * La nave nodriza: plana y ancha. La popa es roca ancestral contaminada por el Warden (deepslate y sculk); la proa es
 * una isla flotante del End (piedra del End arriba, roca y tierra abajo, con unos pocos arboles de coro pequenos).
 * Anillos de piedra ancestral con sculk la rodean y los motores empujan por detras. Eje largo = X, proa = +X.
 * El origen es el centro de la isla (donde esta el canon del vientre). La comparten La Barca Celeste y La Caida.
 */
public final class ShipModel {
    public static final float PORT_Y = -48.0F;
    public static final float ENGINE_X = -395.0F;
    private static final float CELL = 8.0F;
    private static final float[] RING_X = {-300.0F, -240.0F, -180.0F, -110.0F, -50.0F, 10.0F, 70.0F, 120.0F};

    // indices de sprites
    private static final int DS_BRICKS = 0, DS_COBBLED = 1, DS_POLISHED = 2, DS_TILES = 3, DS_CHISELED = 4, DS_REINF = 5;
    private static final int SCULK = 6, SENSOR = 7, END = 8, END_BR = 9, STONE = 10, ANDESITE = 11, DIRT = 12, COBBLE = 13;

    private final List<float[]> faces = new ArrayList<>();   // cx,cy,cz,dir,sprite,shade
    private final List<float[]> sculkGlow = new ArrayList<>();
    private final List<float[]> trees = new ArrayList<>();   // x,y,z,dirx,dirz,altura
    private final List<float[]> ringBoxes = new ArrayList<>(); // x,y,z,sprite
    private final List<float[]> spires = new ArrayList<>();  // x,y,z,alto,radio
    private boolean built = false;

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

    /** Altura aproximada del lomo de la nave en x (sin escala). */
    public static float topY(float x) {
        if (x > -100.0F) return 7.0F;
        float a = Math.abs((x + 190.0F) / 88.0F);
        return a >= 1.0F ? 0.0F : 32.0F * (float) Math.cbrt(1.0F - a * a * a);
    }

    private boolean islandSolid(float x, float y, float z, int ix, int iy, int iz) {
        float ex = x / 128.0F;
        float ez = z / 80.0F;
        float n = 0.92F + 0.16F * hash01(ix, 7, iz) + 0.06F * sin(x * 0.04F + z * 0.03F);
        float r2 = (ex * ex + ez * ez) / (n * n);
        if (r2 >= 1.0F) return false;
        float depth = 42.0F * (1.0F - r2) * (0.75F + 0.35F * hash01(ix, iy, iz + 3));
        float top = 6.0F + 2.0F * hash01(ix, 1, iz);
        return y <= top && y >= -depth;
    }

    private boolean ancientSolid(float x, float y, float z, int ix, int iy, int iz) {
        float ax = Math.abs((x + 190.0F) / 88.0F);
        float ay = Math.abs(y / 32.0F);
        float az = Math.abs(z / 62.0F);
        float n = 0.88F + 0.24F * hash01(ix, iy, iz) + 0.05F * sin(z * 0.06F + x * 0.02F);
        return ax * ax * ax + ay * ay * ay + az * az * az < n * n * n;
    }

    private boolean solid(int ix, int iy, int iz) {
        float x = ix * CELL;
        float y = iy * CELL;
        float z = iz * CELL;
        return islandSolid(x, y, z, ix, iy, iz) || ancientSolid(x, y, z, ix, iy, iz);
    }

    private void build() {
        built = true;
        Random r = new Random(4242L);
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int x0 = (int) Math.floor(-290.0F / CELL);
        int x1 = (int) Math.ceil(140.0F / CELL);
        List<float[]> topCells = new ArrayList<>();
        List<float[]> sideCells = new ArrayList<>();
        for (int ix = x0; ix <= x1; ix++) {
            for (int iy = -8; iy <= 6; iy++) {
                for (int iz = -11; iz <= 11; iz++) {
                    if (!solid(ix, iy, iz)) continue;
                    float cx = ix * CELL;
                    float cy = iy * CELL;
                    float cz = iz * CELL;
                    boolean anc = cx < -100.0F;
                    for (int d = 0; d < 6; d++) {
                        if (solid(ix + dirs[d][0], iy + dirs[d][1], iz + dirs[d][2])) continue;
                        float roll = r.nextFloat();
                        int sp;
                        if (roll < (anc ? 0.12F : 0.06F)) {
                            sp = SCULK;
                            sculkGlow.add(new float[] {cx + dirs[d][0] * CELL * 0.55F, cy + dirs[d][1] * CELL * 0.55F, cz + dirs[d][2] * CELL * 0.55F});
                        } else if (roll < 0.125F) {
                            sp = SENSOR;
                        } else if (anc) {
                            int[] pick = {DS_BRICKS, DS_BRICKS, DS_COBBLED, DS_COBBLED, DS_POLISHED, DS_TILES, DS_CHISELED, DS_REINF};
                            sp = pick[r.nextInt(pick.length)];
                        } else if (d == 2) {
                            sp = r.nextFloat() < 0.35F ? END_BR : END;
                        } else if (cy < -6.0F) {
                            int[] pick = {STONE, STONE, ANDESITE, DIRT, COBBLE, END};
                            sp = pick[r.nextInt(pick.length)];
                        } else {
                            sp = r.nextFloat() < 0.6F ? END : STONE;
                        }
                        faces.add(new float[] {cx, cy, cz, d, sp, 0.6F + 0.4F * hash01(ix + d, iy, iz)});
                        if (!anc && d == 2 && cy > 2.0F) topCells.add(new float[] {cx, cy + CELL * 0.5F, cz});
                        if (!anc && d != 2 && d != 3 && cy > -6.0F && cy < 10.0F) sideCells.add(new float[] {cx + dirs[d][0] * CELL * 0.5F, cy, cz + dirs[d][2] * CELL * 0.5F, dirs[d][0], dirs[d][2]});
                    }
                }
            }
        }
        // arboles de coro pequenos encima de la isla y unos pocos en los costados
        for (int i = 0; i < 15 && !topCells.isEmpty(); i++) {
            float[] c = topCells.get(r.nextInt(topCells.size()));
            trees.add(new float[] {c[0], c[1], c[2], 0.0F, 0.0F, 14.0F + r.nextFloat() * 18.0F});
        }
        for (int i = 0; i < 6 && !sideCells.isEmpty(); i++) {
            float[] c = sideCells.get(r.nextInt(sideCells.size()));
            trees.add(new float[] {c[0], c[1], c[2], c[3], c[4], 12.0F + r.nextFloat() * 14.0F});
        }
        // anillos de piedra ancestral que rodean la nave
        for (float rx : RING_X) {
            float maxZ = 0.0F;
            float maxY = -999.0F;
            float minY = 999.0F;
            int ix = Math.round(rx / CELL);
            for (int iy = -8; iy <= 7; iy++) {
                for (int iz = -11; iz <= 11; iz++) {
                    if (!solid(ix, iy, iz)) continue;
                    maxZ = Math.max(maxZ, Math.abs(iz * CELL));
                    maxY = Math.max(maxY, iy * CELL);
                    minY = Math.min(minY, iy * CELL);
                }
            }
            if (maxY < minY) continue;
            float ry = (maxY - minY) * 0.5F + 9.0F;
            float rz = maxZ + 9.0F;
            float cyy = (maxY + minY) * 0.5F;
            int n = Math.max(20, (int) ((rz + ry) * 0.62F));
            for (int k = 0; k < n; k++) {
                float a = TAU * k / n;
                int sp = (k % 4 == 0) ? SCULK : (k % 3 == 0 ? DS_POLISHED : DS_BRICKS);
                ringBoxes.add(new float[] {rx, cyy + sin(a) * ry, cos(a) * rz, sp});
                if (k % 4 == 0) sculkGlow.add(new float[] {rx, cyy + sin(a) * ry, cos(a) * rz});
            }
        }
        // agujas de piedra ancestral en la popa
        for (int i = 0; i < 7; i++) {
            float x = -280.0F + r.nextFloat() * 160.0F;
            float z = (r.nextFloat() - 0.5F) * 100.0F;
            spires.add(new float[] {x, topY(x) * 0.85F, z, 36.0F + r.nextFloat() * 60.0F, 7.0F + r.nextFloat() * 5.0F});
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
        int sprite = (int) f[4];
        float k = f[5] * faceLight(d) * dark;
        float cr = k;
        float cg = k;
        float cb = k;
        if (sprite == SCULK || sprite == SENSOR) {
            cr = k * 0.60F;
            cg = k * 0.95F;
            cb = k * 0.95F;
        } else if (sprite == END || sprite == END_BR) {
            cr = k * 1.0F;
            cg = k * 0.96F;
            cb = k * 0.78F;
        } else if (sprite >= DS_BRICKS && sprite <= DS_REINF) {
            cr = k * 0.85F;
            cg = k * 0.88F;
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
        TextureAtlasSprite[] sp = {
                HullGeo.sprite(Blocks.DEEPSLATE_BRICKS), HullGeo.sprite(Blocks.COBBLED_DEEPSLATE),
                HullGeo.sprite(Blocks.POLISHED_DEEPSLATE), HullGeo.sprite(Blocks.DEEPSLATE_TILES),
                HullGeo.sprite(Blocks.CHISELED_DEEPSLATE), HullGeo.sprite(Blocks.REINFORCED_DEEPSLATE),
                HullGeo.sprite(Blocks.SCULK), HullGeo.sprite(Blocks.SCULK_SENSOR),
                HullGeo.sprite(Blocks.END_STONE), HullGeo.sprite(Blocks.END_STONE_BRICKS),
                HullGeo.sprite(Blocks.STONE), HullGeo.sprite(Blocks.ANDESITE),
                HullGeo.sprite(Blocks.DIRT), HullGeo.sprite(Blocks.COBBLESTONE)};
        TextureAtlasSprite[] metal = {
                HullGeo.sprite(Blocks.IRON_BLOCK), HullGeo.sprite(Blocks.POLISHED_DEEPSLATE),
                HullGeo.sprite(Blocks.WEATHERED_COPPER), HullGeo.sprite(Blocks.OXIDIZED_COPPER),
                HullGeo.sprite(Blocks.DEEPSLATE_TILES)};
        TextureAtlasSprite chorus = HullGeo.sprite(Blocks.CHORUS_PLANT);
        TextureAtlasSprite flower = HullGeo.sprite(Blocks.CHORUS_FLOWER);

        for (float[] f : faces) rockFace(vc, pose, sp[(int) f[4]], f, q, dark);

        // ---- anillos de piedra ancestral con sculk
        for (float[] b : ringBoxes) {
            int s = (int) b[3];
            float k = (s == SCULK ? 0.9F : 0.85F) * dark;
            HullGeo.box(vc, pose, sp[s], b[0] * q, b[1] * q, b[2] * q, 4.5F * q, 4.5F * q, 4.5F * q,
                    s == SCULK ? k * 0.6F : k * 0.85F, s == SCULK ? k * 0.95F : k * 0.88F, s == SCULK ? k * 0.95F : k * 0.95F, false, false);
        }

        // ---- agujas ancestrales de la popa
        for (float[] s : spires) {
            HullGeo.frustum(vc, pose, new TextureAtlasSprite[] {sp[DS_BRICKS], sp[DS_POLISHED], sp[DS_COBBLED]},
                    s[0] * q, s[1] * q, s[2] * q, 0, s[3] * q, s[4] * q, 0.8F * q, 4, 1, 4, 0.85F * dark, (float) (Math.PI / 4.0));
        }

        // ---- arboles de coro pequenos
        for (float[] t : trees) {
            float h = t[5] * q;
            float x = t[0] * q;
            float y = t[1] * q;
            float z = t[2] * q;
            if (t[3] == 0.0F && t[4] == 0.0F) {
                HullGeo.box(vc, pose, chorus, x, y + h * 0.5F, z, 1.7F * q, h * 0.5F, 1.7F * q, 0.75F * dark, 0.7F * dark, 0.8F * dark, true, false);
                HullGeo.box(vc, pose, chorus, x + 5.0F * q, y + h * 0.72F, z, 5.0F * q, 1.5F * q, 1.5F * q, 0.75F * dark, 0.7F * dark, 0.8F * dark, false, false);
                HullGeo.box(vc, pose, flower, x + 10.5F * q, y + h * 0.72F, z, 2.6F * q, 2.6F * q, 2.6F * q, 0.9F * dark, 0.85F * dark, 0.95F * dark, false, false);
                HullGeo.box(vc, pose, flower, x, y + h + 2.4F * q, z, 2.8F * q, 2.8F * q, 2.8F * q, 0.9F * dark, 0.85F * dark, 0.95F * dark, false, false);
            } else {
                float hx = Math.abs(t[3]) > 0.5F ? h * 0.5F : 1.6F * q;
                float hz = Math.abs(t[4]) > 0.5F ? h * 0.5F : 1.6F * q;
                float ox = t[3] * h * 0.5F;
                float oz = t[4] * h * 0.5F;
                HullGeo.box(vc, pose, chorus, x + ox, y, z + oz, hx, 1.6F * q, hz, 0.75F * dark, 0.7F * dark, 0.8F * dark, false, false);
                HullGeo.box(vc, pose, flower, x + t[3] * (h + 2.0F * q), y, z + t[4] * (h + 2.0F * q), 2.6F * q, 2.6F * q, 2.6F * q, 0.9F * dark, 0.85F * dark, 0.95F * dark, false, false);
            }
        }

        // ---- motor principal (popa)
        float md = dark;
        float rzm = (float) (Math.PI / 2.0);
        push(pose, -270.0F * q, 0, 0, rzm, 0);
        HullGeo.frustum(vc, pose, metal, 0, 0, 0, 0, 95.0F * q, 36.0F * q, 27.0F * q, 12, 2, 4, 0.95F * md, 0.0F);
        HullGeo.frustum(vc, pose, metal, 0, 0, 0, -6.0F * q, 6.0F * q, 43.0F * q, 43.0F * q, 12, 2, 1, 1.0F * md, 0.0F);
        HullGeo.frustum(vc, pose, metal, 0, 0, 0, 95.0F * q, 125.0F * q, 27.0F * q, 40.0F * q, 12, 2, 2, 0.85F * md, 0.0F);
        pop(pose, -270.0F * q, 0, 0, rzm, 0);

        // ---- 4 toberas laterales
        for (int i = 0; i < 4; i++) {
            float oy = (i % 2 == 0 ? 1 : -1) * 24.0F * q;
            float oz = (i < 2 ? 1 : -1) * 32.0F * q;
            push(pose, -335.0F * q, oy, oz, rzm, 0);
            HullGeo.frustum(vc, pose, metal, 0, 0, 0, 0, 22.0F * q, 9.0F * q, 11.0F * q, 10, 1, 2, 0.9F * md, 0.0F);
            HullGeo.frustum(vc, pose, metal, 0, 0, 0, 22.0F * q, 44.0F * q, 11.0F * q, 17.0F * q, 10, 1, 2, 0.8F * md, 0.0F);
            pop(pose, -335.0F * q, oy, oz, rzm, 0);
        }

        // ---- puntales entre el motor y la popa de roca
        for (int i = 0; i < 6; i++) {
            float a = i * TAU / 6 + 0.3F;
            HullGeo.box(vc, pose, metal[i % 3], -225.0F * q, cos(a) * 26.0F * q, sin(a) * 38.0F * q, 55.0F * q, 2.4F * q, 2.4F * q,
                    0.85F * md, 0.87F * md, 0.9F * md, false, false);
        }

        // ---- canon del vientre
        HullGeo.box(vc, pose, metal[0], 0, (PORT_Y + 6.0F) * q, 0, 14.0F * q, 6.0F * q, 14.0F * q, 0.9F * md, 0.92F * md, 0.97F * md, false, false);
        HullGeo.box(vc, pose, metal[4], 0, (PORT_Y - 1.0F) * q, 0, 8.0F * q, 2.0F * q, 8.0F * q, 0.5F * md, 0.5F * md, 0.55F * md, false, true);
    }

    private static void dot(VertexConsumer vc, Matrix4f m, float x, float y, float z, float s, float cr, float cg, float cb, float a) {
        FxGeo.quad(vc, m, x - s, y, z, a, x + s, y, z, a, x + s, y + s * 1.6F, z, a, x - s, y + s * 1.6F, z, a, cr, cg, cb);
        FxGeo.quad(vc, m, x, y, z - s, a, x, y, z + s, a, x, y + s * 1.6F, z + s, a, x, y + s * 1.6F, z - s, a, cr, cg, cb);
    }

    /** Luces: runas de los anillos, brillo del sculk, canon, toberas y estela de los motores. */
    public void lights(VertexConsumer vc, Matrix4f m, float t, float q, float gr, float gg, float gb,
                       float engine, float damage) {
        if (!built) build();
        Random rnd = new Random(99L);
        float pulse = 0.55F + 0.45F * sin(t * 0.07F);
        for (int i = 0; i < ringBoxes.size(); i += 2) {
            if (rnd.nextFloat() < damage * 0.6F) continue;
            float[] b = ringBoxes.get(i);
            float on = 0.5F + 0.5F * sin(t * 0.12F + i);
            dot(vc, m, b[0] * q, b[1] * q + 5.0F * q, b[2] * q, 1.3F * q, gr, gg, gb, 0.85F * on);
        }
        for (int i = 0; i < sculkGlow.size(); i++) {
            if (i % 2 == 1) continue;
            float[] s = sculkGlow.get(i);
            dot(vc, m, s[0] * q, s[1] * q, s[2] * q, 2.2F * q, 0.2F, 0.95F, 0.9F, 0.5F * pulse * (1.0F - 0.6F * damage));
        }
        FxGeo.ring(vc, m, 0, (PORT_Y - 3.0F) * q, 0, 0.0F, 9.0F * q, 20, gr, gg, gb, 0.5F + 0.4F * engine, 0.3F);

        float flick = 0.8F + 0.2F * sin(t * 0.9F);
        for (int i = 0; i < 4; i++) {
            float oy = (i % 2 == 0 ? 1 : -1) * 24.0F * q;
            float oz = (i < 2 ? 1 : -1) * 32.0F * q;
            float rad = 15.0F * q * (0.4F + 0.6F * engine) * flick;
            FxGeo.sphere(vc, m, -358.0F * q, oy, oz, rad * 0.5F, 6, 10, 1.0F, 1.0F, 1.0F, 0.9F * engine);
            FxGeo.sphere(vc, m, -358.0F * q, oy, oz, rad, 6, 10, gr, gg, gb, 0.45F * engine);
        }
        float crad = 30.0F * q * (0.4F + 0.6F * engine) * flick;
        FxGeo.sphere(vc, m, -398.0F * q, 0, 0, crad * 0.5F, 6, 10, 1.0F, 1.0F, 1.0F, 0.9F * engine);
        FxGeo.sphere(vc, m, -398.0F * q, 0, 0, crad, 8, 12, gr, gg, gb, 0.5F * engine);
        FxGeo.sphere(vc, m, -398.0F * q, 0, 0, crad * 1.7F, 8, 12, gr, gg, gb, 0.14F * engine);
        if (engine > 0.05F) {
            FxGeo.tube(vc, m, -398.0F * q, 0, 0, -398.0F * q - 260.0F * q * engine, 0, 0, 24.0F * q, 3.0F * q, 12, gr, gg, gb, 0.40F * engine, 0.0F);
            FxGeo.tube(vc, m, -398.0F * q, 0, 0, -398.0F * q - 150.0F * q * engine, 0, 0, 11.0F * q, 2.0F * q, 10, 1.0F, 1.0F, 1.0F, 0.55F * engine, 0.0F);
        }
    }
}
