package com.eternity.simulation.quests;

/**
 * Флаг-шлюз для {@code PlayerAdvancementsMixin}: {@code true} только на время нашего
 * собственного вызова {@code PlayerAdvancements.award(...)} для
 * {@code twilightforest:progression_end} (см. {@code ModEvents.awardProgressionEnd}).
 *
 * <p>Вынесен в ОБЫЧНЫЙ класс, а не в сам миксин, по двум причинам сразу:
 * (1) Mixin запрещает не-private статические поля в миксин-классах —
 * InvalidMixinException при применении валит конструкцию первого же мода,
 * подгрузившего целевой класс (в нашем модпаке это Create), и рушит всю загрузку;
 * (2) миксин-классы вообще нельзя референсить из обычного кода — их загрузка
 * обычным класслоадером запрещена. Ссылка миксин → обычный класс легальна.
 */
public final class ProgressionEndGate {

    private ProgressionEndGate() {}

    public static volatile boolean allowManualAward = false;
}
