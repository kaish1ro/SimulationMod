package com.eternity.simulation.crafting;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

import javax.annotation.Nullable;

/**
 * Сериализатор для WorkbenchRecipe.
 * JSON-формат совпадает с minecraft:crafting_shaped + дополнительное поле:
 *   "blueprint_group": 0  // 0 = без Blueprint, 1/2/3 = группа
 */
public class WorkbenchRecipeSerializer implements RecipeSerializer<WorkbenchRecipe> {

    private static final ShapedRecipe.Serializer SHAPED_SERIALIZER = new ShapedRecipe.Serializer();

    @Override
    public WorkbenchRecipe fromJson(ResourceLocation id, JsonObject json) {
        int group = 0;
        if (json.has("blueprint_group")) {
            group = json.get("blueprint_group").getAsInt();
        }
        // Убираем наше поле, чтобы ShapedRecipe.Serializer не упал на неизвестном ключе
        JsonObject copy = json.deepCopy();
        copy.remove("blueprint_group");
        // ShapedRecipe.Serializer.fromJson требует "type" — восстанавливаем оригинальный
        // (он читает поля из json напрямую, type-поле не нужно serializer'у, только dispatcher'у)
        ShapedRecipe inner = SHAPED_SERIALIZER.fromJson(id, copy);
        return new WorkbenchRecipe(inner, group);
    }

    @Override
    public WorkbenchRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        int group = buf.readVarInt();
        ShapedRecipe inner = SHAPED_SERIALIZER.fromNetwork(id, buf);
        return new WorkbenchRecipe(inner, group);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, WorkbenchRecipe recipe) {
        buf.writeVarInt(recipe.getBlueprintGroup());
        SHAPED_SERIALIZER.toNetwork(buf, recipe.getInner());
    }
}
