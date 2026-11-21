package com.tablilla.apocaliptica.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tablilla.apocaliptica.Laser;
import com.tablilla.apocaliptica.ModItems;
import com.tablilla.apocaliptica.StrikeManager;
import com.tablilla.apocaliptica.TabletItem;
import com.tablilla.apocaliptica.TablillaMod;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** El menu de la tablilla (tecla R): una tablilla de piedra con runas ambar bordeadas de purpura. */
public class LaserWheelScreen extends Screen {
    private static final ResourceLocation PANEL = new ResourceLocation(TablillaMod.MOD_ID, "textures/gui/panel.png");
    private static final ResourceLocation LOCKED = new ResourceLocation(TablillaMod.MOD_ID, "textures/gui/icon_locked.png");
    private static final ResourceLocation RUNAS = new ResourceLocation(TablillaMod.MOD_ID, "runas");

    private static final int W = 320;
    private static final int H = 200;
    private static final int PER_PAGE = 5;
    private static final int CARD_W = 54;
    private static final int CARD_H = 78;
    private static final int CARD_GAP = 6;
    private static final int CARD_X0 = 13;
    private static final int CARD_Y = 34;

    private static final int AMBER = 0xFFF08C32;
    private static final int DIM = 0xFF8C7052;
    private static final int PURPLE = 0xFF5B2A86;
    private static final int PURPLE_HI = 0xFFB070E0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PAPER = 0xFFE9D8B8;
    private static final int[][] OUT = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}};

    private static int page = 0;
    private static boolean runic = true;

    private int left;
    private int top;

    public LaserWheelScreen() {
        super(Component.translatable("screen.tablilla.title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientForgeEvents.holdsTablet(mc.player)) onClose();
    }

    // ------------------------------------------------------------ utilidades
    private static int pages() {
        return (Laser.values().length + PER_PAGE - 1) / PER_PAGE;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int cardX(int i) {
        return left + CARD_X0 + i * (CARD_W + CARD_GAP);
    }

    private int cardY() {
        return top + CARD_Y;
    }

    private Laser selectedLaser() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Laser.PLASMA;
        ItemStack s = mc.player.getMainHandItem();
        if (!s.is(ModItems.TABLILLA.get())) s = mc.player.getOffhandItem();
        return s.is(ModItems.TABLILLA.get()) ? TabletItem.selected(s) : Laser.PLASMA;
    }

    private Component styled(Component c) {
        if (!runic) return c;
        return Component.literal(c.getString().toUpperCase(Locale.ROOT)).withStyle(Style.EMPTY.withFont(RUNAS));
    }

    private void seq(GuiGraphics g, FormattedCharSequence s, int cx, int y, int color, boolean outline) {
        int x = cx - font.width(s) / 2;
        if (outline) {
            for (int[] o : OUT) g.drawString(font, s, x + o[0], y + o[1], PURPLE, false);
        }
        g.drawString(font, s, x, y, color, false);
    }

    private void text(GuiGraphics g, Component c, int cx, int y, int color, boolean outline) {
        seq(g, styled(c).getVisualOrderText(), cx, y, color, outline);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? 0xDD3A2A1C : 0xDD1C140E);
        outline(g, x, y, w, h, hover ? PURPLE_HI : PURPLE);
        text(g, label, x + w / 2, y + (h - 7) / 2, AMBER, true);
    }

    private void send(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    private void turn(int dir) {
        int n = pages();
        page = ((page + dir) % n + n) % n;
    }

    // ------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        RenderSystem.enableBlend();
        g.blit(PANEL, left, top, 0f, 0f, W, H, W, H);

        text(g, Component.translatable("screen.tablilla.title"), left + W / 2, top + 10, AMBER, true);

        Laser[] all = Laser.values();
        Laser selected = selectedLaser();
        Laser hovered = null;

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.length) break;
            Laser l = all[idx];
            int x = cardX(i);
            int y = cardY();
            boolean hover = in(mx, my, x, y, CARD_W, CARD_H);
            if (hover) hovered = l;
            boolean sel = l == selected;

            g.fill(x, y, x + CARD_W, y + CARD_H, hover ? 0xCC30241A : 0xCC17110C);
            outline(g, x, y, CARD_W, CARD_H, sel ? AMBER : (hover ? PURPLE_HI : PURPLE));
            g.blit(l.ready ? l.icon : LOCKED, x + 11, y + 7, 0f, 0f, 32, 32, 32, 32);

            List<FormattedCharSequence> lines = font.split(styled(Component.translatable(l.nameKey())), CARD_W - 4);
            for (int k = 0; k < Math.min(3, lines.size()); k++) {
                seq(g, lines.get(k), x + CARD_W / 2, y + 44 + k * 10, l.ready ? AMBER : DIM, true);
            }
            if (sel) text(g, Component.translatable("screen.tablilla.loaded"), x + CARD_W / 2, y + CARD_H - 11, WHITE, true);
        }

        // descripcion del ataque señalado (o del cargado)
        Laser shown = hovered != null ? hovered : selected;
        text(g, Component.translatable(shown.nameKey()), left + W / 2, top + 118, AMBER, true);
        Component desc = shown.ready ? Component.translatable(shown.descKey())
                : Component.translatable("screen.tablilla.locked_desc");
        List<FormattedCharSequence> d = font.split(styled(desc), W - 44);
        for (int k = 0; k < Math.min(3, d.size()); k++) {
            seq(g, d.get(k), left + W / 2, top + 132 + k * 9, PAPER, false);
        }

        // botones
        button(g, left + 14, top + 170, 70, 18, Component.translatable("screen.tablilla.stop"), mx, my);
        button(g, left + 104, top + 170, 22, 18, Component.literal("<"), mx, my);
        button(g, left + 194, top + 170, 22, 18, Component.literal(">"), mx, my);
        text(g, Component.literal((page + 1) + " / " + pages()), left + 160, top + 175, AMBER, true);
        button(g, left + 226, top + 170, 80, 18, Component.translatable(
                StrikeManager.terrainDamage ? "screen.tablilla.terrain_on" : "screen.tablilla.terrain_off"), mx, my);
        button(g, left + W - 42, top + 8, 30, 14, Component.translatable(
                runic ? "screen.tablilla.font_runic" : "screen.tablilla.font_plain"), mx, my);
    }

    // ------------------------------------------------------------ interaccion
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) {
            turn(1);
            return true;
        }
        if (button != 0) return super.mouseClicked(mx, my, button);

        Laser[] all = Laser.values();
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.length) break;
            if (in(mx, my, cardX(i), cardY(), CARD_W, CARD_H)) {
                Laser l = all[idx];
                if (l.ready) send("tablilla select " + l.id);
                return true;
            }
        }
        if (in(mx, my, left + 14, top + 170, 70, 18)) {
            send("tablilla stop");
            return true;
        }
        if (in(mx, my, left + 104, top + 170, 22, 18)) {
            turn(-1);
            return true;
        }
        if (in(mx, my, left + 194, top + 170, 22, 18)) {
            turn(1);
            return true;
        }
        if (in(mx, my, left + 226, top + 170, 80, 18)) {
            send("tablilla terrain " + (StrikeManager.terrainDamage ? "off" : "on"));
            return true;
        }
        if (in(mx, my, left + W - 42, top + 8, 30, 14)) {
            runic = !runic;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        turn(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (ClientModEvents.WHEEL_KEY.matches(key, scancode)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scancode, mods);
    }
}
