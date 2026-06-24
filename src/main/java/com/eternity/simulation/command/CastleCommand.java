package com.eternity.simulation.command;

import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.castle.CastleBossFightTask;
import com.eternity.simulation.castle.CastleClearTask;
import com.eternity.simulation.castle.CastleRoofSealTask;
import com.eternity.simulation.castle.CastleConstants;
import com.eternity.simulation.castle.CastleDataMarker;
import com.eternity.simulation.castle.CastleForceFieldTask;
import com.eternity.simulation.castle.CastlePlacementTask;
import com.eternity.simulation.castle.CastleTerrainFillTask;
import com.eternity.simulation.castle.CastleTerrainTask;
import com.eternity.simulation.castle.CastleTowerFixTask;
import com.eternity.simulation.castle.CastleSpawnDefinition;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

/**
 * /simcastle — служебные команды для разведки и перестройки канонического Final Castle.
 *
 * <ul>
 *   <li>{@code anchor} — показывает захваченный castleAnchorPos</li>
 *   <li>{@code offset} — смещение текущей позиции игрока относительно anchor'а
 *                         (для записи координат башен под замену на NBT)</li>
 *   <li>{@code clear}  — батчевая зачистка родных блоков замка вокруг anchor'а</li>
 *   <li>{@code build}  — установка castle.nbt и labyrinth.nbt со сбором DATA-маркеров</li>
 *   <li>{@code markers} — список собранных DATA-маркеров (для отладки)</li>
 *   <li>{@code locate} — поиск Final Castle через findNearestMapStructure
 *                         (без захода игрока), для проверки совпадения с anchor'ом</li>
 *   <li>{@code roof}   — установка castle_roof.nbt (после победы над боссом, вручную)</li>
 *   <li>{@code forcefield} — батчевое продление синего силового поля вниз до deadrock</li>
 *   <li>{@code terrain} — батчевое выравнивание ландшафта вокруг замка (weathered_deadrock)</li>
 *   <li>{@code terrainfill} — засыпка "ям" от снесённых башен диффузией карты высот (отдельно от terrain)</li>
 *   <li>{@code towers} — батчевое доделывание нижних частей башен, висящих в воздухе</li>
 *   <li>{@code resetboss} — сброс битвы с главным боссом 2-го этажа в стадию 0
 *                            (убирает текущего босса/мобов волн) для повторного теста</li>
 * </ul>
 */
public class CastleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("simcastle")
            .requires(src -> src.hasPermission(2))

            .then(Commands.literal("anchor")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    BlockPos anchor = data.getCastleAnchorPos();
                    src.sendSystemMessage(Component.literal(
                        "§e[simcastle] §7Якорь: §f" + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ()));
                    return 1;
                })
            )

            .then(Commands.literal("offset")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    BlockPos anchor = data.getCastleAnchorPos();
                    BlockPos offset = player.blockPosition().subtract(anchor);

                    src.sendSystemMessage(Component.literal(
                        "§e[simcastle] §7Смещение от якоря (dx dy dz): §f"
                            + offset.getX() + " " + offset.getY() + " " + offset.getZ()));
                    return 1;
                })
            )

            .then(Commands.literal("clear")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    if (CastleClearTask.isRunning()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Зачистка уже идёт."));
                        return 0;
                    }

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    CastleClearTask.start(tfLevel, data.getCastleAnchorPos(), player);
                    return 1;
                })
            )

            .then(Commands.literal("build")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    if (CastleClearTask.isRunning()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Сначала дождись завершения зачистки."));
                        return 0;
                    }

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    return CastlePlacementTask.run(tfLevel, data.getCastleAnchorPos(), player) ? 1 : 0;
                })
            )

            .then(Commands.literal("markers")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    List<CastleDataMarker> markers = data.getCastleMarkers();
                    if (markers.isEmpty()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Маркеров нет."));
                        return 0;
                    }

                    src.sendSystemMessage(Component.literal("§e[simcastle] §7Маркеров: §f" + markers.size()));
                    for (CastleDataMarker marker : markers) {
                        BlockPos p = marker.pos();
                        src.sendSystemMessage(Component.literal(
                            "§7- §f(" + p.getX() + " " + p.getY() + " " + p.getZ() + ") §7"
                                + (marker.isEmpty() ? "§8<empty>" : marker.rawMetadata())));
                    }
                    return 1;
                })
            )

            .then(Commands.literal("spawnstate")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.isCastleSpawnSystemInit()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Спавн-система не инициализирована (нужен /simcastle build)."));
                        return 0;
                    }

                    List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());

                    src.sendSystemMessage(Component.literal("§e[simcastle] §7Точек спавна: §f" + defs.size()));
                    for (CastleSpawnDefinition def : defs) {
                        BlockPos p = def.pos();
                        boolean triggered = data.isSpawnTriggered(def.index());
                        int alive = data.getSpawnAlive(def.index());

                        src.sendSystemMessage(Component.literal(
                            "§7- §f#" + def.index() + " §7(" + p.getX() + " " + p.getY() + " " + p.getZ() + ") "
                                + "§7id=§f" + def.groupId() + " §7mob=§f" + def.mobId()
                                + " §7triggered=§f" + triggered
                                + " §7alive=§f" + alive + "/" + def.count()
                                + (def.keyDoorId() != null ? " §7keyid=§f" + def.keyDoorId() : "")));
                    }
                    return 1;
                })
            )

            .then(Commands.literal("locate")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    Registry<Structure> structureReg = tfLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
                    ResourceKey<Structure> finalCastleKey = ResourceKey.create(Registries.STRUCTURE,
                            new ResourceLocation("twilightforest", "final_castle"));
                    Holder<Structure> finalCastle = structureReg.getHolderOrThrow(finalCastleKey);
                    HolderSet<Structure> structureSet = HolderSet.direct(finalCastle);

                    BlockPos searchFrom = player.blockPosition();
                    Pair<BlockPos, Holder<Structure>> result = tfLevel.getChunkSource().getGenerator()
                            .findNearestMapStructure(tfLevel, structureSet, searchFrom, 100, false);

                    if (result == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Final Castle не найден в радиусе поиска (100 чанков)."));
                        return 0;
                    }

                    BlockPos candidate = result.getFirst();
                    src.sendSystemMessage(Component.literal(
                        "§e[simcastle] §7findNearestMapStructure: §f"
                            + candidate.getX() + " " + candidate.getY() + " " + candidate.getZ()));

                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());
                    boolean hadAnchorBefore = data.hasCastleAnchor();

                    if (hadAnchorBefore) {
                        BlockPos anchor = data.getCastleAnchorPos();
                        BlockPos delta = anchor.subtract(candidate);
                        src.sendSystemMessage(Component.literal(
                            "§e[simcastle] §7Текущий якорь: §f"
                                + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ()
                                + " §7(delta: " + delta.getX() + " " + delta.getY() + " " + delta.getZ() + ")"));
                    } else {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен — форсирую генерацию области вокруг найденной точки..."));

                        int chunkX = candidate.getX() >> 4;
                        int chunkZ = candidate.getZ() >> 4;
                        int radius = 2; // 5x5 чанков

                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                tfLevel.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, true);
                            }
                        }

                        if (data.hasCastleAnchor()) {
                            BlockPos anchor = data.getCastleAnchorPos();
                            BlockPos delta = anchor.subtract(candidate);
                            src.sendSystemMessage(Component.literal(
                                "§a[simcastle] §7Якорь захвачен: §f"
                                    + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ()
                                    + " §7(delta от findNearestMapStructure: "
                                    + delta.getX() + " " + delta.getY() + " " + delta.getZ() + ")"));
                        } else {
                            src.sendSystemMessage(Component.literal(
                                "§c[simcastle] §7Область сгенерирована, но стенд WIP не найден в радиусе 5x5 чанков от найденной точки."));
                        }
                    }

                    return 1;
                })
            )

            .then(Commands.literal("roof")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    return CastlePlacementTask.runRoof(tfLevel, data.getCastleAnchorPos(), player) ? 1 : 0;
                })
            )

            .then(Commands.literal("forcefield")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    if (CastleForceFieldTask.isRunning()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Продление силового поля уже идёт."));
                        return 0;
                    }

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    return CastleForceFieldTask.start(tfLevel, data.getCastleAnchorPos(), player) ? 1 : 0;
                })
            )

            .then(Commands.literal("terrain")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    if (CastleTerrainTask.isRunning()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Выравнивание ландшафта уже идёт."));
                        return 0;
                    }

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    return CastleTerrainTask.start(tfLevel, data.getCastleAnchorPos(), player) ? 1 : 0;
                })
            )

            .then(Commands.literal("terrainfill")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    if (CastleTerrainFillTask.isRunning()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Засыпка ям уже идёт."));
                        return 0;
                    }

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    return CastleTerrainFillTask.start(tfLevel, data.getCastleAnchorPos(), player) ? 1 : 0;
                })
            )

            .then(Commands.literal("towers")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.hasCastleAnchor()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Якорь ещё не захвачен."));
                        return 0;
                    }

                    if (CastleTowerFixTask.isRunning()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Доделывание башен уже идёт."));
                        return 0;
                    }

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    return CastleTowerFixTask.start(tfLevel, data.getCastleAnchorPos(), player) ? 1 : 0;
                })
            )

            .then(Commands.literal("roofstop")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    if (!data.isRoofSealActive() && !CastleRoofSealTask.isActive()) {
                        src.sendSystemMessage(Component.literal("§e[simcastle] §7Распечатывание крыши не активно."));
                        return 0;
                    }

                    CastleRoofSealTask.stop(data);
                    src.sendSystemMessage(Component.literal(
                        "§a[simcastle] §7Процесс распечатывания остановлен. Мир не изменён."));
                    return 1;
                })
            )

            .then(Commands.literal("resetboss")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());

                    ServerLevel tfLevel = src.getServer().getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
                    if (tfLevel == null) {
                        src.sendSystemMessage(Component.literal("§c[simcastle] §7Twilight Forest не загружен."));
                        return 0;
                    }

                    CastleBossFightTask.resetBossFight(tfLevel, data);
                    src.sendSystemMessage(Component.literal(
                        "§a[simcastle] §7Битва с боссом сброшена (стадия 0). "
                            + "Если группа floor2 уже зачищена, бой запустится автоматически."));
                    return 1;
                })
            )
        );
    }
}
