package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Todos los ataques de la tablilla. Los que tienen ready=false salen en el menu como "proximamente". */
public enum Laser {
    PLASMA("plasma", true),
    GODLY("godly", false),
    BLACKHOLE("blackhole", false),
    METEOR("meteor", false),
    MOTHERSHIP("mothership", false),
    DOWNFALL("downfall", false),
    PLANETKILLER("planetkiller", false),
    CRYSTALBLOOM("crystalbloom", false),
    SINKHOLE("sinkhole", false),
    PAYLOAD("payload", false),
    NUKE("nuke", false),
    ROD("rod", false),
    SUPERCELL("supercell", false),
    HURRICANE("hurricane", false),
    TSUNAMI("tsunami", false),
    FOLD("fold", false),
    SUPERVOLCANO("supervolcano", false),
    SKYBREAK("skybreak", false),
    CARPETBOMB("carpetbomb", false),
    WORLDEATER("worldeater", false),
    CONVERGENCE("convergence", false),
    EARTHQUAKE("earthquake", false),
    SIXSTARS("sixstars", false),
    COLOSSI("colossi", false);

    public final String id;
    public final boolean ready;
    public final ResourceLocation icon;

    Laser(String id, boolean ready) {
        this.id = id;
        this.ready = ready;
        this.icon = new ResourceLocation(TablillaMod.MOD_ID, "textures/gui/icon_" + id + ".png");
    }

    public String nameKey() {
        return "laser." + TablillaMod.MOD_ID + "." + id;
    }

    public String descKey() {
        return nameKey() + ".desc";
    }

    public static Laser byId(String id) {
        for (Laser l : values()) {
            if (l.id.equals(id)) return l;
        }
        return null;
    }

    public static List<String> readyIds() {
        List<String> out = new ArrayList<>();
        for (Laser l : values()) {
            if (l.ready) out.add(l.id);
        }
        return out;
    }

    /** Crea el ataque, o null si todavia no esta hecho. */
    public Strike create(ServerLevel level, BlockPos pos) {
        switch (this) {
            case PLASMA:
                return new PlasmaStrike(level, pos);
            default:
                return null;
        }
    }
}
