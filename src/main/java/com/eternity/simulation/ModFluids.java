package com.eternity.simulation;

import com.eternity.simulation.fluids.SuperhotLavaFluid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

public class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, SimulationMod.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, SimulationMod.MODID);

    private static final ResourceLocation STILL_TEXTURE =
            new ResourceLocation(SimulationMod.MODID, "fluid/superhot_lava_still");
    private static final ResourceLocation FLOW_TEXTURE =
            new ResourceLocation(SimulationMod.MODID, "fluid/superhot_lava_flow");

    public static final RegistryObject<FluidType> SUPERHOT_LAVA_TYPE = FLUID_TYPES.register(
        "superhot_lava",
        () -> new FluidType(FluidType.Properties.create()
                .canSwim(false)
                .canDrown(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)
                .lightLevel(15)
                .density(3000)
                .viscosity(6000)
                .temperature(1600)) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return STILL_TEXTURE;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return FLOW_TEXTURE;
                    }
                });
            }
        }
    );

    public static final RegistryObject<Fluid> SUPERHOT_LAVA_SOURCE = FLUIDS.register(
        "superhot_lava",
        () -> new SuperhotLavaFluid.Source(fluidProperties())
    );

    public static final RegistryObject<Fluid> SUPERHOT_LAVA_FLOWING = FLUIDS.register(
        "flowing_superhot_lava",
        () -> new SuperhotLavaFluid.Flowing(fluidProperties())
    );

    // block()/bucket() выставляются отдельно из ModBlocks/ModItems, чтобы избежать
    // обратной зависимости класса от них на этапе статической инициализации —
    // см. ModBlocks.SUPERHOT_LAVA_BLOCK / ModItems.SUPERHOT_LAVA_BUCKET.
    private static ForgeFlowingFluid.Properties fluidProperties() {
        return new ForgeFlowingFluid.Properties(SUPERHOT_LAVA_TYPE, SUPERHOT_LAVA_SOURCE, SUPERHOT_LAVA_FLOWING)
                .bucket(ModItems.SUPERHOT_LAVA_BUCKET)
                .block(ModBlocks.SUPERHOT_LAVA_BLOCK)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .explosionResistance(100.0f)
                .tickRate(30);
    }
}
