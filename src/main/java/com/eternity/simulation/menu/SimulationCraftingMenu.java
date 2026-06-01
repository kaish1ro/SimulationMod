package com.eternity.simulation.menu;

import com.eternity.simulation.BlueprintGroups;
import com.eternity.simulation.ModBlocks;
import com.eternity.simulation.crafting.ModRecipeTypes;
import com.eternity.simulation.crafting.WorkbenchRecipe;
import com.eternity.simulation.items.BlueprintItem;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public class SimulationCraftingMenu extends AbstractContainerMenu {

    // ── Флаг: крафт происходит внутри нашего верстака ─────────────────────────
    // Сервер однопоточный, поэтому ThreadLocal достаточен.
    // ModEvents.onItemCrafted читает этот флаг, чтобы не блокировать наш верстак.
    public static final ThreadLocal<Boolean> IS_WORKBENCH_CRAFTING =
            ThreadLocal.withInitial(() -> false);

    // ── Индексы слотов ────────────────────────────────────────────────────────
    public static final int RESULT_SLOT         = 0;
    public static final int CRAFT_SLOT_START    = 1;   // 1..9
    public static final int BLUEPRINT_SLOT      = 10;
    public static final int PLAYER_INV_START    = 11;  // 11..37 (27 слотов)
    public static final int PLAYER_HOTBAR_START = 38;  // 38..46 (9 слотов)
    public static final int PLAYER_HOTBAR_END   = 46;

    // ── GUI-координаты (константы используются и экраном, и JEI) ─────────────
    public static final int GRID_X      = 30;
    public static final int GRID_Y      = 17;
    public static final int RESULT_X    = 124;
    public static final int RESULT_Y    = 35;
    public static final int BLUEPRINT_X = 91;
    public static final int BLUEPRINT_Y = 59;

    // ── Контейнеры ────────────────────────────────────────────────────────────
    // В Forge 1.20.1 CraftingContainer — интерфейс; конкретная реализация TransientCraftingContainer.
    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots = new ResultContainer();
    private final SimpleContainer blueprintContainer = new SimpleContainer(1);
    private final ContainerLevelAccess access;
    private final Player player;

    public SimulationCraftingMenu(int windowId, Inventory playerInventory,
                                   ContainerLevelAccess access) {
        super(ModMenuTypes.SIMULATION_WORKBENCH.get(), windowId);
        this.access = access;
        this.player = playerInventory.player;
        this.craftSlots = new TransientCraftingContainer(this, 3, 3);

        // При смене blueprint-слота — пересчитать результат
        this.blueprintContainer.addListener(this::slotsChanged);

        // ── Слот результата (0) ──────────────────────────────────────────────
        this.addSlot(new ResultSlot(playerInventory.player, craftSlots, resultSlots, 0,
                RESULT_X, RESULT_Y) {
            @Override
            public void onTake(Player player, ItemStack stack) {
                String resultId = Objects.toString(
                        ForgeRegistries.ITEMS.getKey(stack.getItem()), "");
                if (requiresBlueprint(resultId)) {
                    ItemStack bp = SimulationCraftingMenu.this
                            .slots.get(BLUEPRINT_SLOT).getItem();
                    if (!bp.isEmpty()) {
                        bp.shrink(1);
                        SimulationCraftingMenu.this
                                .slots.get(BLUEPRINT_SLOT).setChanged();
                    }
                }
                // Помечаем, что ItemCraftedEvent будет от нашего верстака →
                // ModEvents.onItemCrafted не должен блокировать этот крафт.
                IS_WORKBENCH_CRAFTING.set(true);
                try {
                    super.onTake(player, stack);
                } finally {
                    IS_WORKBENCH_CRAFTING.set(false);
                }
            }
        });

        // ── Сетка 3×3 (слоты 1–9) ────────────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(craftSlots, col + row * 3,
                        GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // ── Blueprint-слот (10) ──────────────────────────────────────────────
        this.addSlot(new Slot(blueprintContainer, 0, BLUEPRINT_X, BLUEPRINT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BlueprintItem;
            }
        });

        // ── Инвентарь игрока (11–37) ─────────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }

        // ── Хотбар (38–46) ───────────────────────────────────────────────────
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    // ── Пересчёт результата (server-side) ────────────────────────────────────

    @Override
    public void slotsChanged(Container container) {
        this.access.execute((level, pos) -> updateResult(level));
    }

    private void updateResult(Level level) {
        if (level.isClientSide()) return;
        ServerPlayer serverPlayer = (ServerPlayer) this.player;

        java.util.Optional<WorkbenchRecipe> optional = level.getServer()
                .getRecipeManager().getRecipeFor(ModRecipeTypes.WORKBENCH_CRAFTING.get(), craftSlots, level);

        ItemStack result = ItemStack.EMPTY;

        if (optional.isPresent()) {
            WorkbenchRecipe recipe = optional.get();
            ItemStack assembled = recipe.assemble(craftSlots, level.registryAccess());

            if (!assembled.isEmpty() && assembled.isItemEnabled(level.enabledFeatures())) {
                int group = recipe.getBlueprintGroup();
                boolean needed = group > 0;
                boolean hasIt  = !needed || hasBlueprintForGroup(group);
                if (hasIt) result = assembled;
            }
        }

        resultSlots.setItem(0, result);
        this.setRemoteSlot(0, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                this.containerId, this.incrementStateId(), 0, result));
    }

    // ── Проверки blueprint ────────────────────────────────────────────────────

    public static boolean requiresBlueprint(String itemId) {
        return BlueprintGroups.GROUP1.contains(itemId)
                || BlueprintGroups.GROUP2.contains(itemId)
                || BlueprintGroups.GROUP3.contains(itemId);
    }

    private boolean hasBlueprintForGroup(int requiredGroup) {
        ItemStack bp = this.slots.get(BLUEPRINT_SLOT).getItem();
        if (bp.isEmpty() || !(bp.getItem() instanceof BlueprintItem blueprint)) return false;
        return blueprint.getGroup() >= requiredGroup;
    }

    // ── Закрытие меню ─────────────────────────────────────────────────────────

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> {
            this.clearContainer(player, this.craftSlots);
            this.clearContainer(player, this.blueprintContainer);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(
                this.access, player, ModBlocks.SIMULATION_WORKBENCH.get());
    }

    // ── Shift-клик ────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack current = slot.getItem();
        ItemStack copy    = current.copy();

        if (slotIndex == RESULT_SLOT) {
            if (!this.moveItemStackTo(current, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END + 1, false)
                    && !this.moveItemStackTo(current, PLAYER_INV_START, PLAYER_HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(current, copy);
            slot.onTake(player, copy);

        } else if (slotIndex >= PLAYER_INV_START) {
            if (current.getItem() instanceof BlueprintItem) {
                if (!this.moveItemStackTo(current, BLUEPRINT_SLOT, BLUEPRINT_SLOT + 1, false)) {
                    if (slotIndex < PLAYER_HOTBAR_START) {
                        if (!this.moveItemStackTo(current, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END + 1, false))
                            return ItemStack.EMPTY;
                    } else {
                        if (!this.moveItemStackTo(current, PLAYER_INV_START, PLAYER_HOTBAR_START, false))
                            return ItemStack.EMPTY;
                    }
                }
            } else {
                return ItemStack.EMPTY;
            }

        } else if (slotIndex == BLUEPRINT_SLOT) {
            if (!this.moveItemStackTo(current, PLAYER_INV_START, PLAYER_HOTBAR_END + 1, false))
                return ItemStack.EMPTY;

        } else {
            if (!this.moveItemStackTo(current, PLAYER_INV_START, PLAYER_HOTBAR_END + 1, false))
                return ItemStack.EMPTY;
        }

        if (current.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (current.getCount() == copy.getCount()) return ItemStack.EMPTY;

        return copy;
    }

    public CraftingContainer getCraftSlots() { return craftSlots; }
}
