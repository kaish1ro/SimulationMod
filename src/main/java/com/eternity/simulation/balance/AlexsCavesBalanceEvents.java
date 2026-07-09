package com.eternity.simulation.balance;

import com.eternity.simulation.SimulationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Alex's Caves не даёт ни одного конфиг-рычага для HP/урона (в отличие от Cataclysm
 * и Legendary Monsters) — все статы зашиты в Java. Правим напрямую при спавне через
 * {@link EntityJoinLevelEvent}, тем же способом, что {@code ModEvents.applyBossScale},
 * только здесь это фиксированная замена базового значения, а не множитель.
 *
 * <p>Затронуты только существа из АКТИВНЫХ биомов (abyssal_chasm, forlorn_hollows,
 * magnetic_caves, primordial_caves) с базовым HP>100 — существа из заблокированных
 * candy_cavity/toxic_caves (Candicorn, Gum Worm, Gammaroach, Nucleeper и т.п.) не тронуты.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public final class AlexsCavesBalanceEvents {

    private AlexsCavesBalanceEvents() {}

    private static final String NAMESPACE = "alexscaves";

    private record MobCap(double health, double damage) {}

    // HP срезаны ещё на ~21% (тот же процент, что довёл luxtructosaurus 380 -> 300 ровно).
    private static final Map<String, MobCap> TARGET_MOBS = Map.of(
        "luxtructosaurus", new MobCap(300.0, 9.0),
        "tremorzilla",     new MobCap(275.0, 18.0),
        "forsaken",        new MobCap(180.0, 8.0),
        "hullbreaker",     new MobCap(235.0, 12.0),
        "atlatitan",       new MobCap(235.0, 6.0),
        "tremorsaurus",    new MobCap(110.0, 11.0),
        "relicheirus",     new MobCap(85.0, 9.0)
    );

    /** Урон луча Tremorzilla (hurtEntitiesAround, 20.0 за тик дыхания) — единственная
     * найденная декомпилом способность этого списка, чей урон не совпадает с базовым
     * атрибутом атаки. Sonic Boom Forsaken декомпилом подтверждён безобидным (4.0 по
     * игроку, 45.0 только против цели из тега WEAK_TO_FORSAKEN_SONIC_ATTACK — то есть
     * не по игрокам), поэтому не тронут. */
    private static final float TREMORZILLA_BEAM_MULT = 0.65f;

    /**
     * HIGH — эти существа теперь также в {@code data/simulation/tags/entity_types/scaled_bosses.json},
     * так что {@code ModEvents.onBossJoinLevel} домножает HP/урон на скейл игроков на том же
     * EntityJoinLevelEvent. Наша фиксация базового значения обязана отработать ПЕРВОЙ,
     * иначе порядок между двумя NORMAL-хендлерами не гарантирован и скейл может слететь.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMobJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id == null || !NAMESPACE.equals(id.getNamespace())) return;

        MobCap cap = TARGET_MOBS.get(id.getPath());
        if (cap == null) return;

        var hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(cap.health());
            mob.setHealth((float) cap.health());
        }
        var dmgAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(cap.damage());
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        if (!"tremorzilla_beam".equals(event.getSource().getMsgId())) return;
        event.setAmount(event.getAmount() * TREMORZILLA_BEAM_MULT);
    }
}
