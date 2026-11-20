package com.tablilla.apocaliptica;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmlmod.javafml.FMLJavaModLoadingContext;

@Mod(TablillaMod.MOD_ID)
public class TablillaMod {
    public static final String MOD_ID = "tablilla_apocaliptica";

    public TablillaMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(this::onCommands);
    }

    private void onCommands(RegisterCommandsEvent event) {
        TablillaCommand.register(event.getDispatcher());
    }
}
