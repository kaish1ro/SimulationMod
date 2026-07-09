package com.eternity.simulation.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Достижение Twilight Forest {@code twilightforest:progression_end} переиспользовано
 * под нашу кастомную прогрессию (см. переопределение display в
 * {@code data/twilightforest/advancements/progression_end.json}): новое название/описание/
 * иконка, и выдаётся не по штатному триггеру TF (биом final_plateau), а вручную —
 * когда игрок реально покидает перестроенный Final Castle (см. {@code ModEvents.tickQuestUiVisibility}).
 *
 * <p>Штатные критерии (biome/tick) в json оставлены как есть — глушим их здесь,
 * на уровне PlayerAdvancements.award (единственная точка, через которую ЛЮБОЙ
 * триггер реально присуждает достижение), пропуская только наш собственный
 * award-вызов, помеченный флагом {@link com.eternity.simulation.quests.ProgressionEndGate}.
 *
 * <p>ВАЖНО: сам флаг живёт в отдельном обычном классе — миксин-классам запрещены
 * не-private статические поля (InvalidMixinException при применении, валит загрузку
 * первого мода, тронувшего PlayerAdvancements), и их нельзя референсить из обычного кода.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    private static final ResourceLocation TF_PROGRESSION_END =
        new ResourceLocation("twilightforest", "progression_end");

    private static final Map<ResourceLocation, ResourceLocation> DEAD_LANDS_ADVANCEMENT_REMAP = Map.ofEntries(
        Map.entry(new ResourceLocation("born_in_chaos_v1", "good_demoman"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/good_demoman")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "explosive_temper"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/explosive_temper")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "spruce_cowboyinthe_moonlight"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/spruce_cowboyinthe_moonlight")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "junior_summoner"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/junior_summoner")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "compact_necromancy"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/compact_necromancy")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "dismantledto_bones"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/dismantledto_bones")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "lifeturnedouttobeacomedy"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/lifeturnedouttobeacomedy")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "mr_decoction"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/mr_decoction")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "shakeand_mix"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/shakeand_mix")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "double_trouble"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/double_trouble")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "one_explosion_is_good_two_is_better"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/one_explosion_is_good_two_is_better")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "exorcism"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/exorcism")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "the_wind_darkening"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/the_wind_darkening")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "guidetothenextworld"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/guidetothenextworld")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "cultural_attribute"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/cultural_attribute")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "waste_recycler"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/waste_recycler")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "shell_warrior"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/shell_warrior")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "soul_eater"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/soul_eater")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "naughty_child"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/naughty_child")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "wrong_santa"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/wrong_santa")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "food_fight"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/food_fight")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "bypassingthe_expiration_date"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/bypassingthe_expiration_date")),
        Map.entry(new ResourceLocation("born_in_chaos_v1", "freshen_your_breath"),
            new ResourceLocation("simulation", "dead_lands/born_in_chaos/freshen_your_breath"))
    );

    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract boolean award(Advancement advancement, String criterionName);

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void simulation$blockNaturalProgressionEnd(Advancement advancement, String criterionName,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (!com.eternity.simulation.quests.ProgressionEndGate.allowManualAward
                && TF_PROGRESSION_END.equals(advancement.getId())) {
            cir.setReturnValue(false);
            return;
        }

        ResourceLocation remappedId = DEAD_LANDS_ADVANCEMENT_REMAP.get(advancement.getId());
        if (remappedId != null) {
            Advancement remapped = player.getServer().getAdvancements().getAdvancement(remappedId);
            if (remapped != null) {
                for (String criterion : remapped.getCriteria().keySet()) {
                    award(remapped, criterion);
                }
            }
            cir.setReturnValue(false);
        }
    }
}
