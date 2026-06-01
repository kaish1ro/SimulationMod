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

public record RemoveDivineRPGModifier(HolderSet<Biome> biomes) implements BiomeModifier {

    static final Holder<Codec<RemoveDivineRPGModifier>> CODEC = Holder.direct(
        RecordCodecBuilder.create(inst -> inst.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(RemoveDivineRPGModifier::biomes)
        ).apply(inst, RemoveDivineRPGModifier::new))
    );

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE) return;
        if (!biomes.contains(biome)) return;

        // Убираем все PlacedFeature из namespace divinerpg
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            builder.getGenerationSettings().getFeatures(step).removeIf(f ->
                f.unwrapKey()
                    .map(k -> "divinerpg".equals(k.location().getNamespace()))
                    .orElse(false)
            );
        }

        // Убираем всех мобов divinerpg
        for (MobCategory cat : MobCategory.values()) {
            builder.getMobSpawnSettings().getSpawner(cat).removeIf(s -> {
                var key = ForgeRegistries.ENTITY_TYPES.getKey(s.type);
                return key != null && "divinerpg".equals(key.getNamespace());
            });
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return ModBiomeModifiers.REMOVE_DIVINERPG.get();
    }
}
