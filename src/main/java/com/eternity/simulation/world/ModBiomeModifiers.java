package com.eternity.simulation.world;

import com.eternity.simulation.SimulationMod;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBiomeModifiers {
    public static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, SimulationMod.MODID);

    public static final RegistryObject<Codec<RemoveDivineRPGModifier>> REMOVE_DIVINERPG =
        BIOME_MODIFIER_SERIALIZERS.register("remove_divinerpg",
            RemoveDivineRPGModifier.CODEC::value);

    public static final RegistryObject<Codec<RemoveThreateninglyModifier>> REMOVE_THREATENINGLY =
        BIOME_MODIFIER_SERIALIZERS.register("remove_threateningly",
            RemoveThreateninglyModifier.CODEC::value);
}
