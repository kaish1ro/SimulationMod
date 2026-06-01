package com.eternity.simulation.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Убирает все фичи и спавн мобов из неймспейса {@code threateningly_mobs} из всех указанных биомов.
 * Применяется ко всем измерениям через несколько JSON-файлов.
 *
 * <p>Структуры (Structure Objects) подавляются отдельно через override-JSON в
 * {@code data/threateningly_mobs/worldgen/structure/} с пустым полем {@code "biomes": []}.
 */
public record RemoveThreateninglyModifier(HolderSet<Biome> biomes) implements BiomeModifier {

    static final Holder<Codec<RemoveThreateninglyModifier>> CODEC = Holder.direct(
        RecordCodecBuilder.create(inst -> inst.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(RemoveThreateninglyModifier::biomes)
        ).apply(inst, RemoveThreateninglyModifier::new))
    );

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE) return;
        if (!biomes.contains(biome)) return;

        // Убираем все PlacedFeature из namespace threateningly_mobs
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            builder.getGenerationSettings().getFeatures(step).removeIf(f ->
                f.unwrapKey()
                    .map(k -> "threateningly_mobs".equals(k.location().getNamespace()))
                    .orElse(false)
            );
        }

        // Убираем всех мобов threateningly_mobs из спавн-листов биома
        for (MobCategory cat : MobCategory.values()) {
            builder.getMobSpawnSettings().getSpawner(cat).removeIf(s -> {
                var key = ForgeRegistries.ENTITY_TYPES.getKey(s.type);
                return key != null && "threateningly_mobs".equals(key.getNamespace());
            });
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return ModBiomeModifiers.REMOVE_THREATENINGLY.get();
    }
}
