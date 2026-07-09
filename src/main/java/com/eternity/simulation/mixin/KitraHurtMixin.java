package com.eternity.simulation.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * divinerpg:kitra в её hurt() пропускает урон только от узкого списка типов
 * (magic/indirect_magic/arcana/explosion/lightning_bolt/dragon_breath/wither/
 * void) — весь остальной урон (melee, стрелы) вообще не проходит, метод
 * возвращает false ещё до вызова super.hurt(). На этом этапе игры магическое
 * оружие DivineRPG (Arcana) игроку недоступно, поэтому пробиваем иммунитет
 * ещё и для обычных атак.
 *
 * <p>Метод делает 7 отдельных вызовов {@code DamageSource.is(ResourceKey)}
 * (по одному на каждый разрешённый тип, см. декомпиляцию байткода) — Redirect
 * без ordinal перехватывает все 7. Как только источник урона — melee/дальняя
 * атака, самая первая же проверка (на MAGIC) возвращает true и код прыгает
 * сразу к вызову super.hurt(), остальные проверки просто не выполняются.
 *
 * <p>Цель задаётся строкой и SRG-именем метода напрямую (проверено через
 * javap) — как и у {@link IceBroodMotherHurtMixin}, ремап через refmap тут не
 * нужен и не сработал бы для метода чужого мода.
 */
@Mixin(targets = "divinerpg.entities.boss.EntityKitra", remap = false)
public abstract class KitraHurtMixin {

    @Redirect(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;m_276093_(Lnet/minecraft/resources/ResourceKey;)Z", remap = false))
    private boolean simulation$allowMeleeAndRangedDamage(DamageSource source, ResourceKey<DamageType> key) {
        if (source.is(key)) return true;
        return source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.ARROW) || source.is(DamageTypes.TRIDENT)
                || source.is(DamageTypes.MOB_PROJECTILE) || source.is(DamageTypes.THROWN);
    }
}
