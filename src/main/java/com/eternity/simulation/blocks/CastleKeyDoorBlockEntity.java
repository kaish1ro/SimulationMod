package com.eternity.simulation.blocks;

import com.eternity.simulation.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Хранит состояние блока двери:
 * <ul>
 *   <li>{@code door_id} — строковый идентификатор; ключ открывает только дверь с совпадающим ID.</li>
 *   <li>{@code locked} — заблокирована ли дверь (требует ключ); после разблокировки
 *       остаётся {@code false} и открывается простым ПКМ.</li>
 * </ul>
 */
public class CastleKeyDoorBlockEntity extends BlockEntity {

    public static final String DEFAULT_ID = "default";

    private String  doorId = DEFAULT_ID;
    private boolean locked = true;   // по умолчанию заперта

    public CastleKeyDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CASTLE_KEY_DOOR_BE_TYPE.get(), pos, state);
    }

    // ── Геттеры / сеттеры ────────────────────────────────────────────────────

    public String getDoorId() { return doorId; }

    public void setDoorId(String id) {
        this.doorId = (id == null || id.isEmpty()) ? DEFAULT_ID : id;
        pushUpdate();
    }

    public boolean isLocked() { return locked; }

    public void setLocked(boolean locked) {
        this.locked = locked;
        pushUpdate();
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("door_id", doorId);
        tag.putBoolean("locked",  locked);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        String id = tag.getString("door_id");
        doorId = (id == null || id.isEmpty()) ? DEFAULT_ID : id;
        // Если тег отсутствует — считаем заперта (безопасный дефолт)
        locked = !tag.contains("locked") || tag.getBoolean("locked");
    }

    // ── Синхронизация с клиентом ─────────────────────────────────────────────

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) load(tag);
    }

    /** Сохраняет изменения и рассылает пакет клиентам. */
    private void pushUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
