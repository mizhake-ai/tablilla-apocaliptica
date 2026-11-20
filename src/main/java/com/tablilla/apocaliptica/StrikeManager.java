package com.tablilla.apocaliptica;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Solo hay un ataque a la vez, igual que en el mod original. */
@Mod.EventBusSubscriber(modid = TablillaMod.MOD_ID)
public final class StrikeManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Strike active;

    /** Si es false, los ataques solo hacen luz y sonido (no tocan el terreno). */
    public static boolean terrainDamage = false;

    private StrikeManager() {}

    public static boolean isBusy() {
        return active != null;
    }

    public static void start(Strike strike) {
        active = strike;
    }

    public static void stop() {
        active = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || active == null) return;
        try {
            if (active.tick()) active = null;
        } catch (Exception e) {
            LOGGER.error("El ataque fallo y se cancelo", e);
            active = null;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        active = null;
    }
}
