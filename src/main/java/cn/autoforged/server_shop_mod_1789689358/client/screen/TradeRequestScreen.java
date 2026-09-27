package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.Config;
import cn.autoforged.server_shop_mod_1789689358.client.ClientNotifications;
import cn.autoforged.server_shop_mod_1789689358.client.ClientShopData;
import cn.autoforged.server_shop_mod_1789689358.data.NotificationType;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 需求 1：交易请求列表。打开后可以看到别人发来的交易请求，点击即可进入交易界面；
 * 失效（超时/被取消）的请求由服务端清理并刷新本列表。
 */
public class TradeRequestScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 210;
    private static final int ROW_H = 30;
    private static final int PAGE_SIZE = 5;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_GOLD = 0xFFD700;

    private int page;
    private int left;
    private int top;

    public TradeRequestScreen() {
        super(Component.translatable("gui.server_shop_mod.trade.request_title"));
    }

    public void onDataUpdated() {
        // 列表直接从 ClientShopData 读取，无需额外处理。
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        // 需求 10：确认界面已打开后再清除交易请求红点（服务端权威清零）。
        ClientNotifications.clearLocal(NotificationType.TRADE);
        CompoundTag clear = new CompoundTag();
        clear.putString("action", "notif_clear");
        clear.putString("type", NotificationType.TRADE.id());
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(clear));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Bricks.panel(g, this.left, this.top, PANEL_W, PANEL_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = this.left;
        int y = this.top;
        g.drawCenteredString(this.font, this.title, x + PANEL_W / 2, y + 8, COLOR_GOLD);

        var requests = ClientShopData.tradeRequests;
        int pages = Math.max(1, (requests.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(this.page, pages - 1));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = this.page * PAGE_SIZE + i;
            if (index >= requests.size()) {
                break;
            }
            ClientShopData.TradeRequest request = requests.get(index);
            int rowY = y + 24 + i * ROW_H;
            g.fill(x + 8, rowY, x + PANEL_W - 8, rowY + ROW_H - 4, 0xFF232427);
            g.fill(x + 8, rowY, x + 10, rowY + ROW_H - 4, COLOR_GOLD);
            if (request.from != null && !request.from.isEmpty()) {
                PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null
                        : Minecraft.getInstance().getConnection().getPlayerInfo(request.from);
                if (info != null) {
                    PlayerFaceRenderer.draw(g, info.getSkin(), x + 14, rowY + 3, 16);
                }
            }
            // 需求二：整行展示“请求来自谁 + 剩余时间”，进入交易改由独立绿色【接受】按钮触发。
            g.drawString(this.font, Component.translatable("gui.server_shop_mod.trade.request_from", request.from),
                    x + 36, rowY + 3, COLOR_TEXT, true);
            long expireMs = Config.INSTANCE.tradeRequestExpireSeconds.get() * 1000L;
            // 需求二：倒计时数字实时递减——快照里的 age 加上快照到达后的本地流逝时间。
            long elapsed = request.ageMs + Math.max(0L, System.currentTimeMillis() - request.receivedAt);
            long remainSec = Math.max(0L, (expireMs - elapsed + 999L) / 1000L);
            String remain = Component.translatable("gui.server_shop_mod.trade.request_remaining", remainSec).getString();
            g.drawString(this.font, remain, x + 36, rowY + 14, COLOR_GOLD, true);
            // 需求二：绿色【接受】按钮（进入交易）。
            int acceptX = x + PANEL_W - 112;
            g.fill(acceptX, rowY + 6, acceptX + 46, rowY + 20, 0xFF245E2A);
            g.fill(acceptX, rowY + 6, acceptX + 46, rowY + 7, 0xFF3E9E48);
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.trade.accept"),
                    acceptX + 23, rowY + 9, COLOR_TEXT);
            // 需求二：保留红色【拒绝】按钮。
            int rejectX = x + PANEL_W - 60;
            g.fill(rejectX, rowY + 6, rejectX + 46, rowY + 20, 0xFF5A2A2A);
            g.fill(rejectX, rowY + 6, rejectX + 46, rowY + 7, 0xFF8A4A4A);
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.trade.decline"),
                    rejectX + 23, rowY + 9, COLOR_TEXT);
        }
        if (requests.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.trade.request_empty"),
                    x + PANEL_W / 2, y + 100, COLOR_MUTED);
        }
        if (pages > 1) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.page", this.page + 1, pages),
                    x + PANEL_W / 2, y + PANEL_H - 34, COLOR_MUTED);
        }
        drawButton(g, x + 10, y + PANEL_H - 24, 70, 16, Component.translatable("gui.server_shop_mod.back"));
        if (pages > 1) {
            drawButton(g, x + 88, y + PANEL_H - 24, 58, 16, Component.translatable("gui.server_shop_mod.page_prev"));
            drawButton(g, x + PANEL_W - 68, y + PANEL_H - 24, 58, 16, Component.translatable("gui.server_shop_mod.page_next"));
        }
    }

    /** 鼠标滚轮翻页。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            int pages = Math.max(1, (ClientShopData.tradeRequests.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int next = Math.max(0, Math.min(pages - 1, this.page + (scrollY > 0.0D ? -1 : 1)));
            if (next != this.page) {
                this.page = next;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.left;
        int y = this.top;
        if (button == 0 && mouseX >= x + 10 && mouseX < x + 80
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            Minecraft.getInstance().setScreen(new ShopScreen("daily"));
            return true;
        }
        var requests = ClientShopData.tradeRequests;
        int pages = Math.max(1, (requests.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pages > 1 && button == 0 && mouseX >= x + 88 && mouseX < x + 146
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            this.page = Math.max(0, this.page - 1);
            return true;
        }
        if (pages > 1 && button == 0 && mouseX >= x + PANEL_W - 68 && mouseX < x + PANEL_W - 10
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            this.page = Math.min(pages - 1, this.page + 1);
            return true;
        }
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = this.page * PAGE_SIZE + i;
            if (index >= requests.size()) {
                break;
            }
            int rowY = y + 24 + i * ROW_H;
            ClientShopData.TradeRequest request = requests.get(index);
            // 需求二：只有点绿色【接受】才进入交易，点红色【拒绝】拒绝请求。
            // 左键点整行不再触发任何操作，避免误触。
            int acceptX = x + PANEL_W - 112;
            if (button == 0 && mouseX >= acceptX && mouseX < acceptX + 46
                    && mouseY >= rowY + 6 && mouseY < rowY + 20) {
                Bricks.click();
                send("trade_accept", request.id);
                return true;
            }
            int rejectX = x + PANEL_W - 60;
            if (button == 0 && mouseX >= rejectX && mouseX < rejectX + 46
                    && mouseY >= rowY + 6 && mouseY < rowY + 20) {
                Bricks.click();
                send("trade_decline", request.id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(String action, long id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("action", action);
        tag.putLong("id", id);
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label) {
        g.fill(x, y, x + w, y + h, 0xFF3A3A3A);
        g.fill(x, y, x + w, y + 1, 0xFF6A6A6A);
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, COLOR_TEXT);
    }
}
