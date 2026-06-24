package com.eternity.simulation.castle;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Маппинг строковых id мобов из DATA-маркеров castle.nbt/labyrinth.nbt (поле {@code mob=...})
 * на реальные {@link EntityType} (включая модовые) + донастройка (HP, экипировка) при спавне.
 */
public final class CastleMobRegistry {

    private record Entry(ResourceLocation entityId, Double maxHealth, Consumer<LivingEntity> postSpawn) {}

    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    static {
        register("skeleton", "minecraft:skeleton", 30.0, CastleMobRegistry::equipIronSkeleton);
        register("soul_skeleton", "block_factorys_bosses:soul_skeleton", 35.0, null);
        register("soul_knight_wither_skeleton", "block_factorys_bosses:soul_knight_wither_skeleton", 40.0, null);
        register("jungle_skeleton", "betternether:jungle_skeleton", 30.0, null);
        register("undead_paladin", "threateningly_mobs:undead_paladin", 100.0, null);
        register("king_spider", "twilightforest:king_spider", null, null);
        register("hedge_spider", "twilightforest:hedge_spider", null, null);
        // Обычный stray: 30 HP, лук выдаётся через finalizeSpawn в CastleSpawnManager
        // (там же подключается ИИ-стрельбы из лука).
        register("stray", "minecraft:stray", 30.0, null);

        // "Хранитель тайн" — кастомный страж синей башни (выпадает ключ через keyid маркера).
        register("stray_boss", "minecraft:stray", 120.0, CastleMobRegistry::equipStrayBoss);

        // Главный босс 2-го этажа (CastleBossFightTask). Базовое HP 200 — далее
        // автоматически масштабируется ModEvents#onBossJoinLevel (тег scaled_bosses).
        register("underworld_knight_boss", "block_factorys_bosses:underworld_knight", 200.0, null);
    }

    private CastleMobRegistry() {}

    private static void register(String markerId, String entityId, Double maxHealth, Consumer<LivingEntity> postSpawn) {
        ENTRIES.put(markerId, new Entry(new ResourceLocation(entityId), maxHealth, postSpawn));
    }

    /** @return тип сущности для данного маркерного id, или {@code null} если id неизвестен/мод отсутствует. */
    public static EntityType<?> getEntityType(String markerId) {
        Entry entry = ENTRIES.get(markerId);
        if (entry == null) return null;
        return ForgeRegistries.ENTITY_TYPES.getValue(entry.entityId());
    }

    /** Донастройка после спавна (HP, экипировка и т.п.), если требуется для данного id. */
    public static void applyPostSpawn(String markerId, LivingEntity entity) {
        Entry entry = ENTRIES.get(markerId);
        if (entry == null) return;

        if (entry.maxHealth() != null) {
            AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(entry.maxHealth());
            }
            entity.setHealth(entry.maxHealth().floatValue());
        }

        if (entry.postSpawn() != null) {
            entry.postSpawn().accept(entity);
        }
    }

    /**
     * "Хранитель тайн": кастомное голубое имя, 2 алмазных меча (основная и доп. рука),
     * замедляет противников при атаке (см. {@code ModEvents#onStrayBossAttack}).
     */
    private static void equipStrayBoss(LivingEntity entity) {
        entity.setCustomName(Component.literal("§b§lХранитель тайн"));
        entity.setCustomNameVisible(true);
        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        entity.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND_SWORD));
        entity.addTag("castle_stray_boss");
    }

    private static void equipIronSkeleton(LivingEntity entity) {
        entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        entity.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        entity.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }
}
