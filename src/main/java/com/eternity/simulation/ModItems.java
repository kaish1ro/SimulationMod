package com.eternity.simulation;

import com.eternity.simulation.items.BlueprintItem;
import com.eternity.simulation.items.CastleKeyItem;
import com.eternity.simulation.items.FeroxMapFragmentItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, SimulationMod.MODID);

    public static final RegistryObject<Item> SIMULATION_SHARD = ITEMS.register(
        "simulation_shard",
        () -> new Item(new Item.Properties().rarity(Rarity.EPIC))
    );

    // ── Осколки пространства (дроп из разломов) ───────────────────────────────
    public static final RegistryObject<Item> SPACE_SHARD_RED    = ITEMS.register("space_shard_red",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_SHARD_PURPLE = ITEMS.register("space_shard_purple",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_SHARD_BLUE   = ITEMS.register("space_shard_blue",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_SHARD_ELITE  = ITEMS.register("space_shard_elite",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    // ── Пыль пространства (получается из 9 осколков) ─────────────────────────
    public static final RegistryObject<Item> SPACE_DUST_RED    = ITEMS.register("space_dust_red",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_DUST_BLUE   = ITEMS.register("space_dust_blue",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_DUST_PURPLE = ITEMS.register("space_dust_purple",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_DUST_ORANGE = ITEMS.register("space_dust_orange",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ── Кристалл пространства (8 пыли + аметист по середине) ─────────────────
    public static final RegistryObject<Item> SPACE_CRYSTAL_RED    = ITEMS.register("space_crystal_red",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_CRYSTAL_BLUE   = ITEMS.register("space_crystal_blue",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_CRYSTAL_PURPLE = ITEMS.register("space_crystal_purple",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPACE_CRYSTAL_ORANGE = ITEMS.register("space_crystal_orange",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ── Кластер пространства (3 кристалла одного цвета) ──────────────────────
    public static final RegistryObject<Item> SPACE_CLUSTER_RED    = ITEMS.register("space_cluster_red",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SPACE_CLUSTER_BLUE   = ITEMS.register("space_cluster_blue",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SPACE_CLUSTER_PURPLE = ITEMS.register("space_cluster_purple",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SPACE_CLUSTER_ORANGE = ITEMS.register("space_cluster_orange",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    // ── Большой кристалл пространства (4 кластера по одному каждого цвета) ───
    public static final RegistryObject<Item> SPACE_CRYSTAL_LARGE = ITEMS.register("space_crystal_large",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));

    // ── Фрагмент пространства (большой кристалл + 4 звезды незера) ───────────
    public static final RegistryObject<Item> SPACE_FRAGMENT = ITEMS.register("space_fragment",
        () -> new Item(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));

    // ── Пробуждённый вечный кристалл (активатор портала TF) ──────────────────
    public static final RegistryObject<Item> AWAKENED_ETERNAL_CRYSTAL = ITEMS.register(
        "awakened_eternal_crystal",
        () -> new Item(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));

    public static final RegistryObject<Item> BLUEPRINT_GROUP1 = ITEMS.register(
        "blueprint_group1",
        () -> new BlueprintItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), 1)
    );

    public static final RegistryObject<Item> BLUEPRINT_GROUP2 = ITEMS.register(
        "blueprint_group2",
        () -> new BlueprintItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), 2)
    );

    public static final RegistryObject<Item> BLUEPRINT_GROUP3 = ITEMS.register(
        "blueprint_group3",
        () -> new BlueprintItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 3)
    );

    // ── Undergarden: фрагменты карты Ферокса (4 штуки, собираются в карту) ──

    public static final RegistryObject<Item> FEROX_MAP_FRAGMENT_1 = ITEMS.register(
        "ferox_map_fragment_1",
        () -> new FeroxMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> FEROX_MAP_FRAGMENT_2 = ITEMS.register(
        "ferox_map_fragment_2",
        () -> new FeroxMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> FEROX_MAP_FRAGMENT_3 = ITEMS.register(
        "ferox_map_fragment_3",
        () -> new FeroxMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> FEROX_MAP_FRAGMENT_4 = ITEMS.register(
        "ferox_map_fragment_4",
        () -> new FeroxMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    // ── Ключ от замка TF ──────────────────────────────────────────────────────

    /**
     * Ключ от финального замка Twilight Forest.
     * Используется на блоке {@link com.eternity.simulation.blocks.CastleKeyDoorBlock}
     * для открытия дверного проёма. Потребляется при использовании.
     * Нестакуется — один ключ = один проём.
     */
    public static final RegistryObject<Item> CASTLE_KEY = ITEMS.register(
        "castle_key",
        () -> new CastleKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
}
