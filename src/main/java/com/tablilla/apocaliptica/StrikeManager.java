package com.tablilla.apocaliptica;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Solo hay un ataque a la vez, igual que en el mod original. */
@Mod.EventBusSubscriber(modid = TablillaMod.MOD_ID)
public final class StrikeManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Strike active;

    /** Si es false, los ataques solo hacen luz y sonido (no tocan el terreno ni a los seres vivos). */
    public static boolean terrainDamage = true;

    /** Multiplicador del tamano de la destruccion (radios y alturas). */
    public static double scale = 1.0;

    private StrikeManager() {}

    public static boolean isBusy() {
        return active != null;
    }

    public static void start(Strike strike) {
        active = strike;
        strike.start();
    }

    public static void stop() {
        if (active != null) {
            try {
                active.cancel();
            } catch (Exception e) {
                LOGGER.error("Error al cancelar el ataque", e);
            }
        }
        active = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || active == null) return;
        try {
            if (active.tick()) active = null;
        } catch (Exception e) {
            LOGGER.error("El ataque fallo y se cancelo", e);
            stop();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        active = null;
    }
}
