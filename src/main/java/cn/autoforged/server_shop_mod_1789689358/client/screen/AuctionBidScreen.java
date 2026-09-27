package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.Config;
import cn.autoforged.server_shop_mod_1789689358.client.ClientShopData;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 需求 2：竞拍（出价）界面。
 * 上方为拍卖物品图标（仅图标，不可拿取/丢弃，悬停显示名称/附魔/属性/NBT/标签），
 * 下方显示当前价格，并提供 -10、-5、自定义金额、+5、+10 的快捷调价，
 * 最下方左侧取消、右侧确认。
 */
public class AuctionBidScreen extends Screen {
    private static final int PANEL_W = 280;
    private static final int PANEL_H = 180;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_GOLD = 0xFFD700;

    private final long id;
    private final ItemStack stack;
    private final long startPrice;
    private long currentBid;
    private long lastBid;
    private final long minIncrement;
    /** 该拍卖是否还在列表里（不在说明已结束，出价没有意义）。 */
    private boolean listed = true;
    private EditBox amountBox;
    private int left;
    private int top;

    public AuctionBidScreen(ClientShopData.AuctionView view) {
        super(Component.translatable("gui.server_shop_mod.input.bid"));
        this.id = view.id;
        this.stack = view.stack.copy();
        this.startPrice = view.startPrice;
        this.currentBid = view.currentBid;
        this.lastBid = view.lastBid;
        this.minIncrement = Math.max(1L, Config.INSTANCE.auctionMinIncrement.get());
    }

    /** 需求：最低出价 = 当前价 + 加价幅度（无人出价时为起拍价）。 */
    private long minimum() {
        return currentBid > 0L ? currentBid + minIncrement : Math.max(1L, startPrice);
    }

    /**
     * 用最新快照刷新当前价：界面打开后别人继续加价时，本界面提示的最低出价也要跟着变，
     * 否则玩家按旧提示出价会被服务端以“出价过低”拒绝。
     */
    private void refreshFromCache() {
        for (ClientShopData.AuctionView view : ClientShopData.auctions) {
            if (view.id == this.id) {
                this.currentBid = view.currentBid;
                this.lastBid = view.lastBid;
                this.listed = true;
                return;
            }
        }
        this.listed = false;
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        int rowY = this.top + 82;
        int x = this.left + 12;
        this.addRenderableWidget(Button.builder(Component.literal("-10"), b -> adjust(-10L))
                .pos(x, rowY).size(36, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("-5"), b -> adjust(-5L))
                .pos(x + 40, rowY).size(36, 20).build());
        this.amountBox = new EditBox(this.font, x + 80, rowY, 80, 20,
                Component.translatable("gui.server_shop_mod.field.amount"));
        this.amountBox.setMaxLength(18);
        this.amountBox.setValue(Long.toString(minimum()));
        this.addRenderableWidget(this.amountBox);
        this.addRenderableWidget(Button.builder(Component.literal("+5"), b -> adjust(5L))
                .pos(x + 164, rowY).size(36, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+10"), b -> adjust(10L))
                .pos(x + 204, rowY).size(36, 20).build());
        int buttonY = this.top + PANEL_H - 28;
        // 需求 2：最下为取消和确认。
        this.addRenderableWidget(Button.builder(Component.translatable("gui.server_shop_mod.cancel"), b -> onClose())
                .pos(this.left + 24, buttonY).size(90, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.server_shop_mod.confirm"), b -> confirm())
                .pos(this.left + PANEL_W - 114, buttonY).size(90, 20).build());
        this.setInitialFocus(this.amountBox);
    }

    private void adjust(long delta) {
        long value;
        try {
            value = Long.parseLong(this.amountBox.getValue().trim());
        } catch (NumberFormatException ex) {
            value = minimum();
        }
        this.amountBox.setValue(Long.toString(Math.max(minimum(), value + delta)));
    }

    private void confirm() {
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "bid");
        tag.putLong("id", this.id);
        tag.putString("amount", this.amountBox.getValue().trim());
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Bricks.panel(g, this.left, this.top, PANEL_W, PANEL_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refreshFromCache();
        super.render(g, mouseX, mouseY, partialTick);
        int cx = this.left + PANEL_W / 2;
        g.drawCenteredString(this.font, this.title, cx, this.top + 10, COLOR_GOLD);
        // 上方物品图标（仅展示，不是可交互槽位）。
        int iconX = cx - 8;
        int iconY = this.top + 26;
        Bricks.slot(g, iconX - 1, iconY - 1);
        if (!this.stack.isEmpty()) {
            g.renderItem(this.stack, iconX, iconY);
            g.renderItemDecorations(this.font, this.stack, iconX, iconY);
        }
        g.drawCenteredString(this.font, this.stack.isEmpty() ? Component.literal("-") : this.stack.getHoverName(),
                cx, this.top + 48, COLOR_TEXT);
        // 需求三：居中显示当前最高出价，并在下方用划掉效果展示上一轮出价。
        if (this.currentBid > 0L) {
            String current = Component.translatable("gui.server_shop_mod.auction_current_bid",
                    ClientShopData.money(this.currentBid)).getString();
            g.drawCenteredString(this.font, current, cx, this.top + 58, COLOR_GOLD);
            if (this.lastBid > 0L) {
                String last = Component.translatable("gui.server_shop_mod.auction_last_bid",
                        ClientShopData.money(this.lastBid)).getString();
                int lastX = cx - this.font.width(last) / 2;
                g.drawString(this.font, last, lastX, this.top + 66, 0x8A8A9A, false);
                g.fill(lastX, this.top + 71, lastX + this.font.width(last), this.top + 72, 0x8A8A9A);
            }
        } else {
            String start = Component.translatable("gui.server_shop_mod.auction_start",
                    ClientShopData.money(this.startPrice)).getString();
            g.drawCenteredString(this.font, start, cx, this.top + 60, COLOR_GOLD);
        }
        g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.bid_min",
                ClientShopData.money(minimum())), cx, this.top + 74, COLOR_MUTED);
        // 明确“出价即扣款、被超价/流拍会退回”，并显示当前余额，避免玩家以为钱被吃掉。
        g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.bid_rule"),
                cx, this.top + 112, COLOR_MUTED);
        g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.bid_balance",
                ClientShopData.money(ClientShopData.balance)), cx, this.top + 124, COLOR_GOLD);
        if (!this.listed) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.auction_gone"),
                    cx, this.top + 138, 0xFF6B6B);
        }
        // 悬停图标显示物品完整信息（名称/附魔/属性/NBT/标签）。
        if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16 && !this.stack.isEmpty()) {
            g.renderTooltip(this.font, this.stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
