package com.eerietapes;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EerieTapes.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.WATCHER.get(),
                ctx -> new CreepyRenderer<WatcherEntity>(ctx, tex("watcher"), 1.0F));
        e.registerEntityRenderer(ModEntities.MIMIC.get(),
                ctx -> new CreepyRenderer<MimicEntity>(ctx, tex("mimic"), 1.12F));
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(EerieTapes.MODID, "textures/entity/" + name + ".png");
    }
}
