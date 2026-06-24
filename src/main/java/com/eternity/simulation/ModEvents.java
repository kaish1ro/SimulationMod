package com.eternity.simulation;

import com.eternity.simulation.castle.CastleClearTask;
import com.eternity.simulation.castle.CastleForceFieldTask;
import com.eternity.simulation.castle.CastleTerrainTask;
import com.eternity.simulation.castle.CastleTerrainFillTask;
import com.eternity.simulation.castle.CastleTowerFixTask;
import com.eternity.simulation.castle.CastleSpawnManager;
import com.eternity.simulation.castle.CastleRevealTask;
import com.eternity.simulation.castle.CastlePedestalPuzzleTask;
import com.eternity.simulation.castle.CastleSpawnPointTask;
import com.eternity.simulation.castle.CastleBossFightTask;
import com.eternity.simulation.castle.CastleConstants;
import com.eternity.simulation.castle.CastleRoofSealTask;
import com.eternity.simulation.castle.CastlePaintingRestoreTask;
import com.eternity.simulation.command.CastleCommand;
import com.eternity.simulation.command.PortalCommand;
import com.eternity.simulation.command.RiftCommand;
import com.eternity.simulation.villager.VillagerTradeModifier;
import com.eternity.simulation.command.TestOllamaCommand;
import com.eternity.simulation.command.WandererCommand;
import com.eternity.simulation.entity.EncounterManager;
import com.eternity.simulation.entity.ObserverSpawnHandler;
import com.eternity.simulation.entity.RiftManager;
import com.eternity.simulation.entity.RiftSealingSequence;
import com.eternity.simulation.entity.WandererEntity;
import com.eternity.simulation.menu.SimulationCraftingMenu;
import com.eternity.simulation.ollama.NpcConversationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Objects;

import java.util.List;

@Mod.EventBusSubscriber(modid = SimulationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    // ─── Синхронизация глобального scoreboard для KubeJS ───
    // KubeJS читает очки фейкового игрока "#world" в ServerEvents.recipes
    // и убирает рецепты залоченных групп. "#world" — стандартное имя для
    // глобальных счётчиков в Minecraft (не привязано к реальному игроку).

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        GameRules rules = overworld.getGameRules();
        Scoreboard sb = server.getScoreboard();

        boolean g1 = getBooleanSafe(rules, ModGameRules.BLUEPRINT_GROUP1);
        boolean g2 = getBooleanSafe(rules, ModGameRules.BLUEPRINT_GROUP2);
        boolean g3 = getBooleanSafe(rules, ModGameRules.BLUEPRINT_GROUP3);

        setGlobalScore(sb, 1, g1);
        setGlobalScore(sb, 2, g2);
        setGlobalScore(sb, 3, g3);

        // Если хоть одна группа открыта — перезагружаем рецепты,
        // чтобы KubeJS прочитал scoreboard и вернул нужные рецепты.
        if (g1 || g2 || g3) {
            server.reloadResources(server.getPackRepository().getSelectedIds());
        }

        // Однократный поиск осиротевшего разлома после перезагрузки сервера.
        // Вынесено из RiftManager.tick() чтобы не итерировать getAllEntities каждые 100 тиков.
        // Выполняется здесь, так как сущности уже загружены к моменту ServerStartedEvent.
        if (SimulationSavedData.get(overworld).isDragonDefeated()) {
            RiftManager.INSTANCE.adoptOrphanedRift(overworld);
        }

        // Восстановление реестра кастомных картин Immersive Paintings на каждом старте
        // сервера — реестр живёт в памяти и не переживает перезапуск без /simcastle build.
        ServerLevel tfLevel = server.getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
        if (tfLevel != null) {
            CastlePaintingRestoreTask.restore(tfLevel);
        }
    }

    /**
     * Ставит score=1 (открыто) или score=0 (закрыто) для фейкового игрока "#world"
     * на objective "sim_gN". Если unlocked=false и objective ещё не создан — ничего не делаем.
     */
    public static void setGlobalScore(Scoreboard sb, int group, boolean unlocked) {
        String objName = "sim_g" + group;
        Objective obj = sb.getObjective(objName);
        if (obj == null) {
            if (!unlocked) return;
            obj = sb.addObjective(objName, ObjectiveCriteria.DUMMY,
                    Component.empty(), ObjectiveCriteria.RenderType.INTEGER);
        }
        sb.getOrCreatePlayerScore("#world", obj).setScore(unlocked ? 1 : 0);
    }

    /**
     * Смещение точки приземления относительно якоря (стенда "Final Castle WIP.").
     * Сам Страж спавнится в {@code anchor + (0, -20, 0)} (см. spawnTFCastleLich) —
     * сдвигаем игрока в сторону по X, чтобы не приземлиться прямо на босса.
     * Колонна расчищена в диапазоне dx,dz ∈ [-2, 2], dy ∈ [-5, -21] от якоря.
     * TODO: подогнать офсет по месту после первого визита.
     */
    private static final BlockPos CASTLE_LANDING_OFFSET = new BlockPos(2, -19, 0);

    /**
     * Телепортирует игрока в Финальный замок TF.
     *
     * <p>Если якорь ({@code SimulationSavedData.castleAnchorPos}) уже захвачен
     * (см. {@code onEntityJoinLevel} — захватывается при первой загрузке чанка
     * со стендом "Final Castle WIP."), телепортируем сразу на кэшированные
     * координаты — без поиска структуры.
     *
     * <p>Если якоря ещё нет — ищем структуру через ChunkGenerator (эвристика,
     * сработает только если нужный чанк уже когда-то полностью прогружался —
     * иначе стенд ещё не заспавнился и якорь не захвачен).
     */
    private static void teleportToCastle(ServerPlayer player) {
        // Откладываем на 20 тиков — к тому моменту игрок уже в измерении
        player.getServer().execute(() -> {
            var server = player.getServer();
            if (server == null) return;

            var tfLevel = server.getLevel(TWILIGHT_FOREST_DIM);
            if (tfLevel == null) return;

            SimulationSavedData data = SimulationSavedData.get(server.overworld());

            if (data.hasCastleAnchor()) {
                BlockPos target = data.getCastleAnchorPos().offset(CASTLE_LANDING_OFFSET);
                player.teleportTo(tfLevel,
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5,
                    player.getYRot(), player.getXRot()
                );
                applyCastleArrivalEffects(player, data, target);
                TF_LOGGER.info("[TF Castle] Teleported {} to cached castle anchor {}", player.getName().getString(), target);
                return;
            }

            // ── Якорь ещё не захвачен — ищем структуру (старая эвристика) ──────
            var structureRegistry = tfLevel.registryAccess()
                .registry(net.minecraft.core.registries.Registries.STRUCTURE)
                .orElse(null);
            if (structureRegistry == null) return;

            var castleHolder = structureRegistry.getHolder(TF_CASTLE_KEY).orElse(null);
            if (castleHolder == null) {
                TF_LOGGER.warn("[TF Castle] structure holder not found for {}", TF_CASTLE_KEY.location());
                return;
            }

            var result = tfLevel.getChunkSource().getGenerator()
                .findNearestMapStructure(
                    tfLevel,
                    net.minecraft.core.HolderSet.direct(castleHolder),
                    player.blockPosition(),
                    200,
                    false
                );

            if (result == null) {
                TF_LOGGER.warn("[TF Castle] No final_castle found near {}", player.blockPosition());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§8[Система] §7Замок не найден в радиусе поиска."
                ));
                return;
            }

            var castlePos = result.getFirst();
            // Телепортируем на вершину структуры + небольшой офсет чтобы не застрять
            int y = tfLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                castlePos.getX(), castlePos.getZ());
            player.teleportTo(tfLevel,
                castlePos.getX() + 0.5,
                y + 2,
                castlePos.getZ() + 0.5,
                player.getYRot(), player.getXRot()
            );

            // Якорь ещё не захвачен (чанк со стендом не прогружен) — печать пока не ставим,
            // её выставит applyCastleArrivalEffects при следующем телепорте на якорь.
            TF_LOGGER.info("[TF Castle] Teleported {} near castle (no anchor yet) at {}", player.getName().getString(), castlePos);
        });
    }

    /** Печать строительства + сообщение игроку при прибытии в арену стража. */
    private static void applyCastleArrivalEffects(ServerPlayer player, SimulationSavedData data, BlockPos target) {
        if (data.isCastleGuardianDefeated()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§8[Система] §7Зал стража пуст — древняя печать спала."));
            return;
        }
        data.setBuildLocked(player.getUUID(), true);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§8[Система] §7Древняя печать запрещает что-либо строить или ломать здесь."));
    }

    private static boolean getBooleanSafe(GameRules rules, GameRules.Key<GameRules.BooleanValue> key) {
        GameRules.BooleanValue value = rules.getRule(key);
        return value != null && value.get();
    }

    // ─── Скейл боссов по числу активных игроков ──────────────────────────────

    /** Тег entity-типов, которые масштабируются по количеству игроков. */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> SCALED_BOSSES_TAG =
            net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    new ResourceLocation("simulation", "scaled_bosses"));

    /**
     * Вычисляет множитель сложности босса: 1.5^(N-1).
     * N — количество игроков, активных за последние 3 реальных дня.
     */
    public static double getBossScale(ServerLevel level) {
        SimulationSavedData data = SimulationSavedData.get(level);
        int n = data.getScalingPlayerCount();
        return Math.pow(1.5, n - 1);
    }

    /**
     * Применяет масштаб HP и урона к мобу-боссу.
     * Вызывается при спавне и при загрузке из NBT.
     */
    public static void applyBossScale(net.minecraft.world.entity.Mob mob, double scale) {
        if (scale <= 1.001) return; // нет смысла трогать при 1 игроке

        var maxHpAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            double newMax = maxHpAttr.getBaseValue() * scale;
            maxHpAttr.setBaseValue(newMax);
            mob.setHealth((float) newMax);
        }
        var dmgAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(dmgAttr.getBaseValue() * scale);
        }
    }

    /** При входе босса в мир — масштабируем если он в теге scaled_bosses. */
    @SubscribeEvent
    public static void onBossJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) return;
        if (!mob.getType().is(SCALED_BOSSES_TAG)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        // Кастомные боссы масштабируются в своём spawn-коде напрямую
        var tags = mob.getTags();
        if (tags.contains("sealed_rift_boss") || tags.contains("tf_castle_guardian")) return;

        applyBossScale(mob, getBossScale(sl));
    }

    // ─── Спавн Наблюдателя в первой деревне ─────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ObserverSpawnHandler.onPlayerLogin(event);
        // Записываем факт входа игрока для системы скейла боссов
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                SimulationSavedData.get(server.overworld()).recordPlayerSeen(player.getUUID());
            }
        }
    }

    // ─── Переходы между измерениями ───────────────────────────────────────────

    private static final ResourceKey<Level> TWILIGHT_FOREST_DIM = ResourceKey.create(
        net.minecraft.core.registries.Registries.DIMENSION,
        new ResourceLocation("twilightforest", "twilight_forest")
    );

    private static final ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> TF_CASTLE_KEY =
        ResourceKey.create(
            net.minecraft.core.registries.Registries.STRUCTURE,
            new ResourceLocation("twilightforest", "final_castle")
        );

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Первый выход из Нижнего мира → встреча со Скитальцем (обычный AI)
        if (event.getFrom().equals(Level.NETHER) && event.getTo().equals(Level.OVERWORLD)) {
            EncounterManager.INSTANCE.onPlayerExitedNether(player);
        }

        // Телепорт в замок TF при входе — отключён (замок ещё в разработке)
        // if (event.getTo().equals(TWILIGHT_FOREST_DIM)) { teleportToCastle(player); }

        // ВАЖНО: выход из Края НЕ обрабатывается здесь.
        // ServerPlayer.changeDimension для End→Overworld делает ранний return
        // (показ титров + отложенный респаун) и НЕ вызывает это событие.
        // Выход из Края ловится в onPlayerRespawn() по флагу isEndConquered().
    }

    /**
     * Срабатывает при респауне игрока. Выход из Края (через портал после
     * убийства дракона) приходит сюда с флагом {@code isEndConquered() == true} —
     * уже ПОСЛЕ титров, когда игрок полностью перемещён в Обычный мир.
     * Обычная смерть игрока сюда тоже приходит, но с {@code isEndConquered() == false}.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered()) return;  // только выход из Края, не смерть
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        handleEndExit(player);
    }

    /**
     * Спавнит Скитальца сцены рядом с игроком.
     * Скиталец произносит монолог, отдаёт книгу и уходит. Один раз на весь мир.
     * Публичный для вызова из {@link com.eternity.simulation.command.WandererCommand}.
     *
     * @param forceScene  true → игнорировать флаги dragonDefeated / exitSceneTriggered
     *                    (для тестовой команды)
     */
    public static void spawnEndExitScene(ServerPlayer player, boolean forceScene) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel overworld = server.overworld();
        SimulationSavedData data = SimulationSavedData.get(overworld);

        if (!forceScene) {
            if (!data.isDragonDefeated()) return;
            if (data.isExitSceneTriggered()) return;
        }
        data.setExitSceneTriggered(true);

        // 8 блоков позади игрока, на поверхности земли
        double yawRad = Math.toRadians(player.getYRot());
        double spawnX = player.getX() - Math.sin(yawRad) * 8.0;
        double spawnZ = player.getZ() + Math.cos(yawRad) * 8.0;
        // Heightmap: ставим на поверхность, а не в воздух/землю
        double spawnY = overworld.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) spawnX, (int) spawnZ);

        WandererEntity wanderer = ModEntities.WANDERER.get().create(overworld);
        if (wanderer == null) return;

        wanderer.moveTo(spawnX, spawnY, spawnZ, 0f, 0f);
        wanderer.setTargetPlayer(player.getUUID());
        wanderer.startScene();
        overworld.addFreshEntity(wanderer);
    }

    private static void handleEndExit(ServerPlayer player) {
        spawnEndExitScene(player, false);
    }

    // ─── Регистрация команд ───────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TestOllamaCommand.register(event.getDispatcher());
        WandererCommand.register(event.getDispatcher());
        RiftCommand.register(event.getDispatcher());
        PortalCommand.register(event.getDispatcher());
        CastleCommand.register(event.getDispatcher());
    }

    // ─── Блокировка Midnight разломов в Обычном мире ─────────────────────────────
    // Midnight спавнит Rift-сущности по ночам в Overworld через биомный тег spawns_rifts.
    // Тег переопределён в KubeJS (replace=true, values=[]), но на случай если биомный
    // механизм не отрабатывает — дополнительно отменяем сущность при попытке войти в мир.

    @SubscribeEvent
    public static void onMidnightRiftJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!event.getLevel().dimension().equals(Level.OVERWORLD)) return;

        var key = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (key != null && "midnight".equals(key.getNamespace())
                && "rift".equals(key.getPath())) {
            event.setCanceled(true);
        }
    }

    // ─── Блокировка зачарований Cyclic из стола зачарований и наковальни ────────
    //
    // В Forge 1.20.1 нет события для перехвата выбора зачарований из стола.
    // EnchantmentLevelSetEvent — делаем все 3 слота недоступными (уровень 0)
    // если предмет имеет namespace cyclic в зачарованиях... однако в этом
    // событии ещё неизвестно какие конкретно зачарования будут — поэтому
    // для стола зачарований используем другой подход: убираем зачарования
    // через VillagerTradeModifier (библиотекари) и ниже через наковальню.
    //
    // AnvilUpdateEvent — блокируем применение книг Cyclic через наковальню.

    @SubscribeEvent
    public static void blockCyclicAnvil(AnvilUpdateEvent event) {
        net.minecraft.world.item.ItemStack right = event.getRight();
        if (right.isEmpty()) return;
        // Проверяем книгу зачарований: если содержит только Cyclic — блокируем
        if (right.getItem() == net.minecraft.world.item.Items.ENCHANTED_BOOK) {
            var stored = net.minecraft.world.item.EnchantedBookItem.getEnchantments(right);
            boolean allCyclic = !stored.isEmpty();
            for (int i = 0; i < stored.size(); i++) {
                var tag = stored.getCompound(i);
                String enchId = tag.getString("id");
                if (!enchId.startsWith("cyclic:")) { allCyclic = false; break; }
            }
            if (allCyclic && !stored.isEmpty()) {
                event.setOutput(net.minecraft.world.item.ItemStack.EMPTY);
                event.setCost(0);
            }
        }
    }

    // ─── Перехват чата для NPC-диалогов ──────────────────────────────────────────

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!NpcConversationManager.INSTANCE.isInConversation(player)) return;

        // Отменяем обычную отправку сообщения в чат
        event.setCanceled(true);
        NpcConversationManager.INSTANCE.handleMessage(player, event.getMessage().getString());
    }

    // ─── Блокировка крафта предметов blueprint-групп в ванильном верстаке ───────
    //
    // Если крафт происходит НЕ в нашем SimulationCraftingMenu — уничтожаем результат
    // и возвращаем ингредиенты в инвентарь (очищаем сетку, чтобы прервать shift-click-цикл).
    // Предметы из групп 1/2/3 можно крафтить только через Технический верстак с Blueprint.

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        // Крафт из нашего верстака — всегда разрешён
        if (SimulationCraftingMenu.IS_WORKBENCH_CRAFTING.get()) return;
        if (player.containerMenu instanceof SimulationCraftingMenu) return;

        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;

        String id = Objects.toString(ForgeRegistries.ITEMS.getKey(crafted.getItem()), "");
        if (!SimulationCraftingMenu.requiresBlueprint(id)) return;

        // ── Блокируем результат ───────────────────────────────────────────────
        // crafted — тот же объект, что попадёт в курсор игрока,
        // поэтому обнуление count предотвращает получение предмета.
        crafted.setCount(0);

        // Дополнительная подстраховка: если предмет уже оказался в курсоре
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && carried.getItem() == crafted.getItem()) {
            carried.setCount(0);
        }

        // ── Возвращаем ингредиенты ─────────────────────────────────────────────
        // Обнуляем стек прямо в объекте (без вызова setItem → без slotsChanged),
        // тогда ванильный цикл removeItem(i, 1) пропустит пустые слоты.
        // Копии ингредиентов кладём обратно в инвентарь — ничего не теряется.
        Container matrix = event.getInventory();
        if (matrix != null) {
            for (int i = 0; i < matrix.getContainerSize(); i++) {
                ItemStack ingredient = matrix.getItem(i);
                if (!ingredient.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(ingredient.copy());
                    ingredient.setCount(0); // in-place, не вызывает setChanged()
                }
            }
        }

        player.sendSystemMessage(Component.literal(
                "Для крафта этого предмета используй §eТехнический верстак §fс Blueprint."));
    }

    // ─── Запрет естественного спавна мобов threateningly_mobs ────────────────
    // Belt-and-suspenders: биом-модификатор убирает мобов из спавн-листов,
    // это событие блокирует любые оставшиеся попытки (структуры, командный спавн и т.д.)
    // Исключение: мобы разлома добавляются через addFreshEntity, это событие их не затрагивает.

    private static final java.util.Set<String> SUPPRESSED_MOD_SPAWNS =
            java.util.Set.of("threateningly_mobs", "block_factorys_bosses");

    // ─── Born in Chaos: постоянно заблокированные мобы ───────────────────────
    // Навсегда убраны из Обычного мира (и всех измерений через onCheckSpawn):
    // рыба-проглот, миссионер, вожак гончих, кошмарный сталкер, похититель жизни,
    // падший рыцарь хаоса, матерь пауков, трупная рыба, все тыквоголовые,
    // кони скверны (felsteed).
    private static final java.util.Set<ResourceLocation> BIC_PERMANENT_BAN = java.util.Set.of(
        new ResourceLocation("born_in_chaos_v1", "glutton_fish"),
        new ResourceLocation("born_in_chaos_v1", "missioner"),
        new ResourceLocation("born_in_chaos_v1", "missionary_raider"),
        new ResourceLocation("born_in_chaos_v1", "dire_hound_leader"),
        new ResourceLocation("born_in_chaos_v1", "nightmare_stalker"),
        new ResourceLocation("born_in_chaos_v1", "lifestealer"),
        new ResourceLocation("born_in_chaos_v1", "lifestealer_true_form"),
        new ResourceLocation("born_in_chaos_v1", "fallen_chaos_knight"),
        new ResourceLocation("born_in_chaos_v1", "mother_spider"),
        new ResourceLocation("born_in_chaos_v1", "corpse_fish"),
        // Тыквоголовые — все варианты
        new ResourceLocation("born_in_chaos_v1", "mr_pumpkin"),
        new ResourceLocation("born_in_chaos_v1", "mr_pumpkin_controlled"),
        new ResourceLocation("born_in_chaos_v1", "mrs_pumpkin"),
        new ResourceLocation("born_in_chaos_v1", "senor_pumpkin"),
        new ResourceLocation("born_in_chaos_v1", "sir_pumpkinhead"),
        new ResourceLocation("born_in_chaos_v1", "sir_pumpkinhead_without_horse"),
        new ResourceLocation("born_in_chaos_v1", "pumpkinhead"),
        new ResourceLocation("born_in_chaos_v1", "pumpkin_spirit"),
        new ResourceLocation("born_in_chaos_v1", "pumpkin_dunce"),
        new ResourceLocation("born_in_chaos_v1", "lord_pumpkinhead"),
        new ResourceLocation("born_in_chaos_v1", "lord_pumpkinhead_head"),
        new ResourceLocation("born_in_chaos_v1", "lord_pumpkinhead_withouta_horse"),
        new ResourceLocation("born_in_chaos_v1", "lord_the_headless"),
        new ResourceLocation("born_in_chaos_v1", "sir_the_headless"),
        // Кони скверны (felsteed — верховые животные тыквоголовых)
        new ResourceLocation("born_in_chaos_v1", "felsteed"),
        new ResourceLocation("born_in_chaos_v1", "lords_felsteed"),
        new ResourceLocation("born_in_chaos_v1", "riding_felsteed"),
        new ResourceLocation("born_in_chaos_v1", "riding_lords_felsteed")
    );

    // ─── Born in Chaos: заблокированные до убийства дракона ──────────────────
    // зомби-громила, тыквенный громила, скелет-крушитель
    private static final java.util.Set<ResourceLocation> BIC_DRAGON_GATE = java.util.Set.of(
        new ResourceLocation("born_in_chaos_v1", "zombie_bruiser"),
        new ResourceLocation("born_in_chaos_v1", "pumpkin_bruiser"),
        new ResourceLocation("born_in_chaos_v1", "skeleton_thrasher"),
        new ResourceLocation("born_in_chaos_v1", "skeleton_thrasher_not_despawn")
    );

    @SubscribeEvent
    public static void onCheckSpawn(MobSpawnEvent.PositionCheck event) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (key == null) return;

        // Полная блокировка целых пространств имён
        if (SUPPRESSED_MOD_SPAWNS.contains(key.getNamespace())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            return;
        }
        // BIC: постоянно заблокированные
        if (BIC_PERMANENT_BAN.contains(key)) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            return;
        }
        // BIC: заблокированные до убийства дракона
        if (BIC_DRAGON_GATE.contains(key)) {
            var level = event.getLevel();
            if (level instanceof ServerLevel sl) {
                var saved = SimulationSavedData.get(sl.getServer().overworld());
                if (!saved.isDragonDefeated()) {
                    event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                }
            }
        }
    }

    // ─── BIC: блокировка коней скверны при spawn через addFreshEntity ────────
    // Felsteed создаются мобами-наездниками, не проходят через PositionCheck.
    // Дублируем блокировку через EntityJoinLevelEvent.
    @SubscribeEvent
    public static void onBicHorseJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        var key = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (key != null && BIC_PERMANENT_BAN.contains(key)) {
            event.setCanceled(true);
        }
    }

    // ─── Запечатывающая структура разломов ───────────────────────────────────

    /**
     * Правый клик Фрагментом пространства по magic_crystal_block
     * запускает последовательность запечатывания разломов.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (RiftSealingSequence.INSTANCE.needsTick()) return;

        // Только в Обычном мире
        if (!event.getLevel().dimension().equals(Level.OVERWORLD)) return;

        // Только если держим Фрагмент пространства
        net.minecraft.world.item.ItemStack held = player.getItemInHand(event.getHand());
        if (held.getItem() != ModItems.SPACE_FRAGMENT.get()) return;

        // Только если кликнули по magic_crystal_block
        BlockPos pos = event.getPos();
        net.minecraft.world.level.block.Block clickedBlock = event.getLevel().getBlockState(pos).getBlock();
        net.minecraft.world.level.block.Block crystalBlock =
                ForgeRegistries.BLOCKS.getValue(RiftSealingSequence.CRYSTAL_BLOCK);
        if (crystalBlock == null || !clickedBlock.equals(crystalBlock)) return;

        // Проверяем, что драконы убиты и разломы ещё не запечатаны
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        SimulationSavedData data = SimulationSavedData.get(serverLevel);
        if (!data.isDragonDefeated()) {
            player.sendSystemMessage(Component.literal("§cСначала нужно убить Дракона."));
            return;
        }
        if (data.isRiftsSealed()) {
            player.sendSystemMessage(Component.literal("§9Разломы уже запечатаны."));
            return;
        }

        // Валидируем структуру
        if (!RiftSealingSequence.validate(serverLevel, pos)) {
            player.sendSystemMessage(Component.literal("§cСтруктура построена неверно."));
            return;
        }

        // Потребляем фрагмент
        held.shrink(1);

        // Запускаем последовательность
        RiftSealingSequence.INSTANCE.start(serverLevel, pos);
        event.setCanceled(true);
    }

    // ─── Замена WIP-стендов в финальном замке Twilight Forest на BOMD Лича ─────
    //
    // Twilight Forest размещает в финальном замке 4 стенда брони как заглушки WIP:
    //   "Final Castle WIP."  /  "Join our Discord server to"
    //   "discord.experiment115.com"  /  "follow development of the mod"
    // Все стенды отменяются. Вместо первого ("Final Castle WIP.") спавнится BOMD Лич.
    // Заодно убираем колонну из блоков под каждым стендом (15 блоков вниз, 1×1).

    private static final org.apache.logging.log4j.Logger TF_LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("simulation.TFCastle");

    /** ResourceKey для измерения Twilight Forest. */
    private static final ResourceKey<Level> TF_DIMENSION =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    new ResourceLocation("twilightforest", "twilight_forest"));

    /** Подстроки имён всех WIP-стендов финального замка. */
    private static final String[] TF_WIP_STAND_NAMES = {
        "Final Castle WIP",
        "Join our Discord server",
        "discord.experiment115.com",
        "follow development of the mod"
    };

    /** Запрещает использование эндерперл в замке (пока роофсил активен). */
    @SubscribeEvent
    public static void onEnderpearlThrown(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl pearl)) return;
        if (!event.getLevel().dimension().equals(TF_DIMENSION)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        SimulationSavedData epData = SimulationSavedData.get(sl.getServer().overworld());
        if (!epData.isRoofSealActive()) return;

        event.setCanceled(true);
        if (pearl.getOwner() instanceof ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§cВ замке нельзя использовать эндерперлы!"));
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!event.getLevel().dimension().equals(TF_DIMENSION)) return;

        net.minecraft.network.chat.Component customName = stand.getCustomName();
        if (customName == null) return;
        String nameStr = customName.getString();

        boolean isWipStand = false;
        for (String marker : TF_WIP_STAND_NAMES) {
            if (nameStr.contains(marker)) { isWipStand = true; break; }
        }
        if (!isWipStand) return;

        // Отменяем стенд — он не войдёт в мир и не сохранится в NBT чанка
        event.setCanceled(true);
        TF_LOGGER.info("[TFCastle] Canceled WIP armor stand: \"{}\"", nameStr);

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        double x = stand.getX(), y = stand.getY(), z = stand.getZ();
        net.minecraft.core.BlockPos standPos = stand.blockPosition();
        boolean spawnLich = nameStr.contains("Final Castle WIP");

        // Откладываем на следующий тик: убираем колонну под стендом + спавним Лича
        serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                serverLevel.getServer().getTickCount() + 1,
                () -> {
                    clearColumnBelow(serverLevel, standPos);
                    if (spawnLich) {
                        // Страж замка (Underworld Knight) больше не спавнится — он только мешал.
                        // "Final Castle WIP." стенд — единственная точка центрального
                        // корпуса замка с фиксированной формой/ориентацией (всегда SOUTH).
                        // Захватываем её как якорь для последующих телепортов.
                        SimulationSavedData data = SimulationSavedData.get(serverLevel.getServer().overworld());
                        if (!data.hasCastleAnchor()) {
                            data.setCastleAnchorPos(standPos);
                            TF_LOGGER.info("[TFCastle] Captured castle anchor at {}", standPos);
                        }
                    }
                }
        ));
    }

    /**
     * Убирает колонну под WIP-стендом.
     * Область (относительно стенда): x ∈ [-2, +2], z ∈ [-2, +2], y ∈ [-3, -21].
     */
    private static void clearColumnBelow(ServerLevel level,
                                         net.minecraft.core.BlockPos standPos) {
        net.minecraft.world.level.block.state.BlockState air =
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        int cleared = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -5; dy >= -21; dy--) {
                    net.minecraft.core.BlockPos pos = standPos.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 3);
                        cleared++;
                    }
                }
            }
        }
        TF_LOGGER.info("[TFCastle] Cleared {} blocks around column at ({}, {}, {})",
                cleared, standPos.getX(), standPos.getY(), standPos.getZ());
    }

    private static void spawnTFCastleLich(ServerLevel level, double x, double y, double z) {
        net.minecraft.world.entity.EntityType<?> knightType = ForgeRegistries.ENTITY_TYPES
                .getValue(new ResourceLocation("block_factorys_bosses", "underworld_knight"));

        if (knightType == null) {
            TF_LOGGER.error("[TFCastle] underworld_knight entity type not found!");
            return;
        }

        net.minecraft.world.entity.Entity entity = knightType.create(level);
        if (entity == null) {
            TF_LOGGER.error("[TFCastle] Failed to create UnderworldKnightEntity instance.");
            return;
        }

        // Имя и HP
        entity.setCustomName(net.minecraft.network.chat.Component.literal("Страж замка"));
        entity.setCustomNameVisible(false);
        entity.addTag("tf_castle_guardian"); // маркер для отмены дропа

        double scale = getBossScale(level);
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            var maxHealth = living.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                double hp = 400.0 * scale;
                maxHealth.setBaseValue(hp);
                living.setHealth((float) hp);
            }
            var dmg = living.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * scale);
        }

        entity.moveTo(x, y, z, 0f, 0f);
        level.addFreshEntity(entity);
        TF_LOGGER.info("[TFCastle] Spawned Страж замка (UnderworldKnight) at ({}, {}, {}).",
                (int) x, (int) y, (int) z);
    }

    // ─── Fall-resistance для мобов из разлома ────────────────────────────────

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        // Мобы с тегом "rift_mob" не получают урон от падения —
        // они выпадают из разлома в небе и должны долететь до земли живыми.
        if (event.getEntity().getTags().contains("rift_mob")) {
            event.setCanceled(true);
        }
    }

    // ─── Дроп осколков из мобов разломов ─────────────────────────────────────

    /**
     * Заменяет стандартный дроп мобов разлома на осколки пространства.
     *
     * <p>Группы по HP для обычных разломов:
     * <ul>
     *   <li>&lt; 30 HP  → 1–3 осколка</li>
     *   <li>30–60 HP → 2–6 осколков</li>
     *   <li>&gt; 60 HP  → 3–9 осколков</li>
     * </ul>
     * Элитный разлом (тег {@code rift_type_elite}): 10–20 осколков независимо от HP.
     */
    // ─── Урон от Тирана (Inferno) / снарядов — делим на 3 ────────────────────────

    @SubscribeEvent
    public static void onInfernoDamage(LivingHurtEvent event) {
        net.minecraft.world.damagesource.DamageSource source = event.getSource();

        // Прямой атакующий (снаряд или моб)
        net.minecraft.world.entity.Entity direct = source.getDirectEntity();
        // Владелец снаряда / источник урона
        net.minecraft.world.entity.Entity attacker = source.getEntity();

        boolean fromInferno =
                (direct  != null && direct.getTags().contains("sealed_rift_boss")) ||
                (attacker != null && attacker.getTags().contains("sealed_rift_boss"));
        if (!fromInferno) return;

        // Защита от собственных снарядов: Тиран не получает урон от себя
        if (event.getEntity().getTags().contains("sealed_rift_boss")) {
            event.setCanceled(true);
            return;
        }

        // Урон по игрокам/прочим — режем в 2.3 раза
        event.setAmount(event.getAmount() / 2.3f);
    }

    // ─── Битва с боссом замка: неуязвимость на волнах + удержание порога HP ──────

    @SubscribeEvent
    public static void onCastleBossHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        CastleBossFightTask.onBossHurt(event);
    }

    /**
     * Мобы замка с тегом {@link CastleSpawnManager#TEAM_TAG} (включая босса) не выбирают
     * друг друга целью — иначе мобы волн агрятся на босса и помогают игроку его убить.
     * Это корень проблемы: без отмены таргета они всё равно подбегают и бьют союзников,
     * а отмена одного лишь урона ({@link #onCastleTeamDamage}) этого не лечит.
     */
    @SubscribeEvent
    public static void onCastleTeamTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target == null) return;
        if (!target.getTags().contains(CastleSpawnManager.TEAM_TAG)) return;
        if (event.getEntity().getTags().contains(CastleSpawnManager.TEAM_TAG)) {
            event.setCanceled(true);
        }
    }

    /** Мобы замка с тегом {@link CastleSpawnManager#TEAM_TAG} (включая босса) не наносят урон друг другу. */
    @SubscribeEvent
    public static void onCastleTeamDamage(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.getTags().contains(CastleSpawnManager.TEAM_TAG)) return;

        Entity attacker = event.getSource().getEntity();
        if (attacker == null) attacker = event.getSource().getDirectEntity();
        if (attacker != null && attacker.getTags().contains(CastleSpawnManager.TEAM_TAG)) {
            event.setCanceled(true);
        }
    }

    /** Запрещает главному боссу замка ломать блоки (лестницы и т.п.). */
    @SubscribeEvent
    public static void onCastleBossBlockBreak(LivingDestroyBlockEvent event) {
        if (event.getEntity().getTags().contains(CastleBossFightTask.MAIN_BOSS_TAG)) {
            event.setCanceled(true);
        }
    }

    // ─── Отмена дропа у специальных боссов (Страж замка, Inferno запечатывания) ──

    // ─── Блокировка строительства в арене замкового стража ───────────────────

    /** Запрещает ломать блоки игрокам с активной печатью замка. */
    @SubscribeEvent
    public static void onBuildLockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        if (SimulationSavedData.get(serverLevel.getServer().overworld()).isBuildLocked(player.getUUID())) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§8[Система] §7Древняя печать не позволяет ломать блоки здесь."));
        }
    }

    /** Запрещает ставить блоки игрокам с активной печатью замка. Маяк на позицию распечатывания — исключение. */
    @SubscribeEvent
    public static void onBuildLockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        SimulationSavedData data = SimulationSavedData.get(serverLevel.getServer().overworld());
        if (!data.isBuildLocked(player.getUUID())) return;

        // Разрешаем установку маяка на ожидаемую позицию (для распечатывания поля)
        BlockPos expectedBeacon = CastleRoofSealTask.getExpectedBeaconPos();
        if (event.getPlacedBlock().getBlock() == net.minecraft.world.level.block.Blocks.BEACON
                && expectedBeacon != null && event.getPos().equals(expectedBeacon)) {
            return; // не отменяем — маяк пройдёт, onBeaconPlaced поймает его
        }

        event.setCanceled(true);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§8[Система] §7Древняя печать не позволяет ставить блоки здесь."));
    }

    private static final org.apache.logging.log4j.Logger BEACON_LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("simulation.BeaconPlace");

    /** Перехватывает установку маяка на позицию распечатывания и запускает следующую фазу. */
    @SubscribeEvent
    public static void onBeaconPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getPlacedBlock().getBlock() != net.minecraft.world.level.block.Blocks.BEACON) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos placed = event.getPos();
        BlockPos expectedBeacon = CastleRoofSealTask.getExpectedBeaconPos();

        BEACON_LOGGER.info("[BeaconPlace] Маяк поставлен на {} | ожидается {} | совпадение={}",
                placed, expectedBeacon, placed.equals(expectedBeacon));

        if (expectedBeacon == null || !placed.equals(expectedBeacon)) return;

        SimulationSavedData data = SimulationSavedData.get(serverLevel.getServer().overworld());
        CastleRoofSealTask.onBeaconPlaced(serverLevel, data);
    }

    /** Выпадение castle_key с мобов замка, у точки спавна которых есть keyid. */
    @SubscribeEvent
    public static void onCastleSpawnDeath(LivingDeathEvent event) {
        CastleSpawnManager.onLivingDeath(event);
    }

    @SubscribeEvent
    public static void onCastleGuardianDrops(LivingDropsEvent event) {
        var tags = event.getEntity().getTags();
        // Стражи, боссы и все мобы замка (simulation_castle_team) не дропают лут — только ключи через onLivingDeath
        if (tags.contains("tf_castle_guardian")
                || tags.contains("sealed_rift_boss")
                || tags.contains(com.eternity.simulation.castle.CastleSpawnManager.TEAM_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRiftMobDrops(LivingDropsEvent event) {
        net.minecraft.world.entity.LivingEntity entity = event.getEntity();
        if (!entity.getTags().contains("rift_mob")) return;

        event.setCanceled(true);   // убираем весь стандартный дроп

        if (!(entity.level() instanceof ServerLevel level)) return;

        // Определяем предмет по типу разлома
        net.minecraft.world.item.Item shard;
        boolean isElite = entity.getTags().contains("rift_type_elite");
        if (isElite) {
            shard = ModItems.SPACE_SHARD_ELITE.get();
        } else if (entity.getTags().contains("rift_type_purple")) {
            shard = ModItems.SPACE_SHARD_PURPLE.get();
        } else if (entity.getTags().contains("rift_type_blue")) {
            shard = ModItems.SPACE_SHARD_BLUE.get();
        } else {
            shard = ModItems.SPACE_SHARD_RED.get();
        }

        // Определяем количество по HP
        int count;
        if (isElite) {
            count = 10 + level.random.nextInt(11);          // 10–20
        } else {
            float hp = entity.getMaxHealth();
            if (hp < 30f) {
                count = 1 + level.random.nextInt(3);        // 1–3
            } else if (hp <= 60f) {
                count = 2 + level.random.nextInt(5);        // 2–6
            } else {
                count = 3 + level.random.nextInt(7);        // 3–9
            }
        }

        ItemEntity drop = new ItemEntity(level,
                entity.getX(), entity.getY(), entity.getZ(),
                new ItemStack(shard, count));
        level.addFreshEntity(drop);
    }

    // ─── Скейл мобов по времени после убийства дракона ──────────────────────
    //
    // Через 10 игровых дней после убийства дракона мобы получают:
    //   +1/20 HP (5%) и +1/10 атаки (10%) за каждые 10 дней.
    // Максимум 2× для обоих показателей.
    // Не работает на TM, DivineRPG, боссов и entities из mob_scaling_exempt.
    // Тег "sim_scaled" предотвращает повторное применение при перезагрузке чанка.

    private static final String MOB_SCALED_TAG = "sim_scaled";
    private static final java.util.Set<String> SCALING_EXCLUDED_NAMESPACES =
            java.util.Set.of("threateningly_mobs", "divinerpg", "block_factorys_bosses");
    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> MOB_SCALING_EXEMPT_TAG =
            net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    new ResourceLocation("simulation", "mob_scaling_exempt"));

    @SubscribeEvent
    public static void onMobSpawnForScaling(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) return;
        if (mob.getTags().contains(MOB_SCALED_TAG)) return; // уже масштабирован
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        SimulationSavedData data = SimulationSavedData.get(sl.getServer().overworld());
        if (!data.isDragonDefeated()) { mob.addTag(MOB_SCALED_TAG); return; }

        long defeatedAt = data.getDragonDefeatedAt();
        if (defeatedAt <= 0) { mob.addTag(MOB_SCALED_TAG); return; }

        // Проверяем исключения по namespace
        var key = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (key != null && SCALING_EXCLUDED_NAMESPACES.contains(key.getNamespace())) {
            mob.addTag(MOB_SCALED_TAG); return;
        }

        // Исключаем тег mob_scaling_exempt (боссы, жители и т.д.)
        if (mob.getType().is(MOB_SCALING_EXEMPT_TAG)) { mob.addTag(MOB_SCALED_TAG); return; }

        // Исключаем боссов (у них свой скейл по игрокам)
        if (mob.getType().is(SCALED_BOSSES_TAG)) { mob.addTag(MOB_SCALED_TAG); return; }

        // Считаем прожитые игровые дни с момента убийства дракона
        long elapsedTicks = sl.getServer().overworld().getGameTime() - defeatedAt;
        double daysElapsed = elapsedTicks / 24000.0;

        // +5% HP за каждые 10 дней, cap 2×
        double hpScale     = Math.min(2.0, 1.0 + daysElapsed * 0.005);
        // +10% атаки за каждые 10 дней, cap 2×
        double attackScale = Math.min(2.0, 1.0 + daysElapsed * 0.01);

        mob.addTag(MOB_SCALED_TAG);

        if (hpScale > 1.001) {
            var maxHp = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHp != null) {
                double newHp = maxHp.getBaseValue() * hpScale;
                maxHp.setBaseValue(newHp);
                mob.setHealth((float) newHp);
            }
        }
        if (attackScale > 1.001) {
            var atk = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue(atk.getBaseValue() * attackScale);
        }
    }

    // ─── Задача 1 + 2: дроп Осколка и снятие блокировки при гибели дракона ───

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (!(dragon.level() instanceof ServerLevel serverLevel)) return;

        var fight = serverLevel.getDragonFight();
        if (fight == null || fight.hasPreviouslyKilledDragon()) return;

        SimulationSavedData.get(serverLevel).setDragonDefeated(true);

        ItemEntity shard = new ItemEntity(
            serverLevel,
            dragon.getX(), dragon.getY(), dragon.getZ(),
            new ItemStack(ModItems.SIMULATION_SHARD.get())
        );
        serverLevel.addFreshEntity(shard);
    }

    /** При гибели стража замка снимаем печать строительства с игроков рядом. */
    @SubscribeEvent
    public static void onCastleGuardianDeath(LivingDeathEvent event) {
        if (!event.getEntity().getTags().contains("tf_castle_guardian")) return;
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        SimulationSavedData data = SimulationSavedData.get(serverLevel.getServer().overworld());
        data.setCastleGuardianDefeated(true);
        BlockPos deathPos = event.getEntity().blockPosition();

        for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (!data.isBuildLocked(p.getUUID())) continue;
            if (p.level() != serverLevel) continue;
            if (!p.blockPosition().closerThan(deathPos, 150)) continue;

            data.setBuildLocked(p.getUUID(), false);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§8[Система] §7Печать замка спадает — теперь здесь снова можно строить."));
        }
    }

    // ─── Блокировка порталов по прогрессии ──────────────────────────────────

    /** Сумеречный Лес — единственное открытое измерение после дракона (pre-alpha). */
    private static final ResourceKey<Level> TWILIGHT_FOREST = ResourceKey.create(
        net.minecraft.core.registries.Registries.DIMENSION,
        new ResourceLocation("twilightforest", "twilight_forest")
    );

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceKey<Level> destination = event.getDimension();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());

        // ── Энд заблокирован пока не посещён Нижний мир ─────────────────────
        if (destination == Level.END && !data.hasEnteredNether()) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§8Портал не реагирует. Что-то удерживает его закрытым."));
            return;
        }

        // ── Ванильные измерения без ограничений ──────────────────────────────
        if (destination == Level.NETHER || destination == Level.END || destination == Level.OVERWORLD) return;

        // ── Модовые измерения ─────────────────────────────────────────────────
        if (!data.isDragonDefeated()) {
            // Дракон ещё жив — все модовые измерения закрыты
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§8Путь закрыт. Сначала победи того, кто охраняет Край."));
            return;
        }

        // Дракон убит — Сумеречный Лес открыт
        if (destination.equals(TWILIGHT_FOREST)) return;

        // Остальные измерения: WIP (если не разблокированы командой)
        if (!data.isPortalWipUnlocked()) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7[WIP] §8Этот путь ещё не открыт в текущей версии."));
        }
    }

    // ─── Задача 3: автоспавн разломов Gateways to Eternity ───

    private static final int GATEWAY_SPAWN_INTERVAL = 20 * 60 * 5;
    private static int tickCounter = 0;

    // Счётчик для EncounterManager (каждые 100 тиков = 5 сек)
    private static int encounterTickCounter = 0;
    private static int fieldBoundaryTick    = 0;

    /**
     * Проверяет, не вышел ли кто-то за периметр силового поля.
     * Пока {@link CastleRoofSealTask} активен (поле закрыто) — телепортирует нарушителя
     * на маркер {@code 5spawnpoint}.
     */
    private static void tickFieldBoundary() {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        if (!data.isRoofSealActive()) return;

        net.minecraft.server.level.ServerLevel tfLevel = server.getLevel(com.eternity.simulation.castle.CastleConstants.TWILIGHT_FOREST_DIM);
        if (tfLevel == null) return;

        // Ищем маркер 5spawnpoint
        net.minecraft.core.BlockPos spawnMarker = null;
        for (com.eternity.simulation.castle.CastleDataMarker marker : data.getCastleMarkers()) {
            if (marker.has("5spawnpoint")) { spawnMarker = marker.pos(); break; }
        }
        if (spawnMarker == null) return;

        final net.minecraft.core.BlockPos tp = spawnMarker;
        for (ServerPlayer player : tfLevel.players()) {
            if (com.eternity.simulation.castle.CastleRoofSealTask.isOutsideField(player.blockPosition())) {
                player.teleportTo(tp.getX() + 0.5, tp.getY(), tp.getZ() + 0.5);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Батчевая зачистка замка (если запущена через /simcastle clear)
        CastleClearTask.tick();

        // Батчевое продление синего силового поля вниз (если запущено через /simcastle forcefield)
        CastleForceFieldTask.tick();

        // Батчевое выравнивание ландшафта вокруг замка (если запущено через /simcastle terrain)
        CastleTerrainTask.tick();

        // Батчевая засыпка ям от снесённых башен (если запущено через /simcastle terrainfill)
        CastleTerrainFillTask.tick();

        // Батчевое доделывание нижних частей башен (если запущено через /simcastle towers)
        CastleTowerFixTask.tick();

        // Спавн мобов замка по приближению + триггер floor1_boss
        CastleSpawnManager.tick();

        // Дымовые столбы и появление мобов с отложенным спавном (каждый тик, без интервала)
        CastleSpawnManager.tickPendingSpawns();

        // Батчевая стройка лестниц после remove_statue (если запущена через CastleRevealTask.start)
        CastleRevealTask.tick();

        // Проверка пьедесталов головоломки синей башни (раз в 20 тиков)
        CastlePedestalPuzzleTask.tick();

        // Точки возрождения замка (маркеры Nspawnpoint), раз в 20 тиков
        CastleSpawnPointTask.tick();

        // Битва с главным боссом 2-го этажа (underworld_knight, волны boss_fight)
        CastleBossFightTask.tick();

        // Распечатывание силового поля крыши замка (фиолетовые столбы, маяк, кольца)
        CastleRoofSealTask.tick();

        // Граница поля: если поле активно и игрок вышел за XZ-периметр — телепортируем на 5spawnpoint
        if (++fieldBoundaryTick >= 40) {
            fieldBoundaryTick = 0;
            tickFieldBoundary();
        }

        // Тикаем последовательность запечатывания (каждый тик, она сама отслеживает время)
        if (RiftSealingSequence.INSTANCE.needsTick()) {
            MinecraftServer sealServer = ServerLifecycleHooks.getCurrentServer();
            if (sealServer != null) RiftSealingSequence.INSTANCE.tick(sealServer);
        }

        // Проверяем таймеры встреч и разломов каждые 100 тиков
        if (++encounterTickCounter >= 100) {
            encounterTickCounter = 0;
            MinecraftServer encounterServer = ServerLifecycleHooks.getCurrentServer();
            if (encounterServer != null) {
                EncounterManager.INSTANCE.tick(encounterServer);
                RiftManager.INSTANCE.tick(encounterServer);
                // Обновляем lastSeen для всех онлайн-игроков
                SimulationSavedData tickData = SimulationSavedData.get(encounterServer.overworld());
                for (ServerPlayer p : encounterServer.getPlayerList().getPlayers()) {
                    tickData.recordPlayerSeen(p.getUUID());
                }
            }
        }

        if (++tickCounter < GATEWAY_SPAWN_INTERVAL) return;
        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.overworld();
        if (!SimulationSavedData.get(overworld).isDragonDefeated()) return;

        List<ServerPlayer> players = overworld.players();
        if (players.isEmpty()) return;

        ServerPlayer target = players.get(overworld.random.nextInt(players.size()));

        int range = 100 + overworld.random.nextInt(200);
        double angle = Math.toRadians(overworld.random.nextInt(360));
        int x = (int) (target.getX() + Math.cos(angle) * range);
        int z = (int) (target.getZ() + Math.sin(angle) * range);
        int y = overworld.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

        BlockPos spawnPos = new BlockPos(x, y, z);

        // TODO: Задача 3 — раскомментировать после изучения API Gateways to Eternity
        // GatewayEntity gateway = new GatewayEntity(..., overworld, ...);
        // overworld.addFreshEntity(gateway);
    }
}
