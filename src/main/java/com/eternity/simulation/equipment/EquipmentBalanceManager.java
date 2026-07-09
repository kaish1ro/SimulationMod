package com.eternity.simulation.equipment;

import com.eternity.simulation.SimulationMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Балансировочные статы и эффекты снаряжения.
 *
 * <p>Статы применяются через {@link ItemAttributeModifierEvent} — отображаются
 * в тултипе предмета в стандартном ванильном формате автоматически.
 *
 * <p>Эффекты сетов и боевые пассивки — через {@link TickEvent.PlayerTickEvent}
 * и {@link LivingHurtEvent}.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EquipmentBalanceManager {

    // ── UUID модификаторов ────────────────────────────────────────────────────

    private static final UUID WEAPON_UUID           = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID ARMOR_UUID            = UUID.fromString("b1c2d3e4-f5a6-7890-bcde-f01234567891");
    private static final UUID TOUGHNESS_UUID        = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-012345678902");
    private static final UUID KNOCKBACK_UUID        = UUID.fromString("d1e2f3a4-b5c6-7890-def0-123456789013");
    /** Корректирующий модификатор брони Cataclysm (только вниз до незеритового уровня). */
    private static final UUID CATACLYSM_ARMOR_UUID   = UUID.fromString("a2b3c4d5-e6f7-8901-bcde-f12345678906");
    private static final UUID CATACLYSM_TOUGH_UUID   = UUID.fromString("b3c4d5e6-f7a8-9012-cdef-012345678907");
    private static final UUID CATACLYSM_KB_UUID      = UUID.fromString("c4d5e6f7-a8b9-0123-def0-123456789018");

    /**
     * Железно-тирные крафтовые предметы Cataclysm — оставляем оригинальные статы,
     * не применяем stage-cap. Black Steel = ~6 урона, аналог железа.
     */
    private static final Set<String> CATACLYSM_IRON_TIER = Set.of(
        "cataclysm:black_steel_sword",
        "cataclysm:black_steel_axe",
        "cataclysm:black_steel_pickaxe",
        "cataclysm:black_steel_shovel"
    );

    // ── Voidscape cooldown ────────────────────────────────────────────────────

    private static final Map<UUID, Long> voidCooldown = new HashMap<>();
    private static final long VOID_CD_TICKS = 1200L;

    // ── Сеты для эффектов ─────────────────────────────────────────────────────

    private static final Set<String> VOIDSCAPE_ARMOR_PIECES = Set.of(
            "voidscape:helmet", "voidscape:boots");

    private static final Set<String> GRAVITITE_ARMOR = Set.of(
            "aether:gravitite_helmet", "aether:gravitite_chestplate",
            "aether:gravitite_leggings", "aether:gravitite_boots");

    private static final Set<String> CHAROITE_ARMOR = Set.of(
            "blue_skies:charoite_helmet", "blue_skies:charoite_chestplate",
            "blue_skies:charoite_leggings", "blue_skies:charoite_boots");

    private static final Set<String> DIOPSIDE_ARMOR = Set.of(
            "blue_skies:diopside_helmet", "blue_skies:diopside_chestplate",
            "blue_skies:diopside_leggings", "blue_skies:diopside_boots");

    private static final Set<String> HORIZONITE_ARMOR = Set.of(
            "blue_skies:horizonite_helmet", "blue_skies:horizonite_chestplate",
            "blue_skies:horizonite_leggings", "blue_skies:horizonite_boots");

    private static final Set<String> UTHERIUM_ARMOR = Set.of(
            "undergarden:utherium_helmet", "undergarden:utherium_chestplate",
            "undergarden:utherium_leggings", "undergarden:utherium_boots");

    // ── СТАТЫ: ItemAttributeModifierEvent ─────────────────────────────────────
    //
    // Срабатывает при рендере тултипа и при боевых расчётах.
    // Добавленные модификаторы видны в тултипе в ванильном формате автоматически.

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;

        String ns   = id.getNamespace();
        String path = id.getPath();
        String full = id.toString();
        EquipmentSlot slot = event.getSlotType();

        // ── Оружие / инструменты (слот: главная рука) ─────────────────────────
        if (slot == EquipmentSlot.MAINHAND && isWeaponOrTool(path)) {
            double bonus = switch (ns) {
                case "undergarden" -> 3.0;
                case "aether"      -> 4.0;
                case "blue_skies"  -> 3.0;
                default            -> 0.0;
            };
            if (bonus > 0) {
                event.addModifier(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(WEAPON_UUID, "sim_weapon_bonus",
                                bonus, AttributeModifier.Operation.ADDITION));
            }
        }

        // Урон оружия Cataclysm теперь считается общей NBT-системой апгрейдов в balance.WeaponBalanceEvents.

        // ── Cataclysm броня: кап до незеритового уровня (только вниз) ───────────
        // Незерит: шлем/сапоги=3, нагрудник=8, поножи=6, вязкость=3, кб=0.1 за предмет.
        // Correction добавляется только если currentSum > cap — никогда не баффаем слабую броню.
        EquipmentSlot itemArmorSlot = getArmorSlot(path);
        if ("cataclysm".equals(ns) && itemArmorSlot != null && itemArmorSlot == slot) {
            double armorCap = switch (itemArmorSlot) {
                case HEAD  -> 3.0;
                case CHEST -> 8.0;
                case LEGS  -> 6.0;
                case FEET  -> 3.0;
                default    -> -1.0;
            };
            if (armorCap > 0) {
                capAttribute(event, Attributes.ARMOR,               armorCap, CATACLYSM_ARMOR_UUID, "cataclysm_armor_cap");
                capAttribute(event, Attributes.ARMOR_TOUGHNESS,     3.0,      CATACLYSM_TOUGH_UUID, "cataclysm_tough_cap");
                capAttribute(event, Attributes.KNOCKBACK_RESISTANCE, 0.1,     CATACLYSM_KB_UUID,    "cataclysm_kb_cap");
            }
        }

        // ── Броня других измерений: бонусные статы ────────────────────────────
        if (itemArmorSlot != null && itemArmorSlot == slot) {
            double armorBonus    = 0;
            double toughBonus    = 0;
            double knockbackBonus = 0;

            switch (ns) {
                case "undergarden" -> armorBonus = 2.0;
                case "aether"      -> armorBonus = 3.0;
                case "blue_skies"  -> armorBonus = 3.0;
            }
            if (VOIDSCAPE_ARMOR_PIECES.contains(full)) {
                armorBonus     = 1.0;
                toughBonus     = 3.0;
                knockbackBonus = 0.05;
            }

            if (armorBonus > 0)
                event.addModifier(Attributes.ARMOR,
                        new AttributeModifier(ARMOR_UUID, "sim_armor_bonus",
                                armorBonus, AttributeModifier.Operation.ADDITION));
            if (toughBonus > 0)
                event.addModifier(Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(TOUGHNESS_UUID, "sim_toughness_bonus",
                                toughBonus, AttributeModifier.Operation.ADDITION));
            if (knockbackBonus > 0)
                event.addModifier(Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(KNOCKBACK_UUID, "sim_knockback_bonus",
                                knockbackBonus, AttributeModifier.Operation.ADDITION));
        }
    }

    // ── ЭФФЕКТЫ СЕТОВ: PlayerTickEvent ────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) player.level();
        applySetEffects(player, level);
        tickVoidTrigger(player, level);
    }

    private static void applySetEffects(ServerPlayer player, ServerLevel level) {
        // Utherium: Resistance I + Regen I в темноте / под землёй
        if (isFullSet(player, UTHERIUM_ARMOR)) {
            boolean dark = level.getBrightness(LightLayer.BLOCK, player.blockPosition()) < 8;
            if (dark || player.getY() < 60) {
                applyEffect(player, MobEffects.DAMAGE_RESISTANCE, 0, 60);
                applyEffect(player, MobEffects.REGENERATION,      0, 60);
            } else {
                removeEffect(player, MobEffects.DAMAGE_RESISTANCE);
                removeEffect(player, MobEffects.REGENERATION);
            }
        }

        // Gravitite: Speed II + Jump Boost II
        if (isFullSet(player, GRAVITITE_ARMOR)) {
            applyEffect(player, MobEffects.MOVEMENT_SPEED, 1, 60);
            applyEffect(player, MobEffects.JUMP,           1, 60);
        }

        // Charoite / Diopside / Horizonite: Haste II
        if (isFullSet(player, CHAROITE_ARMOR) || isFullSet(player, DIOPSIDE_ARMOR)
                || isFullSet(player, HORIZONITE_ARMOR)) {
            applyEffect(player, MobEffects.DIG_SPEED, 1, 60);
        }

        // Voidscape: Resistance I + снимаем слепоту
        if (isPartialSet(player, VOIDSCAPE_ARMOR_PIECES, 2)) {
            applyEffect(player, MobEffects.DAMAGE_RESISTANCE, 0, 60);
            if (player.hasEffect(MobEffects.BLINDNESS)) player.removeEffect(MobEffects.BLINDNESS);
        }
    }

    private static void tickVoidTrigger(ServerPlayer player, ServerLevel level) {
        if (!isPartialSet(player, VOIDSCAPE_ARMOR_PIECES, 2)) return;
        if (player.getHealth() / player.getMaxHealth() > 0.3f) return;

        long now  = level.getGameTime();
        Long last = voidCooldown.get(player.getUUID());
        if (last != null && now - last < VOID_CD_TICKS) return;

        voidCooldown.put(player.getUUID(), now);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,   100, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 2, false, false));
    }

    // ── БОЕВЫЕ ПАССИВКИ ───────────────────────────────────────────────────────

    /** Charoite / Diopside / Horizonite: +1.5 чистого урона в ближнем бою. */
    @SubscribeEvent
    public static void onMeleeDamageBonus(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (event.getSource().getDirectEntity() != attacker) return; // только ближний бой

        if (isFullSet(attacker, CHAROITE_ARMOR) || isFullSet(attacker, DIOPSIDE_ARMOR)
                || isFullSet(attacker, HORIZONITE_ARMOR)) {
            event.setAmount(event.getAmount() + 1.5f);
        }
    }

    /** Gravitite: +15% урона в прыжке. */
    @SubscribeEvent
    public static void onJumpDamageBonus(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!isFullSet(attacker, GRAVITITE_ARMOR)) return;
        if (attacker.onGround()) return;
        if (event.getSource().getDirectEntity() != attacker) return;

        event.setAmount(event.getAmount() * 1.15f);
    }

    /**
     * Меч из чёрной стали: 20% шанс восстановить 1 HP при попадании в ближнем бою.
     * Срабатывает только на реальных существах (не на блоках и т.д.).
     */
    @SubscribeEvent
    public static void onBlackSteelSwordHeal(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (event.getSource().getDirectEntity() != attacker) return; // только ближний бой

        ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(attacker.getMainHandItem().getItem());
        if (heldId == null || !"cataclysm:black_steel_sword".equals(heldId.toString())) return;

        if (attacker.getRandom().nextFloat() < 0.20f) {
            attacker.heal(2.0f); // 2 HP = 1 сердце
        }
    }

    /** Gravitite: иммунитет к урону от падения. */
    @SubscribeEvent
    public static void onFallDamage(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isFullSet(player, GRAVITITE_ARMOR)) event.setCanceled(true);
    }

    // ── ТУЛТИП: подсказки по эффектам сетов ──────────────────────────────────
    //
    // Статы теперь показываются автоматически через ItemAttributeModifierEvent.
    // Здесь добавляем только подсказку об эффекте полного сета.

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;

        String ns   = id.getNamespace();
        String path = id.getPath();
        String full = id.toString();

        String hint = null;

        if (isArmorPiece(path)) {
            if ("undergarden".equals(ns) && path.startsWith("utherium_"))
                hint = "Сет: Resistance I + Regen I (в темноте / под землёй)";
            else if (GRAVITITE_ARMOR.contains(full))
                hint = "Сет: Скорость II + Прыжок II | Нет урона от падения";
            else if (CHAROITE_ARMOR.contains(full) || DIOPSIDE_ARMOR.contains(full) || HORIZONITE_ARMOR.contains(full))
                hint = "Сет: Ускорение II | +1.5 урона в ближнем бою";
            else if (VOIDSCAPE_ARMOR_PIECES.contains(full))
                hint = "Сет: Resistance I | При HP<30%: Невидимость + Скорость III (кд 60с)";
        }

        if (hint != null) {
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.literal("Бонус полного сета брони:").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            event.getToolTip().add(Component.literal("◆ " + hint).withStyle(ChatFormatting.DARK_AQUA));
        }

        // Пассивка меча из чёрной стали
        if ("cataclysm:black_steel_sword".equals(full)) {
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.literal("Пассивка:").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            event.getToolTip().add(Component.literal("◆ При атаке: 20% шанс восстановить 1 сердце").withStyle(ChatFormatting.DARK_RED));
        }
    }

    // ── Вспомогательные ───────────────────────────────────────────────────────

    private static boolean isFullSet(ServerPlayer player, Set<String> set) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot s : slots) {
            ResourceLocation pid = ForgeRegistries.ITEMS.getKey(player.getItemBySlot(s).getItem());
            if (pid == null || !set.contains(pid.toString())) return false;
        }
        return true;
    }

    private static boolean isPartialSet(ServerPlayer player, Set<String> pieces, int required) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int count = 0;
        for (EquipmentSlot s : slots) {
            ResourceLocation pid = ForgeRegistries.ITEMS.getKey(player.getItemBySlot(s).getItem());
            if (pid != null && pieces.contains(pid.toString())) count++;
        }
        return count >= required;
    }

    private static void applyEffect(ServerPlayer p, net.minecraft.world.effect.MobEffect e, int amp, int dur) {
        var ex = p.getEffect(e);
        if (ex != null && ex.getAmplifier() == amp && ex.getDuration() > 20) return;
        p.addEffect(new MobEffectInstance(e, dur, amp, false, false));
    }

    private static void removeEffect(ServerPlayer p, net.minecraft.world.effect.MobEffect e) {
        if (p.hasEffect(e)) p.removeEffect(e);
    }

    /** Возвращает слот по суффиксу пути предмета, или null если не броня. */
    private static EquipmentSlot getArmorSlot(String path) {
        if (path.endsWith("_helmet")     || path.equals("helmet"))  return EquipmentSlot.HEAD;
        if (path.endsWith("_chestplate"))                            return EquipmentSlot.CHEST;
        if (path.endsWith("_leggings"))                              return EquipmentSlot.LEGS;
        if (path.endsWith("_boots")      || path.equals("boots"))   return EquipmentSlot.FEET;
        return null;
    }

    private static boolean isWeaponOrTool(String path) {
        return path.endsWith("_sword") || path.endsWith("_axe")   || path.endsWith("_pickaxe")
            || path.endsWith("_shovel") || path.endsWith("_hoe")  || path.endsWith("_spear")
            || path.equals("sword")     || path.equals("axe")     || path.equals("pickaxe")
            || path.equals("shovel")    || path.equals("hoe")     || path.equals("xbow")
            || path.equals("bow")       || path.endsWith("_bow")  || path.endsWith("_staff")
            || path.endsWith("_blade")  || path.endsWith("_wand") || path.endsWith("_crossbow");
    }

    /**
     * Добавляет корректирующий модификатор чтобы снизить атрибут до cap.
     * Если текущая сумма уже ≤ cap — ничего не делает (не баффает).
     */
    private static void capAttribute(ItemAttributeModifierEvent event,
                                     net.minecraft.world.entity.ai.attributes.Attribute attr,
                                     double cap, UUID uuid, String name) {
        double current = event.getModifiers().get(attr).stream()
                .filter(m -> m.getOperation() == AttributeModifier.Operation.ADDITION)
                .mapToDouble(AttributeModifier::getAmount)
                .sum();
        double correction = cap - current;
        if (correction < -0.001) { // только если превышает кап
            event.addModifier(attr, new AttributeModifier(uuid, name,
                    correction, AttributeModifier.Operation.ADDITION));
        }
    }

    private static boolean isArmorPiece(String path) {
        return path.endsWith("_helmet")     || path.endsWith("_chestplate")
            || path.endsWith("_leggings")   || path.endsWith("_boots")
            || path.equals("helmet")        || path.equals("boots");
    }
}
