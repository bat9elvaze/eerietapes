package com.eerietapes;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EerieTapes.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {
    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.WATCHER.get(), WatcherEntity.createAttributes().build());
        e.put(ModEntities.MIMIC.get(), MimicEntity.createAttributes().build());
    }
}
