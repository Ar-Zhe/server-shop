package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.client.ClientShopData;
import cn.autoforged.server_shop_mod_1789689358.menu.DepositMenu;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** “放入物品”界面。放好物品后点确认，服务端会弹出数值编辑界面。 */
public class DepositScreen extends AbstractContainerScreen<DepositMenu> {
    public DepositScreen(DepositMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - this.imageWidth) / 2;
        int py = (this.height - this.imageHeight) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.server_shop_mod.confirm"), b -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("action", "deposit_confirm");
            PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
        }).pos(px + 62, py + 43).size(52, 16).build());
    }

    @Override
    public void onClose() {
        // 交易中取消“放入物品”时回到交易界面，而不是直接关闭。
        boolean inTrade = !ClientShopData.tradeSelfName.isEmpty();
        super.onClose();
        if (inTrade) {
            Minecraft.getInstance().setScreen(new TradeScreen());
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        Bricks.panel(g, x, y, this.imageWidth, this.imageHeight);
        Bricks.slot(g, x + 80, y + 20);
        g.blit(Bricks.PLAYER_SLOTS, x + 7, y + 83, 0, 0, 162, 76, 162, 76);
    }
}
