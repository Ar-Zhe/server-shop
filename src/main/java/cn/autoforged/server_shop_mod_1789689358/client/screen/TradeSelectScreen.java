package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.client.ClientNotifications;
import cn.autoforged.server_shop_mod_1789689358.client.ClientShopData;
import cn.autoforged.server_shop_mod_1789689358.data.NotificationType;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 需求 4：交易/转账对象选择界面。展示在线玩家名片（头像+名称），顶部搜索框支持简易搜索。
 * 交易模式下点击名片发送交易请求；转账模式下点击名片进入金额输入。
 */
public class TradeSelectScreen extends Screen {
    private static final int PANEL_W = 280;
    private static final int PANEL_H = 224;
    private static final int ROW_H = 30;
    private static final int PAGE_SIZE = 5;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_GOLD = 0xFFD700;

    private final List<int[]> rowRects = new ArrayList<>();
    private final List<ClientShopData.OnlinePlayer> currentPagePlayers = new ArrayList<>();
    private final boolean transferMode;
    private EditBox searchBox;
    private int page;
    private int left;
    private int top;

    public TradeSelectScreen() {
        this(false);
    }

    /** transferMode=true 时用于需求 4 的转账对象选择，否则用于交易对象选择。 */
    public TradeSelectScreen(boolean transferMode) {
        super(Component.translatable(transferMode
                ? "gui.server_shop_mod.transfer.select_title"
                : "gui.server_shop_mod.trade.select_title"));
        this.transferMode = transferMode;
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        this.searchBox = new EditBox(this.font, this.left + 40, this.top + 26, PANEL_W - 50, 16,
                Component.translatable(transferMode
                        ? "gui.server_shop_mod.transfer.search_hint"
                        : "gui.server_shop_mod.trade.search_hint"));
        this.searchBox.setMaxLength(32);
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);
    }

    private List<ClientShopData.OnlinePlayer> filtered() {
        String self = ClientShopData.playerName;
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase();
        List<ClientShopData.OnlinePlayer> result = new ArrayList<>();
        for (ClientShopData.OnlinePlayer player : ClientShopData.onlinePlayers) {
            if (player.name.equals(self)) {
                continue;
            }
            if (!query.isEmpty() && !player.name.toLowerCase().contains(query)) {
                continue;
            }
            result.add(player);
        }
        return result;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Bricks.panel(g, this.left, this.top, PANEL_W, PANEL_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.rowRects.clear();
        super.render(g, mouseX, mouseY, partialTick);
        int x = this.left;
        int y = this.top;
        g.drawCenteredString(this.font, this.title, x + PANEL_W / 2, y + 8, COLOR_GOLD);
        g.drawString(this.font, Component.translatable("gui.server_shop_mod.trade.search"), x + 6, y + 30, COLOR_MUTED, true);

        List<ClientShopData.OnlinePlayer> players = filtered();
        int pages = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(this.page, pages - 1));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = this.page * PAGE_SIZE + i;
            if (index >= players.size()) {
                break;
            }
            ClientShopData.OnlinePlayer player = players.get(index);
            int rowY = y + 48 + i * ROW_H;
            g.fill(x + 8, rowY, x + PANEL_W - 8, rowY + ROW_H - 4, 0xFF232427);
            g.fill(x + 8, rowY, x + 10, rowY + ROW_H - 4, COLOR_GOLD);
            if (player.uuid != null) {
                Skins.drawHead(g, player.uuid, x + 14, rowY + 3, 16);
            }
            g.drawString(this.font, player.name, x + 36, rowY + 8, COLOR_TEXT, true);
            g.drawString(this.font, Component.translatable(transferMode
                            ? "gui.server_shop_mod.transfer.click_to_transfer"
                            : "gui.server_shop_mod.trade.click_to_trade"),
                    x + PANEL_W - 100, rowY + 8, COLOR_MUTED, true);
            this.rowRects.add(new int[]{x + 8, rowY, PANEL_W - 16, ROW_H - 4, index});
        }
        if (players.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.trade.no_online"),
                    x + PANEL_W / 2, y + 110, COLOR_MUTED);
        }
        if (pages > 1) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.page", this.page + 1, pages),
                    x + PANEL_W / 2, y + PANEL_H - 34, COLOR_MUTED);
        }
        drawButton(g, x + 10, y + PANEL_H - 24, 70, 16, Component.translatable("gui.server_shop_mod.back"));
        // 需求 4：交易请求入口从交易界面移到“选择交易对象”界面（仅交易模式显示）。
        if (!transferMode) {
            int reqX = x + 86;
            int reqW = 80;
            drawButton(g, reqX, y + PANEL_H - 24, reqW, 16,
                    Component.translatable("gui.server_shop_mod.trade.requests"));
            ClientNotifications.drawBadge(g, this.font, NotificationType.TRADE, reqX + reqW, y + PANEL_H - 24);
        }
        if (pages > 1) {
            // 需求 4：交易模式给“交易请求”按钮腾出位置，翻页按钮相应右移收窄。
            int prevX = transferMode ? x + 90 : x + 172;
            int prevW = transferMode ? 58 : 48;
            int nextW = transferMode ? 58 : 46;
            int nextX = x + PANEL_W - 10 - nextW;
            drawButton(g, prevX, y + PANEL_H - 24, prevW, 16, Component.translatable("gui.server_shop_mod.page_prev"));
            drawButton(g, nextX, y + PANEL_H - 24, nextW, 16, Component.translatable("gui.server_shop_mod.page_next"));
        }
        // filtered() 的列表与行点击共用下标，这里缓存当前页列表。
        this.currentPagePlayers.clear();
        this.currentPagePlayers.addAll(players);
    }

    /** 鼠标滚轮翻页。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            int pages = Math.max(1, (filtered().size() + PAGE_SIZE - 1) / PAGE_SIZE);
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
        // 需求 4：交易请求入口按钮，请求服务端下发列表并打开请求界面。
        if (!transferMode && button == 0 && mouseX >= x + 86 && mouseX < x + 166
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            CompoundTag openTag = new CompoundTag();
            openTag.putString("action", "trade_requests_open");
            PacketDistributor.sendToServer(new ServerboundShopActionPayload(openTag));
            return true;
        }
        int pages = Math.max(1, (currentPagePlayers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int prevX = transferMode ? x + 90 : x + 172;
        int prevW = transferMode ? 58 : 48;
        int nextW = transferMode ? 58 : 46;
        int nextX = x + PANEL_W - 10 - nextW;
        if (pages > 1 && mouseX >= prevX && mouseX < prevX + prevW
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            this.page = Math.max(0, this.page - 1);
            return true;
        }
        if (pages > 1 && mouseX >= nextX && mouseX < nextX + nextW
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            this.page = Math.min(pages - 1, this.page + 1);
            return true;
        }
        for (int[] rect : this.rowRects) {
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                Bricks.click();
                ClientShopData.OnlinePlayer player = currentPagePlayers.get(rect[4]);
                if (transferMode) {
                    // 需求 4：先选玩家，再输入转账金额。
                    CompoundTag request = new CompoundTag();
                    request.putString("kind", "input");
                    request.putString("action", "do_transfer");
                    request.putString("title", "gui.server_shop_mod.input.transfer_amount");
                    request.putString("target", player.name);
                    net.minecraft.nbt.ListTag fields = new net.minecraft.nbt.ListTag();
                    fields.add(net.minecraft.nbt.StringTag.valueOf("amount"));
                    request.put("fields", fields);
                    Minecraft.getInstance().setScreen(new ShopInputScreen(request));
                } else {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("action", "trade_request");
                    tag.putString("target", player.name);
                    PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
