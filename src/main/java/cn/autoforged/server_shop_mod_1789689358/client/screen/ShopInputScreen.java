package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 通用数值/文本输入界面：管理员配置、拍卖出价、转账、修改价格都走这里。 */
public class ShopInputScreen extends Screen {
    private final CompoundTag request;
    private final List<String> fieldNames = new ArrayList<>();
    private final List<EditBox> boxes = new ArrayList<>();
    private final Component title;
    private final String info;
    private final int panelW = 240;
    private int panelH;
    private int left;
    private int top;

    public ShopInputScreen(CompoundTag request) {
        super(Component.translatable("gui.server_shop_mod.input.title"));
        this.request = request.copy();
        this.title = Component.translatable(request.getString("title"));
        this.info = request.getString("info");
        ListTag fields = request.getList("fields", 8);
        for (int i = 0; i < fields.size(); i++) {
            this.fieldNames.add(fields.getString(i));
        }
    }

    private int infoOffset() {
        return this.info.isEmpty() ? 0 : 14;
    }

    /** 需求 3：交易金额界面在标题/提示下方额外显示一行“玩家余额”。 */
    private boolean showBalance() {
        return this.request.getBoolean("show_balance");
    }

    private int headerOffset() {
        return infoOffset() + (showBalance() ? 14 : 0);
    }

    /** 需求 1（bug 修复）：交易金额输入界面关闭/确认后需要回到交易界面。 */
    private boolean isTradeMoney() {
        return "trade_set_money".equals(this.request.getString("action"));
    }

    private void returnToTradeIfNeeded() {
        if (isTradeMoney() && !cn.autoforged.server_shop_mod_1789689358.client.ClientShopData.tradeSelfName.isEmpty()) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new TradeScreen());
        }
    }

    @Override
    protected void init() {
        super.init();
        // init() 在窗口 resize 时会再次执行：先把已输入的值记下来、清空 boxes 再重建，
        // 否则 boxes 会累积成 2N 条，setInitialFocus/confirm 读到的是上一轮已经不在界面上的旧输入框
        // （玩家看到的输入框里敲的数字不会进入发送包）。
        List<String> previous = new ArrayList<>();
        for (EditBox box : this.boxes) {
            previous.add(box.getValue());
        }
        this.boxes.clear();
        this.panelH = 70 + this.fieldNames.size() * 33 + headerOffset();
        this.left = (this.width - this.panelW) / 2;
        this.top = (this.height - this.panelH) / 2;
        for (int i = 0; i < this.fieldNames.size(); i++) {
            String field = this.fieldNames.get(i);
            EditBox box = new EditBox(this.font, this.left + 20, this.top + 38 + headerOffset() + i * 33, this.panelW - 40, 18,
                    Component.translatable("gui.server_shop_mod.field." + field));
            box.setMaxLength(32);
            // 需求 6/7：管理界面编辑时带出当前值，方便修改而不是重新输入。
            String preset = request.getString("default_" + field);
            if (!preset.isEmpty()) {
                box.setValue(preset);
            }
            // resize 前已经输入过的内容优先保留，避免重建输入框时把玩家输入清掉。
            if (i < previous.size() && !previous.get(i).isEmpty()) {
                box.setValue(previous.get(i));
            }
            this.addRenderableWidget(box);
            this.boxes.add(box);
        }
        int buttonY = this.top + this.panelH - 30;
        // 需求 7/8：防误触按钮统一为“左侧确认、右侧取消”；confirm_right 保留作兼容开关（true 才反过来）。
        boolean confirmRight = this.request.getBoolean("confirm_right");
        int leftX = this.left + 24;
        int rightX = this.left + this.panelW - 114;
        this.addRenderableWidget(Button.builder(
                        Component.translatable(confirmRight ? "gui.server_shop_mod.cancel" : "gui.server_shop_mod.confirm"),
                        b -> {
                            if (confirmRight) {
                                onClose();
                            } else {
                                confirm();
                            }
                        })
                .pos(leftX, buttonY).size(90, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable(confirmRight ? "gui.server_shop_mod.confirm" : "gui.server_shop_mod.cancel"),
                        b -> {
                            if (confirmRight) {
                                confirm();
                            } else {
                                onClose();
                            }
                        })
                .pos(rightX, buttonY).size(90, 20).build());
        if (!this.boxes.isEmpty()) {
            this.setInitialFocus(this.boxes.get(0));
        }
    }

    private void confirm() {
        CompoundTag tag = this.request.copy();
        for (int i = 0; i < this.fieldNames.size(); i++) {
            tag.putString(this.fieldNames.get(i), this.boxes.get(i).getValue().trim());
        }
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
        // 需求 1（bug 修复）：确认后立即回到交易界面，避免停留在输入界面。
        returnToTradeIfNeeded();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Bricks.panel(g, this.left, this.top, this.panelW, this.panelH);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.left + this.panelW / 2, this.top + 12, 0xFFD700);
        if (!this.info.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal(this.info), this.left + this.panelW / 2,
                    this.top + 27, 0xFFD700);
        }
        // 需求 3：交易金额界面显示玩家余额（数值由服务端在打开输入界面时下发）。
        if (showBalance()) {
            long balance = this.request.getLong("balance");
            g.drawCenteredString(this.font,
                    Component.translatable("gui.server_shop_mod.trade.balance",
                            cn.autoforged.server_shop_mod_1789689358.client.ClientShopData.money(balance)),
                    this.left + this.panelW / 2, this.top + 27 + infoOffset(), 0xFFD700);
        }
        for (int i = 0; i < this.fieldNames.size(); i++) {
            g.drawString(this.font, Component.translatable("gui.server_shop_mod.field." + this.fieldNames.get(i)),
                    this.left + 20, this.top + 26 + headerOffset() + i * 33, 0xAAAAAA, true);
        }
        // 需求 7：购买弹窗实时显示总价与余额变化。
        long unitPrice = this.request.getLong("unit_price");
        if (unitPrice > 0L && !this.boxes.isEmpty()) {
            long quantity = 0L;
            try {
                quantity = Long.parseLong(this.boxes.get(0).getValue().trim());
            } catch (NumberFormatException ignored) {
            }
            long total = unitPrice * Math.max(0L, quantity);
            long balanceAfter = this.request.getLong("balance") - total;
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.buy_total",
                            cn.autoforged.server_shop_mod_1789689358.client.ClientShopData.money(total),
                            cn.autoforged.server_shop_mod_1789689358.client.ClientShopData.money(balanceAfter)),
                    this.left + this.panelW / 2, this.top + this.panelH - 46, 0xFFD700);
        }
    }

    @Override
    public void onClose() {
        // 需求 1（bug 修复）：取消交易金额输入后回到交易界面，而不是直接关闭所有界面。
        super.onClose();
        returnToTradeIfNeeded();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
