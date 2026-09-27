package cn.autoforged.server_shop_mod_1789689358.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 通用“放入物品”界面：1 个槽位 + 玩家背包。管理员配置、拍卖上架、摊位上架都复用它。
 * 关闭界面时槽内物品会安全退还，避免丢失。
 */
public class DepositMenu extends AbstractContainerMenu {
    private final Container deposit;
    private final boolean ghost;
    private final boolean locked;

    public DepositMenu(int id, Inventory playerInventory, Container deposit) {
        this(id, playerInventory, deposit, false, false);
    }

    /**
     * 需求 11：管理员配置物品时使用幽灵槽位（ghost=true）。
     * 幽灵槽只读取物品数据，不消耗玩家物品、也不允许把槽内物品取出刷取真实物品。
     */
    public DepositMenu(int id, Inventory playerInventory, Container deposit, boolean ghost) {
        this(id, playerInventory, deposit, ghost, false);
    }

    /**
     * 需求 2：locked=true 时放入槽位不可放置物品（交易槽位已满的兜底校验）。
     */
    public DepositMenu(int id, Inventory playerInventory, Container deposit, boolean ghost, boolean locked) {
        super(ModMenus.DEPOSIT.get(), id);
        this.deposit = deposit;
        this.ghost = ghost;
        this.locked = locked;

        if (ghost) {
            this.addSlot(new GhostSlot(deposit, 0, 80, 20));
        } else if (locked) {
            this.addSlot(new LockedSlot(deposit, 0, 80, 20));
        } else {
            this.addSlot(new Slot(deposit, 0, 80, 20));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public Container deposit() {
        return deposit;
    }

    public boolean isGhost() {
        return ghost;
    }

    public boolean isLocked() {
        return locked;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                if (!this.moveItemStackTo(stack, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            ItemStack leftover = this.deposit.getItem(0);
            if (!leftover.isEmpty()) {
                this.deposit.setItem(0, ItemStack.EMPTY);
                // 掉线时玩家数据已经先落盘（PlayerList.remove → save → doCloseContainer），
                // 这时 add 进背包不会被保存，物品会消失，只能掉落到地上。
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        && serverPlayer.hasDisconnected()) {
                    player.drop(leftover.copy(), false);
                    return;
                }
                // add 会原地 shrink 传入栈，剩余量必须显式掉落，否则部分放入时会丢物品。
                ItemStack remaining = leftover.copy();
                player.getInventory().add(remaining);
                if (!remaining.isEmpty()) {
                    player.drop(remaining, false);
                }
            }
        }
    }

    public static DepositMenu clientSide(int id, Inventory playerInventory) {
        return new DepositMenu(id, playerInventory, new SimpleContainer(1));
    }

    /** 需求 11：只能放入/读取，不能取出，避免玩家借管理界面刷出真实物品。 */
    public static final class GhostSlot extends Slot {
        public GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isFake() {
            return true;
        }
    }

    /** 需求 2：交易槽位已满时的锁定槽，服务端拒绝任何放入。 */
    public static final class LockedSlot extends Slot {
        public LockedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
