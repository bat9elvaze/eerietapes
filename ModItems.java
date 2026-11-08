package com.eerietapes;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, EerieTapes.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EerieTapes.MODID);

    public static final RegistryObject<Item> TAPE_HOUSE = ITEMS.register("tape_house",
            () -> new TapeItem(TapeType.HOUSE, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> TAPE_FOREST = ITEMS.register("tape_forest",
            () -> new TapeItem(TapeType.FOREST, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> WATCHER_EGG = ITEMS.register("watcher_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WATCHER, 0x1A1A1A, 0xE0E0DA, new Item.Properties()));
    public static final RegistryObject<Item> MIMIC_EGG = ITEMS.register("mimic_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.MIMIC, 0x3C2828, 0x786458, new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eerietapes"))
                    .icon(() -> new ItemStack(TAPE_HOUSE.get()))
                    .displayItems((params, out) -> {
                        out.accept(TAPE_HOUSE.get());
                        out.accept(TAPE_FOREST.get());
                        out.accept(WATCHER_EGG.get());
                        out.accept(MIMIC_EGG.get());
                    }).build());
}
