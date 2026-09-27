package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.client.ClientShopData;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 玩家交易界面：左右两栏分别显示自己与对方的物品/金额。
 * 双方都点“同意”后进入现实时间倒计时，倒计时结束后双方再点“确定交换”完成交易。
 */
public class TradeScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 224;
    /** 需求 1：交易界面槽位数量为 6（原为 9）。 */
    private static final int MAX_ITEMS = 6;
    private static final int COL_W = 150;
    private static final int ROW_H = 16;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_GOLD = 0xFFD700;
    private static final int COLOR_GREEN = 0x55FF55;

    private ButtonZone[] zones;

    public TradeScreen() {
        super(Component.translatable("gui.server_shop_mod.trade.title"));
    }

    public void onDataUpdated() {
        // 每帧从 ClientShopData 读取，无需处理。
    }

    @Override
    protected void init() {
        super.init();
        int x = left();
        int y = top();
        int by = y + PANEL_H - 42;
        this.zones = new ButtonZone[]{
                new ButtonZone(x + 10, by, 80, 16, "trade_add_item", "gui.server_shop_mod.trade.add_item"),
                new ButtonZone(x + 94, by, 80, 16, "trade_money_gui", "gui.server_shop_mod.trade.set_money"),
                new ButtonZone(x + 178, by, 80, 16, "trade_agree", "gui.server_shop_mod.trade.agree"),
                new ButtonZone(x + 262, by, 88, 16, "trade_confirm", "gui.server_shop_mod.trade.confirm"),
                new ButtonZone(x + PANEL_W - 96, y + PANEL_H - 22, 86, 16, "trade_cancel",
                        "gui.server_shop_mod.trade.cancel")
        };
    }

    private int left() {
        return (this.width - PANEL_W) / 2;
    }

    private int top() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        Bricks.panel(g, x, y, PANEL_W, PANEL_H);
        g.drawCenteredString(this.font, this.title, x + PANEL_W / 2, y + 8, COLOR_GOLD);

        int colW = COL_W;
        int selfX = x + 10;
        int otherX = x + PANEL_W - 10 - colW;
        drawSide(g, selfX, y + 24, colW, ClientShopData.tradeSelfName, ClientShopData.tradeSelfItems,
                ClientShopData.tradeSelfMoney, ClientShopData.tradeSelfAgreed, ClientShopData.tradeSelfConfirmed,
                true, true, mouseX, mouseY);
        drawSide(g, otherX, y + 24, colW, ClientShopData.tradeOtherName, ClientShopData.tradeOtherItems,
                ClientShopData.tradeOtherMoney, ClientShopData.tradeOtherAgreed, ClientShopData.tradeOtherConfirmed,
                ClientShopData.tradeAccepted, false, mouseX, mouseY);

        // 中间状态与倒计时
        int centerY = y + 96;
        if (!ClientShopData.tradeAccepted) {
            // 需求 2：等待对方同意请求期间，对方那侧灰色不可选。
            g.drawCenteredString(this.font,
                    Component.translatable("gui.server_shop_mod.trade.waiting_accept", ClientShopData.tradeOtherName),
                    x + PANEL_W / 2, centerY, COLOR_GOLD);
        } else if (ClientShopData.tradeAgreedBoth) {
            long countdown = ClientShopData.tradeCountdownMs;
            if (countdown > 0) {
                long seconds = (countdown + 999L) / 1000L;
                g.drawCenteredString(this.font, Component.literal(String.valueOf(seconds)),
                        x + PANEL_W / 2, centerY - 6, COLOR_GOLD);
                g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.trade.countdown"),
                        x + PANEL_W / 2, centerY + 10, COLOR_MUTED);
            } else {
                g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.trade.ready"),
                        x + PANEL_W / 2, centerY, COLOR_GREEN);
            }
        } else {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.trade.waiting"),
                    x + PANEL_W / 2, centerY, COLOR_MUTED);
        }

        for (ButtonZone zone : this.zones) {
            boolean enabled = isEnabled(zone.action);
            boolean hover = mouseX >= zone.x && mouseX < zone.x + zone.w
                    && mouseY >= zone.y && mouseY < zone.y + zone.h;
            Component label = "trade_agree".equals(zone.action) && ClientShopData.tradeSelfAgreed
                    ? Component.translatable("gui.server_shop_mod.trade.agree_off")
                    : Component.translatable(zone.key);
            drawButton(g, zone.x, zone.y, zone.w, zone.h, label, enabled, hover);
            // 需求 2：槽位已满时悬停“放入物品”提示“已超过可交换数量”。
            if (!enabled && hover && "trade_add_item".equals(zone.action)
                    && ClientShopData.tradeSelfItems.size() >= MAX_ITEMS) {
                g.renderTooltip(this.font,
                        Component.translatable("gui.server_shop_mod.trade.full"), mouseX, mouseY);
            }
        }
    }

    private void drawSide(GuiGraphics g, int x, int y, int w, String name, List<ItemStack> items, long money,
            boolean agreed, boolean confirmed, boolean active, boolean self, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + 132, 0xFF202020);
        g.drawString(this.font, name, x + 6, y + 6, COLOR_TEXT, true);
        String moneyText = ClientShopData.money(money);
        g.drawString(this.font, moneyText, x + w - 6 - this.font.width(moneyText), y + 6, COLOR_GOLD, true);
        int rowY = y + 20;
        for (int i = 0; i < items.size() && i < MAX_ITEMS; i++) {
            ItemStack stack = items.get(i);
            int slotY = rowY + i * ROW_H;
            Bricks.slot(g, x + 6, slotY);
            g.renderItem(stack, x + 6, slotY);
            g.renderItemDecorations(this.font, stack, x + 6, slotY);
            g.drawString(this.font, stack.getHoverName(), x + 26, slotY + 4, COLOR_TEXT, true);
            if (mouseX >= x + 6 && mouseX < x + 22 && mouseY >= slotY && mouseY < slotY + 16) {
                g.renderTooltip(this.font, stack, mouseX, mouseY);
            }
            // 需求 3：玩家自己区域每条目最右侧的红色删除按钮，与物品同一水平线、垂直居中。
            // 双方已同意后服务端锁定改动，此时不显示删除按钮。
            if (self && ClientShopData.tradeAccepted && !ClientShopData.tradeAgreedBoth) {
                int bx = x + w - 14;
                int by = slotY + 3;
                boolean hov = mouseX >= bx && mouseX < bx + 10 && mouseY >= by && mouseY < by + 10;
                g.fill(bx, by, bx + 10, by + 10, hov ? 0xFFFF4040 : 0xFFB00000);
                g.drawCenteredString(this.font, "x", bx + 5, by + 1, 0xFFFFFF);
                if (hov) {
                    g.renderTooltip(this.font,
                            Component.translatable("gui.server_shop_mod.trade.remove"), mouseX, mouseY);
                }
            }
        }
        if (items.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.server_shop_mod.trade.no_items"),
                    x + 6, rowY + 4, COLOR_MUTED, true);
        }
        Component status = agreed
                ? Component.translatable(confirmed ? "gui.server_shop_mod.trade.confirmed"
                        : "gui.server_shop_mod.trade.agreed")
                : Component.translatable("gui.server_shop_mod.trade.not_agreed");
        g.drawString(this.font, status, x + 6, y + 120, agreed ? COLOR_GREEN : COLOR_MUTED, true);
        // 需求 2：对方尚未同意时整列覆盖灰色，表示不可选区。
        if (!active) {
            g.fill(x, y, x + w, y + 132, 0x99101010);
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.trade.pending"),
                    x + w / 2, y + 60, 0xBBBBBB);
        }
    }

    private boolean isEnabled(String action) {
        if ("trade_cancel".equals(action)) {
            return true;
        }
        // 需求 2：对方未同意请求前，交易操作全部不可用。
        if (!ClientShopData.tradeAccepted) {
            return false;
        }
        if ("trade_confirm".equals(action)) {
            return ClientShopData.tradeAgreedBoth && ClientShopData.tradeCountdownMs <= 0;
        }
        // 需求 2：6 个交易槽位已满后不可再点“放入物品”。
        if ("trade_add_item".equals(action)) {
            return ClientShopData.tradeSelfItems.size() < MAX_ITEMS;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 需求 3：点击自己区域的红色删除按钮，把该物品退还背包/邮箱。
        if (button == 0 && ClientShopData.tradeAccepted && !ClientShopData.tradeAgreedBoth) {
            int sx = left() + 10;
            int rowTop = top() + 24 + 20;
            int count = Math.min(MAX_ITEMS, ClientShopData.tradeSelfItems.size());
            for (int i = 0; i < count; i++) {
                int bx = sx + COL_W - 14;
                int by = rowTop + i * ROW_H + 3;
                if (mouseX >= bx && mouseX < bx + 10 && mouseY >= by && mouseY < by + 10) {
                    Bricks.click();
                    CompoundTag tag = new CompoundTag();
                    tag.putString("action", "trade_remove_item");
                    tag.putInt("index", i);
                    PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
                    return true;
                }
            }
        }
        if (this.zones != null && button == 0) {
            for (ButtonZone zone : this.zones) {
                if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
                    if (isEnabled(zone.action)) {
                        Bricks.click();
                        CompoundTag tag = new CompoundTag();
                        tag.putString("action", zone.action);
                        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        // 按 ESC 退出交易界面等同取消交易，避免会话卡死。
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "trade_cancel");
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, boolean hover) {
        int base = !enabled ? 0xFF2A2A2A : (hover ? 0xFF4A4A4A : 0xFF3A3A3A);
        g.fill(x, y, x + w, y + h, base);
        g.fill(x, y, x + w, y + 1, enabled ? 0xFF6A6A6A : 0xFF444444);
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, enabled ? COLOR_TEXT : 0xFF777777);
    }

    private static String symbol() {
        return ClientShopData.symbol;
    }

    private record ButtonZone(int x, int y, int w, int h, String action, String key) {
    }
}
