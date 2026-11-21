package com.tablilla.apocaliptica;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sonidos de los ataques (tomados del mod original Orbital Laser, licencia MIT). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TablillaMod.MOD_ID);

    private static final Map<String, RegistryObject<SoundEvent>> MAP = new HashMap<>();

    private static final String[] NAMES = {
            "plasma.charge",
            "plasma.lockon",
            "plasma.fire",
            "plasma.beam",
            "plasma.rumble",
            "plasma.crackle",
            "plasma.collapse",
            "plasma.shutdown",
            "godly.lockon",
            "godly.charge",
            "godly.fire",
            "godly.beam",
            "godly.rumble",
            "godly.accent",
            "godly.shutdown",
            "blackhole.lockon",
            "blackhole.charge",
            "blackhole.fire",
            "blackhole.beam",
            "blackhole.rumble",
            "blackhole.accent",
            "blackhole.shutdown",
            "meteor.lockon",
            "meteor.charge",
            "meteor.fire",
            "meteor.storm",
            "meteor.rumble",
            "meteor.whoosh1",
            "meteor.whoosh2",
            "meteor.hit1",
            "meteor.hit2",
            "meteor.hit3",
            "meteor.hit_far1",
            "meteor.hit_far2",
            "meteor.hit_far3",
            "meteor.approach",
            "meteor.impact",
            "meteor.shutdown",
            "mothership.arrival",
            "mothership.boom",
            "mothership.hum",
            "mothership.charge",
            "mothership.fire",
            "mothership.beam1",
            "mothership.beam2",
            "mothership.sweep1",
            "mothership.sweep2",
            "mothership.rumble",
            "mothership.cutoff",
            "mothership.depart"
    };

    static {
        for (String n : NAMES) {
            MAP.put(n, SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TablillaMod.MOD_ID, n))));
        }
    }

    private ModSounds() {}

    public static SoundEvent get(String name) {
        RegistryObject<SoundEvent> o = MAP.get(name);
        return o == null ? null : o.get();
    }
}
