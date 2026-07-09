package com.eternity.simulation.fluids;

import com.eternity.simulation.ModFluids;
import com.eternity.simulation.SimulationMod;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Сверхгорячая лава сама по себе не привязана к тегу {@code minecraft:lava}
 * (это отдельная жидкость, см. {@link SuperhotLavaFluid}), поэтому ванильный
 * механизм "лава поджигает и наносит урон" (завязанный именно на этот тег в
 * {@code Entity.isInLava()}/{@code lavaHurt()}) на неё не срабатывает сам по
 * себе — несмотря на то, что поджигание СОСЕДНИХ БЛОКОВ (randomTick) уже
 * реализовано в SuperhotLavaFluid по образцу ванильной LavaFluid. Сущностей
 * это не касалось вообще. Раз нужен урон, ОТЛИЧНЫЙ от ванильных 4.0 (по
 * заданию — 3 HP), просто повесить тег лавы нельзя (это дало бы вкопанные
 * 4.0 из lavaHurt()) — реализуем сами через {@code Entity.isInFluidType}.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class SuperhotLavaEntityListener {

    private static final int FIRE_SECONDS = 15; // как у ванильной лавы
    private static final float DAMAGE_AMOUNT = 3.0F;

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity.fireImmune()) return;

        boolean inSuperhotLava = entity.isInFluidType(
                (fluidType, height) -> fluidType == ModFluids.SUPERHOT_LAVA_TYPE.get());
        if (!inSuperhotLava) return;

        entity.setSecondsOnFire(FIRE_SECONDS);
        if (entity.hurt(entity.damageSources().lava(), DAMAGE_AMOUNT)) {
            entity.playSound(SoundEvents.GENERIC_BURN, 0.4F,
                    2.0F + entity.getRandom().nextFloat() * 0.4F);
        }
    }
}
