package cn.autoforged.server_shop_mod_1789689358.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 管理员发放界面的容器：3 行 × 9 个发放槽 + 管理员背包。
 * <p>
 * 物品槽是真实槽位（服务端权威），管理员放进去的物品会先从自己背包里扣除；
 * 关闭界面但没有点“发送”时，{@link #removed(Player)} 会把槽内物品原样退还，不会丢东西。
 */
public class GrantMenu extends AbstractContainerMenu {
    /** 发放槽数量（与 GrantManager.GRANT_SLOTS 保持一致）。 */
    public static final int GRANT_SLOTS = 27;
    /** 发放界面里玩家背包区域的起始 y（客户端界面按同一套坐标绘制背景）。 */
    public static final int INVENTORY_Y = 146;
    public static final int HOTBAR_Y = 204;

    private final Container grant;

    public GrantMenu(int id, Inventory playerInventory, Container grant) {
        super(ModMenus.GRANT.get(), id);
        this.grant = grant;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(grant, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public Container grant() {
        return grant;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            // 0..26 是发放槽，27..62 是管理员背包（含快捷栏）。
            if (index < GRANT_SLOTS) {
                if (!this.moveItemStackTo(stack, GRANT_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, GRANT_SLOTS, false)) {
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
        if (player.level().isClientSide) {
            return;
        }
        // 掉线时退还的物品必须直接掉到地上：PlayerList.remove 的顺序是
        // 先 this.save(player) 把玩家数据落盘，再 removePlayerImmediately → doCloseContainer → 这里，
        // 此时 add 进背包不会再被保存，重连后物品就没了。vanilla 的 AbstractContainerMenu#clearContainer 同理。
        boolean disconnected = player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected();
        for (int i = 0; i < this.grant.getContainerSize(); i++) {
            ItemStack leftover = this.grant.getItem(i);
            if (leftover.isEmpty()) {
                continue;
            }
            this.grant.setItem(i, ItemStack.EMPTY);
            if (disconnected) {
                player.drop(leftover.copy(), false);
                continue;
            }
            // Inventory.add 会原地 shrink，剩余量必须显式掉出，否则部分放入时会丢物品。
            ItemStack remaining = leftover.copy();
            player.getInventory().add(remaining);
            if (!remaining.isEmpty()) {
                player.drop(remaining, false);
            }
        }
    }

    /** 客户端占位容器：真实内容由服务端同步过来。 */
    public static GrantMenu clientSide(int id, Inventory playerInventory) {
        return new GrantMenu(id, playerInventory, new SimpleContainer(GRANT_SLOTS));
    }
}
