package com.eternity.simulation.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Alex's Mobs treats Dimensional Carver mostly as a portal tool. In this pack it
 * is a Void Worm reward, so it also behaves like a netherite-grade expedition
 * pickaxe while keeping the original portal ability.
 */
@Mixin(targets = "com.github.alexthe666.alexsmobs.item.ItemDimensionalCarver", remap = false)
public abstract class DimensionalCarverMixin {
    private static final int NETHERITE_PICKAXE_DURABILITY = 2031;
    private static final int PORTAL_DURABILITY_COST = 50;
    private static final int NETHERITE_ENCHANTMENT_VALUE = 15;

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;<init>(Lnet/minecraft/world/item/Item$Properties;)V"
        ),
        index = 0
    )
    private static Item.Properties simulation$makeDamageable(Item.Properties properties) {
        return properties.durability(NETHERITE_PICKAXE_DURABILITY);
    }

    public float m_8102_(ItemStack stack, BlockState state) {
        if (isCarverMineable(state)) {
            return Tiers.NETHERITE.getSpeed();
        }
        return 1.0F;
    }

    public boolean m_8096_(BlockState state) {
        return isCarverMineable(state)
                && TierSortingRegistry.isCorrectTierForDrops(Tiers.NETHERITE, state);
    }

    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return m_8096_(state);
    }

    public boolean m_6813_(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(1, miner, entity -> entity.broadcastBreakEvent(miner.getUsedItemHand()));
        }
        return true;
    }

    public boolean m_7579_(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(2, attacker, entity -> entity.broadcastBreakEvent(attacker.getUsedItemHand()));
        return true;
    }

    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(toolAction)
                || ToolActions.DEFAULT_AXE_ACTIONS.contains(toolAction)
                || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(toolAction);
    }

    public boolean m_8120_(ItemStack stack) {
        return true;
    }

    public int m_6473_() {
        return NETHERITE_ENCHANTMENT_VALUE;
    }

    public int getEnchantmentValue(ItemStack stack) {
        return NETHERITE_ENCHANTMENT_VALUE;
    }

    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return isToolEnchantment(enchantment);
    }

    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        var enchantments = EnchantmentHelper.deserializeEnchantments(EnchantedBookItem.getEnchantments(book));
        return !enchantments.isEmpty()
                && enchantments.keySet().stream().allMatch(DimensionalCarverMixin::isToolEnchantment);
    }

    @ModifyArg(
        method = "m_5929_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;m_41622_(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"
        ),
        index = 0
    )
    private int simulation$increasePortalDurabilityCost(int originalCost) {
        return PORTAL_DURABILITY_COST;
    }

    private static boolean isCarverMineable(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    private static boolean isToolEnchantment(Enchantment enchantment) {
        EnchantmentCategory category = enchantment.category;
        return category == EnchantmentCategory.DIGGER
                || category == EnchantmentCategory.BREAKABLE
                || category == EnchantmentCategory.VANISHABLE;
    }
}
