package com.eternity.simulation.screen;

import com.eternity.simulation.menu.SimulationCraftingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class SimulationWorkbenchScreen extends AbstractContainerScreen<SimulationCraftingMenu> {

    // Используем стандартный фон ванильного верстака для сетки, стрелки и результата.
    private static final ResourceLocation CRAFTING_TABLE_TEX =
            new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");


    public SimulationWorkbenchScreen(SimulationCraftingMenu menu,
                                     Inventory playerInventory,
                                     Component title) {
        super(menu, playerInventory, title);
        // Стандартные размеры ванильного верстака — blueprint-слот вписывается
        // в свободную область между результатом и инвентарём игрока.
        this.imageWidth  = 176;
        this.imageHeight = 166;
        // Сдвигаем стандартный заголовок
        this.titleLabelY = 6;
    }

    // ── Фон ───────────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick,
                             int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Стандартный фон верстака (176×166)
        guiGraphics.blit(CRAFTING_TABLE_TEX, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Blueprint-слот: blit из того же crafting_table.png → тот же RenderType.guiTextured,
        // поэтому рендерится строго после основного фона (в порядке вызовов), не перекрывается им.
        // UV (29, 16) — один из свободных 18×18 слотов на текстуре crafting_table.png.
        guiGraphics.blit(CRAFTING_TABLE_TEX,
                x + SimulationCraftingMenu.BLUEPRINT_X - 1,
                y + SimulationCraftingMenu.BLUEPRINT_Y - 1,
                29, 16, 18, 18);
    }

    // ── Рендер (фон + предметы + подписи) ────────────────────────────────────

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
