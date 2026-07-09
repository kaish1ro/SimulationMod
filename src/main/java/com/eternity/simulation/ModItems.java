package com.eternity.simulation;

import com.eternity.simulation.items.BlueprintItem;
import com.eternity.simulation.items.CastleKeyItem;
import com.eternity.simulation.items.VoidBlossomMapFragmentItem;
import com.eternity.simulation.items.VoidBlossomMapItem;
import com.eternity.simulation.items.LichMapFragmentItem;
import com.eternity.simulation.items.LichMapItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, SimulationMod.MODID);

    public static final RegistryObject<Item> SIMULATION_SHARD = ITEMS.register(
        "simulation_shard",
        () -> new Item(new Item.Properties().rarity(Rarity.EPIC))
    );

    // ── Осколок сердца (дроп с мировых боссов, 4 шт. -> Heart Container) ──────
    public static final RegistryObject<Item> HEART_SHARD = ITEMS.register(
        "heart_shard",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE))
    );

    // ── Декоративная иконка-лицо Invoker (замена яйца-спавнера в UI) ──────────
    public static final RegistryObject<Item> INVOKER_FACE_ICON = ITEMS.register(
        "invoker_face_icon",
        () -> new Item(new Item.Properties())
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

    // ── Undergarden: фрагменты карты Цветка пустоты (4 штуки, собираются в карту) ──

    public static final RegistryObject<Item> VOID_BLOSSOM_MAP_FRAGMENT_1 = ITEMS.register(
        "void_blossom_map_fragment_1",
        () -> new VoidBlossomMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> VOID_BLOSSOM_MAP_FRAGMENT_2 = ITEMS.register(
        "void_blossom_map_fragment_2",
        () -> new VoidBlossomMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> VOID_BLOSSOM_MAP_FRAGMENT_3 = ITEMS.register(
        "void_blossom_map_fragment_3",
        () -> new VoidBlossomMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> VOID_BLOSSOM_MAP_FRAGMENT_4 = ITEMS.register(
        "void_blossom_map_fragment_4",
        () -> new VoidBlossomMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final RegistryObject<Item> VOID_BLOSSOM_MAP = ITEMS.register(
        "void_blossom_map",
        () -> new VoidBlossomMapItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    // ── Iceika: фрагменты карты, ведущей к Личу (4 штуки, собираются в карту) ──
    // Пока без функционала — только регистрация + крафт (см. lich_map.json).
    // Текстуры и источники добычи фрагментов — TODO.

    public static final RegistryObject<Item> LICH_MAP_FRAGMENT_1 = ITEMS.register(
        "lich_map_fragment_1",
        () -> new LichMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> LICH_MAP_FRAGMENT_2 = ITEMS.register(
        "lich_map_fragment_2",
        () -> new LichMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> LICH_MAP_FRAGMENT_3 = ITEMS.register(
        "lich_map_fragment_3",
        () -> new LichMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> LICH_MAP_FRAGMENT_4 = ITEMS.register(
        "lich_map_fragment_4",
        () -> new LichMapFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final RegistryObject<Item> LICH_MAP = ITEMS.register(
        "lich_map",
        () -> new LichMapItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
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

    public static final RegistryObject<Item> DIMENSIONAL_UPGRADE_SMITHING_TEMPLATE = ITEMS.register(
        "dimensional_upgrade_smithing_template",
        () -> new SmithingTemplateItem(
            Component.translatable("item.simulation.smithing_template.dimensional_upgrade.applies_to"),
            Component.translatable("item.simulation.smithing_template.dimensional_upgrade.ingredients"),
            Component.translatable("upgrade.simulation.dimensional_upgrade"),
            Component.translatable("item.simulation.smithing_template.dimensional_upgrade.base_slot_description"),
            Component.translatable("item.simulation.smithing_template.dimensional_upgrade.additions_slot_description"),
            List.of(new ResourceLocation("minecraft", "item/empty_slot_pickaxe")),
            List.of(new ResourceLocation("minecraft", "item/empty_slot_ender_eye"))
        )
    );

    // ── Ведро сверхгорячей лавы ───────────────────────────────────────────────
    // Своя жидкость (ModFluids.SUPERHOT_LAVA_SOURCE) — физика как у лавы, своя
    // голубая текстура. Плавящая способность — отдельная задача вместе с крепким льдом.
    public static final RegistryObject<Item> SUPERHOT_LAVA_BUCKET = ITEMS.register(
        "superhot_lava_bucket",
        () -> new BucketItem(ModFluids.SUPERHOT_LAVA_SOURCE, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1).rarity(Rarity.RARE))
    );
}
