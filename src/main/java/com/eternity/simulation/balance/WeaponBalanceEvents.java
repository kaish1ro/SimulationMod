package com.eternity.simulation.balance;

import com.eternity.simulation.SimulationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Attribute caps for third-party boss weapons.
 *
 * <p>Raw LM/Cataclysm drops and crafts are always fixed stage 0 bases. Further
 * power comes from the NBT upgrade stage on that exact item stack.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public final class WeaponBalanceEvents {

    private WeaponBalanceEvents() {}

    private static final String LM_NAMESPACE = "legendary_monsters";
    private static final String CATACLYSM_NAMESPACE = "cataclysm";

    private record WeaponCap(double stage0Damage, int maxStage) {}

    private static final Map<String, WeaponCap> LM_WEAPONS = Map.ofEntries(
        cap("knights_sword", 10.0, 3),
        cap("golden_halbert", 14.4, 3),
        cap("axe_of_lightning", 8.8667, 6),
        cap("dinosaur_bone_club", 8.5, 5),
        cap("mossy_hammer", 12.8, 6),
        cap("the_great_frost", 12.24, 5),
        cap("withered_scythe", 10.7667, 5),
        cap("the_tesseract", 10.08, 7),
        cap("monstrous_anchor", 12.8, 7),
        cap("chorus_blade", 12.24, 5),
        cap("shattered_greatsword", 14.4, 3),
        cap("resurrected_javelin", 10.0, 3),
        cap("soul_great_sword", 10.08, 6),
        cap("atom_splitter", 8.4, 7),
        cap("fiery_jaw", 7.14, 5)
    );

    private static final Map<String, WeaponCap> CATACLYSM_WEAPONS = Map.ofEntries(
        cap("gauntlet_of_guard", 10.0, 6),
        cap("infernal_forge", 16.0, 6),
        cap("tidal_claws", 10.0, 6),
        cap("soul_render", 15.2381, 5),
        cap("the_annihilator", 10.0, 5),
        cap("ceraunus", 22.8571, 5),
        cap("astrape", 11.4286, 5),
        cap("meat_shredder", 11.4286, 5),
        cap("ancient_spear", 11.4286, 5),
        cap("the_incinerator", 11.4286, 6),
        cap("gauntlet_of_bulwark", 11.0, 6),
        cap("gauntlet_of_maelstrom", 11.0, 6),
        cap("void_forge", 13.0, 6),
        cap("the_immolator", 8.5, 5)
    );

    /** Vanilla netherite reference for old armor caps: [feet, legs, chest, head]. */
    private static final int[] NETHERITE_ARMOR = {3, 6, 8, 3};
    private static final double NETHERITE_TOUGHNESS = 4.0;
    private static final double NETHERITE_KNOCKBACK = 0.1;

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null) return;

        if (event.getSlotType() == EquipmentSlot.MAINHAND || event.getSlotType() == EquipmentSlot.OFFHAND) {
            WeaponCap cap = weaponCap(id);
            if (cap != null) {
                int stage = Math.min(WeaponUpgrade.getStage(event.getItemStack()), cap.maxStage());
                double totalDamage = cap.stage0Damage() * WeaponProgression.stats(stage).damageMult();
                replaceModifier(event, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, totalDamage - 1.0);
            }
            return;
        }

        if (!LM_NAMESPACE.equals(id.getNamespace())) return;

        // LM armor still follows the old world-tier cap. Armor upgrade recipes
        // will be handled separately later.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        int tier = server != null ? WorldTier.compute(server) : 1;
        WorldTier.TierStats stats = WorldTier.stats(tier);

        int slotIndex = armorSlotIndex(event.getSlotType());
        if (slotIndex < 0) return;

        double cappedArmor = NETHERITE_ARMOR[slotIndex] * stats.damageMult();
        double cappedToughness = NETHERITE_TOUGHNESS * stats.damageMult();
        double cappedKnockback = Math.min(NETHERITE_KNOCKBACK * stats.damageMult(), 1.0);

        capIfExceeds(event, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, cappedArmor);
        capIfExceeds(event, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS, cappedToughness);
        capIfExceeds(event, net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, cappedKnockback);
    }

    private static Map.Entry<String, WeaponCap> cap(String itemPath, double stage0Damage, int maxStage) {
        return Map.entry(itemPath, new WeaponCap(stage0Damage, WeaponProgression.clampStage(maxStage)));
    }

    private static WeaponCap weaponCap(ResourceLocation id) {
        return switch (id.getNamespace()) {
            case LM_NAMESPACE -> LM_WEAPONS.get(id.getPath());
            case CATACLYSM_NAMESPACE -> CATACLYSM_WEAPONS.get(id.getPath());
            default -> null;
        };
    }

    /** Replace attack damage with an exact target total damage value. */
    private static void replaceModifier(ItemAttributeModifierEvent event, Attribute attribute, double newAmount) {
        List<AttributeModifier> existing = new ArrayList<>(event.getModifiers().get(attribute));
        if (existing.isEmpty()) {
            event.addModifier(attribute, new AttributeModifier("simulation_weapon_cap", newAmount, AttributeModifier.Operation.ADDITION));
            return;
        }
        for (AttributeModifier mod : existing) {
            event.removeModifier(attribute, mod);
            event.addModifier(attribute, new AttributeModifier(mod.getId(), mod.getName(), newAmount, mod.getOperation()));
        }
    }

    /** Lower an attribute only when it exceeds the cap. */
    private static void capIfExceeds(ItemAttributeModifierEvent event, Attribute attribute, double cap) {
        List<AttributeModifier> existing = new ArrayList<>(event.getModifiers().get(attribute));
        for (AttributeModifier mod : existing) {
            if (mod.getAmount() > cap) {
                event.removeModifier(attribute, mod);
                event.addModifier(attribute, new AttributeModifier(mod.getId(), mod.getName(), cap, mod.getOperation()));
            }
        }
    }

    private static int armorSlotIndex(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }
}
