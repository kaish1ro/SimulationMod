package com.eternity.simulation.balance;

import com.eternity.simulation.SimulationMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Баланс способностей Cataclysm — см. {@code equipment_balance_v2.xlsx} (декомпил
 * подтвердил: {@code Ancient Spear}, {@code Cursed Bow}, {@code Tidal Claws},
 * {@code Gauntlet of Guard} и {@code Meat Shredder} не вызывают
 * {@code ItemCooldowns.addCooldown} вообще — единственная найденная в байткоде
 * проверка кулдауна у Gauntlet of Guard/Meat Shredder на деле проверяет ЩИТ в
 * другой руке, не саму способность).
 *
 * <p>Урон предметов капается напрямую в {@code cataclysm-common.toml}
 * (`attack_damage`) — у Cataclysm, в отличие от Legendary Monsters, конфиг хранит
 * реальные абсолютные числа, мексины/события для этого не нужны.
 *
 * <p>Два предмета — "пила" ({@code meat_shredder}, урон по фронту в упор,
 * игнорирует i-frames) и "лазерная пушка" ({@code laser_gatling}, useDuration =
 * Integer.MAX_VALUE — держать можно буквально бесконечно, кулдаун по коду
 * применяется только при отпускании) — получают отдельный механизм "нагрева":
 * после {@link #OVERHEAT_TICKS} тиков непрерывного удержания оружие само
 * прерывает использование и уходит в кулдаун, вместо разового кулдауна на
 * старте применения.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public final class CataclysmBalanceEvents {

    private CataclysmBalanceEvents() {}

    private static final String CATACLYSM_NAMESPACE = "cataclysm";

    /** Предметы без единого кулдауна в оригинальном коде — простой кулдаун на старт применения. */
    private static final Map<String, Integer> NO_COOLDOWN_WEAPONS = Map.of(
        "ancient_spear", 80,      // 4с — сопоставимо с infernal_forge/gauntlet_of_bulwark
        "cursed_bow", 80,
        "tidal_claws", 80,
        "gauntlet_of_guard", 100  // немного дольше — способность массового притяжения
    );

    /** "Пила"/"лазерная пушка" — держатся неограниченно долго, нужен нагрев, не разовый кулдаун. */
    private static final Set<String> HEATING_WEAPONS = Set.of("meat_shredder", "laser_gatling");

    private static final int OVERHEAT_TICKS = 60;    // 3с непрерывного удержания до перегрева
    private static final int OVERHEAT_COOLDOWN = 100; // 5с кулдауна после перегрева
    private static final int MIN_USE_COOLDOWN = 30;   // 1.5с — минимальный кулдаун даже на быстрый тап-отпуск

    private static final Map<UUID, Integer> heatTicks = new ConcurrentHashMap<>();

    /**
     * Способности, чей урон зашит в Java как отношение к атрибуту attack_damage предмета
     * (например Meat Shredder: OriginDamage/8.5), а не хранится в конфиге отдельным числом —
     * их нельзя капнуть правкой toml, поэтому режем на месте нанесения урона по msg_id урона
     * (см. data/cataclysm/damage_type/*.json). Применяется только когда источник — игрок,
     * чтобы не трогать те же способности у боссов-мобов.
     */
    private static final Set<String> ABILITY_DAMAGE_MSG_IDS = Set.of(
        "cataclysm.shredder",      // Meat Shredder — фронтальный AOE, игнорирует i-frames
        "cataclysm.storm_bringer", // Ceraunus — бросок/волны веером
        "cataclysm.sword_dance",   // Soul Render — рывок Rush of Render
        "cataclysm.flame_strike",
        "cataclysm.penetrate"
    );
    private static final float ABILITY_DAMAGE_MULT = 0.65f;

    // ── Простой кулдаун на старт применения для "дырявых" предметов ───────────

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !CATACLYSM_NAMESPACE.equals(id.getNamespace())) return;

        Integer cooldownTicks = NO_COOLDOWN_WEAPONS.get(id.getPath());
        if (cooldownTicks == null) return;

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            event.setCanceled(true);
            return;
        }
        player.getCooldowns().addCooldown(stack.getItem(), cooldownTicks);
    }

    // ── Нагрев для "пилы"/"лазерной пушки" ─────────────────────────────────────

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!isHeatingWeapon(event.getItem())) return;
        heatTicks.put(event.getEntity().getUUID(), 0);
    }

    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        applyMinCooldownOnRelease(event.getEntity(), event.getItem());
    }

    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        applyMinCooldownOnRelease(event.getEntity(), event.getItem());
    }

    /**
     * И Meat Shredder (release = фронтальный удар в упор, "OriginDamage/8.5", бьёт по AABB
     * ПЕРЕД собой всех LivingEntity сразу), и Laser Gatling (продолжительный луч) реально
     * наносят урон именно при отпускании/на тике. Раньше кулдаун вешался ТОЛЬКО когда игрок
     * держал 3с до принудительного перегрева — быстрый тап-отпуск срабатывал без единого
     * кулдауна вообще, то есть можно было спамить фронтальный AOE (у Meat Shredder ещё и с
     * игнором i-frames) буквально каждый тик. Теперь любое завершение использования вешает
     * хотя бы минимальный кулдаун.
     */
    private static void applyMinCooldownOnRelease(net.minecraft.world.entity.LivingEntity entity, ItemStack stack) {
        if (!isHeatingWeapon(stack)) return;
        heatTicks.remove(entity.getUUID());
        if (entity instanceof ServerPlayer player) {
            if (!player.getCooldowns().isOnCooldown(stack.getItem())) {
                player.getCooldowns().addCooldown(stack.getItem(), MIN_USE_COOLDOWN);
            }
        }
    }

    // ── Общий срез урона способностей, зашитых в код (не в toml) ──────────────

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer)) return;
        if (!ABILITY_DAMAGE_MSG_IDS.contains(event.getSource().getMsgId())) return;
        event.setAmount(event.getAmount() * ABILITY_DAMAGE_MULT);
    }

    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isHeatingWeapon(event.getItem())) return;

        int ticks = heatTicks.merge(player.getUUID(), 1, Integer::sum);
        if (ticks < OVERHEAT_TICKS) return;

        heatTicks.remove(player.getUUID());
        player.stopUsingItem();
        player.getCooldowns().addCooldown(event.getItem().getItem(), OVERHEAT_COOLDOWN);
        player.sendSystemMessage(Component.literal("§7Оружие перегрелось."));
    }

    private static boolean isHeatingWeapon(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && CATACLYSM_NAMESPACE.equals(id.getNamespace()) && HEATING_WEAPONS.contains(id.getPath());
    }
}
