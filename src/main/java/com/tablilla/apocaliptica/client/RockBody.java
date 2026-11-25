package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.sin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;

/** Roca irregular hecha de cubos (magma, piedra negra, obsidiana): el cuerpo de un meteoro gigante. Radio 1 = 'radius' bloques. */
public final class RockBody {
    private static final int N = 9;
    private final List<float[]> faces = new ArrayList<>();
    private final float cell;
    private final float radius;

    public RockBody(float radius, long seed) {
        this.radius = radius;
        this.cell = radius / 7.0F;
        Random r = new Random(seed);
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        boolean[][][] s = new boolean[2 * N + 3][2 * N + 3][2 * N + 3];
        float[][][] nz = new float[2 * N + 3][2 * N + 3][2 * N + 3];
        for (int x = -N; x <= N; x++) {
            for (int y = -N; y <= N; y++) {
                for (int z = -N; z <= N; z++) {
                    nz[x + N + 1][y + N + 1][z + N + 1] = r.nextFloat();
                    float d = (float) Math.sqrt(x * x + y * y * 1.25F + z * z) / 7.0F;
                    float n = 0.78F + 0.30F * nz[x + N + 1][y + N + 1][z + N + 1] + 0.12F * sin(x * 0.9F + z * 0.7F);
                    s[x + N + 1][y + N + 1][z + N + 1] = d < n;
                }
            }
        }
        for (int x = -N; x <= N; x++) {
            for (int y = -N; y <= N; y++) {
                for (int z = -N; z <= N; z++) {
                    if (!s[x + N + 1][y + N + 1][z + N + 1]) continue;
                    for (int d = 0; d < 6; d++) {
                        int ax = x + N + 1 + dirs[d][0];
                        int ay = y + N + 1 + dirs[d][1];
                        int az = z + N + 1 + dirs[d][2];
                        if (ax >= 0 && ay >= 0 && az >= 0 && ax < s.length && ay < s.length && az < s.length && s[ax][ay][az]) continue;
                        float f = r.nextFloat();
                        int sp = f < 0.42F ? 0 : (f < 0.62F ? 1 : (f < 0.85F ? 2 : 3));
                        faces.add(new float[] {x * cell, y * cell, z * cell, d, sp, 0.6F + 0.4F * r.nextFloat()});
                    }
                }
            }
        }
    }

    public float radius() {
        return radius;
    }

    /** Dibuja la roca centrada en el origen (la posicion y el giro los pone quien llama con push/pop). */
    public void draw(VertexConsumer vc, PoseStack.Pose pose, float q, float heat) {
        TextureAtlasSprite[] sp = {HullGeo.sprite(Blocks.MAGMA_BLOCK), HullGeo.sprite(Blocks.NETHERRACK),
                HullGeo.sprite(Blocks.BLACKSTONE), HullGeo.sprite(Blocks.OBSIDIAN)};
        float h = cell * 0.5F * q;
        for (float[] f : faces) {
            float cx = f[0] * q;
            float cy = f[1] * q;
            float cz = f[2] * q;
            int d = (int) f[3];
            float k = f[5] * (d == 2 ? 1.0F : (d == 3 ? 0.55F : 0.8F));
            float cr = k * (0.8F + 0.5F * heat);
            float cg = k * (0.75F + 0.1F * heat);
            float cb = k * (0.7F - 0.2F * heat);
            TextureAtlasSprite t = sp[(int) f[4]];
            switch (d) {
                case 0:
                    HullGeo.quad(vc, pose, t, cx + h, cy - h, cz - h, cx + h, cy - h, cz + h, cx + h, cy + h, cz + h, cx + h, cy + h, cz - h, 1, 0, 0, cr, cg, cb);
                    break;
                case 1:
                    HullGeo.quad(vc, pose, t, cx - h, cy - h, cz - h, cx - h, cy - h, cz + h, cx - h, cy + h, cz + h, cx - h, cy + h, cz - h, -1, 0, 0, cr, cg, cb);
                    break;
                case 2:
                    HullGeo.quad(vc, pose, t, cx - h, cy + h, cz - h, cx + h, cy + h, cz - h, cx + h, cy + h, cz + h, cx - h, cy + h, cz + h, 0, 1, 0, cr, cg, cb);
                    break;
                case 3:
                    HullGeo.quad(vc, pose, t, cx - h, cy - h, cz - h, cx + h, cy - h, cz - h, cx + h, cy - h, cz + h, cx - h, cy - h, cz + h, 0, -1, 0, cr, cg, cb);
                    break;
                case 4:
                    HullGeo.quad(vc, pose, t, cx - h, cy - h, cz + h, cx + h, cy - h, cz + h, cx + h, cy + h, cz + h, cx - h, cy + h, cz + h, 0, 0, 1, cr, cg, cb);
                    break;
                default:
                    HullGeo.quad(vc, pose, t, cx - h, cy - h, cz - h, cx + h, cy - h, cz - h, cx + h, cy + h, cz - h, cx - h, cy + h, cz - h, 0, 0, -1, cr, cg, cb);
                    break;
            }
        }
    }
}
