package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.Config;
import cn.autoforged.server_shop_mod_1789689358.client.ClientNotifications;
import cn.autoforged.server_shop_mod_1789689358.client.ClientShopData;
import cn.autoforged.server_shop_mod_1789689358.data.ConfiguredOffer;
import cn.autoforged.server_shop_mod_1789689358.data.NotificationType;
import cn.autoforged.server_shop_mod_1789689358.data.Rarity;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import cn.autoforged.server_shop_mod_1789689358.shop.ShopManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 单界面、标签页切换的商店主界面。没有真实槽位，所有操作通过操作包发给服务端。 */
public class ShopScreen extends Screen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 244;
    private static final int TOP_BAR_H = 42;
    private static final int SIDEBAR_W = 82;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_GOLD = 0xFFD700;
    private static final int GEAR_TEX_SIZE = 128;
    /** 需求 2：齿轮入口纹理（源文件 user-assets/577940.png，128×128）。 */
    private static final net.minecraft.resources.ResourceLocation GEAR_ICON =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "server_shop_mod_1789689358", "textures/gui/gear.png");

    // 需求：模块放在左侧从上往下排；钱包格去掉。管理/设置页签仅管理员可见（需求 2）。
    private static final String[] TAB_IDS = {"daily", "buyback", "auction", "stall", "mail"};

    private final List<Region> regions = new ArrayList<>();
    private final java.util.Map<Integer, Integer> flipStart = new java.util.HashMap<>();
    private String currentTab;
    private int tickCounter;
    private int auctionPage;
    private int adminPage;
    private int mailPage;
    private String adminMode = "shop";

    public ShopScreen(String initialTab) {
        super(Component.translatable("gui.server_shop_mod.title"));
        // 服务端没指定页签时沿用上次打开的页签，避免被弹回“每日商店”。
        this.currentTab = initialTab == null || initialTab.isEmpty() ? ClientShopData.lastTab : initialTab;
    }

    public void onDataUpdated() {
        // 翻牌状态现在由服务端持久化（需求 11），这里保留本地动画进度即可。
    }

    @Override
    protected void init() {
        super.init();
        // 还原上次的页数与编辑模块：服务端每次操作后会重建界面（编辑条目、买卖等），
        // 不还原的话管理员改完一条就被弹回第一页，翻页很痛苦。
        this.auctionPage = ClientShopData.lastAuctionPage;
        this.adminPage = ClientShopData.lastAdminPage;
        this.mailPage = ClientShopData.lastMailPage;
        this.adminMode = ClientShopData.lastAdminMode;
        // 管理页只能由 /shop admin 指令打开，不作为可见页签，但打开后不能被重置掉。
        if (!"admin".equals(currentTab) && !visibleTabs().contains(currentTab)) {
            currentTab = "daily";
        }
        // 需求 11：进入管理页时把当前编辑模块同步给服务端，物品放入时据此归属列表。
        if ("admin".equals(currentTab)) {
            sendAdminType();
        }
        // 需求 10：界面已打开即视为已读，清除当前模块红点。
        clearTabNotification(currentTab);
    }

    private void sendAdminType() {
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "admin_set_edit_type");
        tag.putString("type", adminMode);
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        // 把界面状态记到客户端缓存：服务端重建界面时按这里恢复页数与页签。
        ClientShopData.lastTab = currentTab;
        ClientShopData.lastAuctionPage = auctionPage;
        ClientShopData.lastAdminPage = adminPage;
        ClientShopData.lastMailPage = mailPage;
        ClientShopData.lastAdminMode = adminMode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<String> visibleTabs() {
        List<String> tabs = new ArrayList<>(List.of(TAB_IDS));
        // 需求 2：仅管理员显示隐藏的“商店设置”页签（/shop admin 仍可直接打开）。
        if (ClientShopData.canAdmin) {
            tabs.add("admin");
        }
        return tabs;
    }

    private static String tabKey(String id) {
        return switch (id) {
            case "daily" -> "tab.server_shop_mod.daily";
            case "buyback" -> "tab.server_shop_mod.buyback";
            case "auction" -> "tab.server_shop_mod.auction";
            case "stall" -> "tab.server_shop_mod.stall";
            case "mail" -> "tab.server_shop_mod.mail";
            case "admin" -> "tab.server_shop_mod.admin";
            default -> id;
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
        this.regions.clear();
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        Bricks.panel(g, x, y, PANEL_W, PANEL_H);
        drawTopBar(g, x, y, mouseX, mouseY);
        drawTabs(g, x, y);
        int contentX = x + SIDEBAR_W + 4;
        int contentY = y + TOP_BAR_H + 2;
        int contentW = PANEL_W - SIDEBAR_W - 10;
        int contentH = PANEL_H - TOP_BAR_H - 6;
        switch (currentTab) {
            case "daily" -> drawDaily(g, contentX, contentY, contentW, contentH, mouseX, mouseY, partialTick);
            case "buyback" -> drawBuyback(g, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case "auction" -> drawAuction(g, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case "stall" -> drawStall(g, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case "mail" -> drawMail(g, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case "admin" -> drawAdmin(g, contentX, contentY, contentW, contentH, mouseX, mouseY);
            default -> {
            }
        }
    }

    /** 顶部栏：左上角玩家头像/名称/余额，右上角交易与转账。 */
    private void drawTopBar(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int avatarSize = 20;
        int avatarX = x + 8;
        int avatarY = y + 8;
        g.fill(avatarX - 1, avatarY - 1, avatarX + avatarSize + 1, avatarY + avatarSize + 1, 0xFF202020);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, player.getSkin(), avatarX, avatarY, avatarSize);
        }
        String name = ClientShopData.playerName.isEmpty() ? "-" : ClientShopData.playerName;
        g.drawString(this.font, name, avatarX + avatarSize + 6, y + 9, COLOR_TEXT, true);
        // 需求 6：余额前加“余额：”，金额统一追加“星币”，颜色与星币一致。
        g.drawString(this.font, Component.translatable("gui.server_shop_mod.balance_prefix",
                        ClientShopData.money(ClientShopData.balance)),
                avatarX + avatarSize + 6, y + 21, COLOR_GOLD, true);
        g.fill(x + 6, y + TOP_BAR_H - 3, x + PANEL_W - 6, y + TOP_BAR_H - 2, 0xFF4A4A4A);

        // 右上角：交易 / 转账（需求 8：交易请求入口已转移到交易界面内）。
        int tradeW = 52;
        int transferW = 52;
        int transferX = x + PANEL_W - 8 - transferW;
        int tradeX = transferX - 6 - tradeW;
        drawButton(g, tradeX, y + 8, tradeW, 18, Component.translatable("gui.server_shop_mod.trade"));
        drawButton(g, transferX, y + 8, transferW, 18, Component.translatable("gui.server_shop_mod.transfer"));
        addRegion(tradeX, y + 8, tradeW, 18, "trade_select", null, null);
        addRegion(transferX, y + 8, transferW, 18, "transfer_gui", null, null);
        // 需求 10：交易/钱包未读提示红点。
        ClientNotifications.drawBadge(g, this.font, NotificationType.TRADE, tradeX + tradeW, y + 8);
        ClientNotifications.drawBadge(g, this.font, NotificationType.WALLET, transferX + transferW, y + 8);
        // 需求 10-5：左下角像素风小齿轮，点开提醒音效设置。
        int gearX = x + 6;
        // 需求 5：齿轮稍微往上挪，避免下边缘超出面板边框。
        int gearY = y + PANEL_H - 22;
        drawGear(g, gearX, gearY);
        addRegion(gearX, gearY, 12, 12, "notif_settings", null, null);
    }

    /**
     * 需求 2：提醒设置入口改用 577940.png 图标，缩放到 12×12（该素材原始分辨率为 128×128，
     * 由 user-assets/577940.png 复制到 textures/gui/gear.png），不再用矩形拼装齿轮。
     */
    private void drawGear(GuiGraphics g, int x, int y) {
        g.blit(GEAR_ICON, x, y, 12, 12, 0.0F, 0.0F, GEAR_TEX_SIZE, GEAR_TEX_SIZE,
                GEAR_TEX_SIZE, GEAR_TEX_SIZE);
    }

    /** 左侧竖排页签，从上往下。 */
    private void drawTabs(GuiGraphics g, int x, int y) {
        int tabH = 22;
        int startY = y + TOP_BAR_H + 4;
        List<String> tabs = visibleTabs();
        for (int i = 0; i < tabs.size(); i++) {
            String id = tabs.get(i);
            int ty = startY + i * (tabH + 2);
            boolean selected = id.equals(currentTab);
            g.fill(x + 5, ty, x + SIDEBAR_W - 3, ty + tabH, selected ? 0xFF4C4C4C : 0xFF262626);
            if (selected) {
                g.fill(x + 5, ty, x + 7, ty + tabH, COLOR_GOLD);
            }
            g.drawCenteredString(this.font, Component.translatable(tabKey(id)),
                    x + 5 + (SIDEBAR_W - 8) / 2, ty + (tabH - 8) / 2, selected ? COLOR_GOLD : 0xDDDDDD);
            // 需求 10：对应模块有未读时在页签右上角显示带数字的红点。
            NotificationType badge = switch (id) {
                case "daily", "buyback" -> NotificationType.SHOP;
                case "auction" -> NotificationType.AUCTION;
                case "stall" -> NotificationType.STALL;
                case "mail" -> NotificationType.MAIL;
                default -> null;
            };
            if (badge != null) {
                ClientNotifications.drawBadge(g, this.font, badge, x + SIDEBAR_W - 3, ty - 2);
            }
            CompoundTag extra = new CompoundTag();
            extra.putString("tab", id);
            addRegion(x + 5, ty, SIDEBAR_W - 8, tabH, "set_tab", null, extra);
        }
    }

    // =========================================================== 每日商店

    private void drawDaily(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my, float partialTick) {
        int count = ClientShopData.dailyShop.size();
        if (count == 0) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.empty"), cx + cw / 2, cy + 40, COLOR_MUTED);
            return;
        }
        // 需求 11：牌面加大，排列为 2 行 3 列。
        int cols = 3;
        int cardW = 88;
        int cardH = 78;
        int gapX = 8;
        int gapY = 10;
        int rows = (count + cols - 1) / cols;
        int totalW = cols * cardW + (cols - 1) * gapX;
        int totalH = rows * cardH + (rows - 1) * gapY;
        int startX = cx + Math.max(0, (cw - totalW) / 2);
        int startY = cy + Math.max(0, (ch - totalH) / 2);
        for (int i = 0; i < count; i++) {
            var entry = ClientShopData.dailyShop.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (cardW + gapX);
            int cardY = startY + row * (cardH + gapY);
            // 翻开状态来自服务端并持久化（需求 11），本地 flipStart 只负责翻牌动画。
            boolean flipped = ClientShopData.dailyFlipped.contains(i);
            Integer start = this.flipStart.get(i);
            float progress = start == null ? (flipped ? 1.0F : 0.0F)
                    : Mth.clamp((tickCounter + partialTick - start) / 8.0F, 0.0F, 1.0F);
            float scale = Math.abs((float) Math.cos(progress * Math.PI));
            if (scale < 0.05F) {
                scale = 0.05F;
            }
            float center = x + cardW / 2.0F;
            g.pose().pushPose();
            g.pose().translate(center, 0.0F, 0.0F);
            g.pose().scale(scale, 1.0F, 1.0F);
            g.pose().translate(-center, 0.0F, 0.0F);
            int border = entry.rarity.color();
            g.fill(x, cardY, x + cardW, cardY + cardH, 0xFF17181B);
            g.fill(x, cardY, x + cardW, cardY + 2, border);
            g.fill(x, cardY + cardH - 2, x + cardW, cardY + cardH, border);
            g.fill(x, cardY, x + 2, cardY + cardH, border);
            g.fill(x + cardW - 2, cardY, x + cardW, cardY + cardH, border);
            if (flipped) {
                int itemX = x + (cardW - 16) / 2;
                g.renderItem(entry.stack, itemX, cardY + 8);
                g.renderItemDecorations(this.font, entry.stack, itemX, cardY + 8);
                g.drawCenteredString(this.font, entry.stack.getHoverName(), x + cardW / 2, cardY + 28, COLOR_TEXT);
                g.drawCenteredString(this.font, Component.literal(ClientShopData.money(entry.price)), x + cardW / 2, cardY + 42, COLOR_GOLD);
                // 需求 11：显示该物品自己的剩余购买数。
                int remaining = entry.remaining();
                g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.remaining", remaining),
                        x + cardW / 2, cardY + 54, remaining > 0 ? COLOR_MUTED : 0xFF5555);
                g.drawCenteredString(this.font, Component.translatable(entry.rarity.translationKey()),
                        x + cardW / 2, cardY + 66, entry.rarity.color());
            } else {
                // 牌背面用品质配色，让玩家不用翻开就能看出品质。
                g.fill(x + 2, cardY + 2, x + cardW - 2, cardY + cardH - 2, (border & 0x00FFFFFF) | 0x33000000);
                g.drawCenteredString(this.font, Component.literal("?"), x + cardW / 2, cardY + 26, border);
                g.drawCenteredString(this.font, Component.translatable(entry.rarity.translationKey()),
                        x + cardW / 2, cardY + 62, border);
            }
            g.pose().popPose();
            CompoundTag extra = new CompoundTag();
            extra.putInt("index", i);
            addRegion(x, cardY, cardW, cardH, flipped ? "buy_shop" : "flip_shop", null, extra);
            if (flipped && mx >= x && mx < x + cardW && my >= cardY && my < cardY + cardH) {
                g.renderTooltip(this.font, entry.stack, mx, my);
            }
        }
        // 付费刷新按钮：小尺寸，悬停显示本次花费与今日剩余次数。
        int refreshW = 52;
        int refreshH = 12;
        int refreshX = cx + cw - refreshW - 4;
        int refreshY = cy + ch - refreshH - 2;
        boolean canRefresh = ClientShopData.refreshCost >= 0L;
        addRegion(refreshX, refreshY, refreshW, refreshH, "refresh_daily_shop", null, null);
        drawButton(g, refreshX, refreshY, refreshW, refreshH,
                Component.translatable("gui.server_shop_mod.refresh_daily"), canRefresh);
        if (mx >= refreshX && mx < refreshX + refreshW && my >= refreshY && my < refreshY + refreshH) {
            int left = Math.max(0, ClientShopData.refreshMax - ClientShopData.refreshUsed);
            if (canRefresh) {
                g.renderTooltip(this.font, Component.translatable("gui.server_shop_mod.refresh_tooltip",
                        ClientShopData.money(ClientShopData.refreshCost), left, ClientShopData.refreshMax), mx, my);
            } else {
                g.renderTooltip(this.font, Component.translatable("gui.server_shop_mod.refresh_tooltip_none",
                        ClientShopData.refreshMax), mx, my);
            }
        }
    }

    // =========================================================== 每日收购

    private void drawBuyback(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        var list = ClientShopData.dailyBuyback;
        if (list.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.empty"), cx + cw / 2, cy + 40, COLOR_MUTED);
            return;
        }
        // 需求 5：一行一个共 6 行横条，最左物品图标、中间收购价、右侧还能收购的数量。
        int rows = Math.min(6, list.size());
        int rowH = Math.min(30, Math.max(20, (ch - 8) / 6));
        int startY = cy + 6;
        for (int i = 0; i < rows; i++) {
            var entry = list.get(i);
            int y = startY + i * rowH;
            int textY = y + (rowH - 14) / 2;
            g.fill(cx + 4, y, cx + cw - 4, y + rowH - 4, 0xFF1B1C1F);
            Bricks.slot(g, cx + 8, y + 2);
            g.renderItem(entry.stack, cx + 8, y + 2);
            g.renderItemDecorations(this.font, entry.stack, cx + 8, y + 2);
            g.drawString(this.font, entry.stack.getHoverName(), cx + 30, textY, COLOR_TEXT, true);
            // 需求 6：显示收购物品的品质。
            g.drawString(this.font, Component.translatable(entry.rarity.translationKey()), cx + 96, textY,
                    entry.rarity.color(), true);
            String price = Component.translatable("gui.server_shop_mod.buyback_price", ClientShopData.money(entry.price)).getString();
            g.drawString(this.font, price, cx + cw / 2 - this.font.width(price) / 2, textY, COLOR_GOLD, true);
            // 需求 9：剩余可收购数量来自该物品自己的上限。
            int remaining = entry.remaining();
            String remainText = Component.translatable("gui.server_shop_mod.buyback_remaining", remaining).getString();
            g.drawString(this.font, remainText, cx + cw - 12 - this.font.width(remainText), textY,
                    remaining > 0 ? COLOR_MUTED : 0xFF5555, true);
            CompoundTag extra = new CompoundTag();
            extra.putInt("index", i);
            // 需求 9：点击不立即出售，而是弹出数量输入，让玩家自己调节卖出的数量。
            addRegion(cx + 4, y, cw - 8, rowH - 4, "sell_buyback_gui", null, extra);
            if (mx >= cx + 8 && mx < cx + 24 && my >= y + 2 && my < y + 18) {
                g.renderTooltip(this.font, entry.stack, mx, my);
            }
        }
        // 收购全清奖励进度：全部卖完可额外获得 X 金币（每天一次）。
        if (ClientShopData.buybackBonus > 0L) {
            Component bonusLine = ClientShopData.buybackBonusDone
                    ? Component.translatable("gui.server_shop_mod.buyback_bonus_done",
                            ClientShopData.money(ClientShopData.buybackBonus))
                    : Component.translatable("gui.server_shop_mod.buyback_bonus_progress",
                            ClientShopData.money(ClientShopData.buybackBonus), ClientShopData.buybackLeft);
            g.drawString(this.font, bonusLine, cx + 4, cy + ch - 10,
                    ClientShopData.buybackBonusDone ? COLOR_MUTED : COLOR_GOLD, true);
        }
    }

    // =========================================================== 拍卖

    private void drawAuction(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        List<ClientShopData.AuctionView> auctions = ClientShopData.auctions;
        int pageSize = 5;
        int pages = Math.max(1, (auctions.size() + pageSize - 1) / pageSize);
        auctionPage = Mth.clamp(auctionPage, 0, pages - 1);
        for (int i = 0; i < pageSize; i++) {
            int index = auctionPage * pageSize + i;
            if (index >= auctions.size()) {
                break;
            }
            ClientShopData.AuctionView view = auctions.get(index);
            int y = cy + 10 + i * 30;
            Bricks.slot(g, cx + 8, y + 5);
            g.renderItem(view.stack, cx + 8, y + 5);
            g.renderItemDecorations(this.font, view.stack, cx + 8, y + 5);
            g.drawString(this.font, view.stack.getHoverName(), cx + 32, y + 4, COLOR_TEXT, true);
            String sellerText = Component.translatable("gui.server_shop_mod.auction_seller", view.seller).getString();
            if (view.own) {
                sellerText = sellerText + " " + Component.translatable("gui.server_shop_mod.auction_mine").getString();
            }
            g.drawString(this.font, sellerText, cx + 32, y + 15, view.own ? COLOR_GOLD : COLOR_MUTED, true);
            // 最高出价者：玩家一眼看出自己是不是还在最高价（出价的钱是否还处于冻结中）。
            if (view.currentBid > 0L && view.topBidder != null && !view.topBidder.isEmpty()) {
                String top = Component.translatable(view.topMe
                        ? "gui.server_shop_mod.auction_top_me"
                        : "gui.server_shop_mod.auction_top_bidder", view.topBidder).getString();
                int topX = cx + cw - 6 - this.font.width(top);
                if (topX > cx + 32 + this.font.width(sellerText) + 4) {
                    g.drawString(this.font, top, topX, y + 15, view.topMe ? COLOR_GOLD : COLOR_MUTED, true);
                }
            }
            // 需求三：竞拍倒计时实时递减（快照剩余时间减去快照到达后的本地流逝时间）。
            long remainMs = view.receivedAt > 0L
                    ? Math.max(0L, view.remaining - (System.currentTimeMillis() - view.receivedAt))
                    : view.remaining;
            String time = formatDuration(remainMs);
            int centerX = cx + cw / 2;
            // 需求三：价格展示居中，同时显示上一轮出价（划掉）与当前最高出价。
            if (view.currentBid > 0) {
                String current = Component.translatable("gui.server_shop_mod.auction_current_bid",
                        ClientShopData.money(view.currentBid)).getString();
                g.drawString(this.font, current, centerX - this.font.width(current) / 2, y + 4, COLOR_GOLD, true);
                if (view.lastBid > 0) {
                    String last = Component.translatable("gui.server_shop_mod.auction_last_bid",
                            ClientShopData.money(view.lastBid)).getString();
                    int lastX = centerX - this.font.width(last) / 2;
                    g.drawString(this.font, last, lastX, y + 15, 0x8A8A9A, true);
                    g.fill(lastX, y + 19, lastX + this.font.width(last), y + 20, 0x8A8A9A);
                }
            } else {
                String start = Component.translatable("gui.server_shop_mod.auction_start",
                        ClientShopData.money(view.startPrice)).getString();
                g.drawString(this.font, start, centerX - this.font.width(start) / 2, y + 4, COLOR_GOLD, true);
                g.drawString(this.font, time, centerX - this.font.width(time) / 2, y + 15, COLOR_MUTED, true);
            }
            // 剩余时间固定右上角显示，避免与居中的价格文字重叠。
            if (view.currentBid > 0) {
                g.drawString(this.font, time, cx + cw - 6 - this.font.width(time), y + 4, COLOR_MUTED, true);
            }
            // 需求 3：悬停物品图标时显示名称/附魔/属性/NBT/标签等完整信息。
            if (mx >= cx + 8 && mx < cx + 24 && my >= y + 5 && my < y + 21) {
                g.renderTooltip(this.font, view.stack, mx, my);
            }
            // 自己的拍卖不注册“出价”入口：原来点进去填完金额才会被服务端以 bid_own 拒绝，白填一次。
            if (!view.own) {
                CompoundTag extra = new CompoundTag();
                extra.putLong("id", view.id);
                addRegion(cx + 4, y, cw - 8, 28, "bid_gui", null, extra);
            }
        }
        if (auctions.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.auction_empty"), cx + cw / 2, cy + 50, COLOR_MUTED);
        }
        // 上架按钮
        CompoundTag add = new CompoundTag();
        addRegion(cx + cw - 76, cy + ch - 18, 72, 16, "create_auction", null, add);
        drawButton(g, cx + cw - 76, cy + ch - 18, 72, 16, Component.translatable("gui.server_shop_mod.auction_create"));
        // 翻页：上一页 / 页码 / 下一页（需求 1）
        if (pages > 1) {
            drawButton(g, cx + 4, cy + ch - 18, 44, 16, Component.translatable("gui.server_shop_mod.page_prev"));
            drawButton(g, cx + 52, cy + ch - 18, 44, 16, Component.translatable("gui.server_shop_mod.page_next"));
            addRegion(cx + 4, cy + ch - 18, 44, 16, "auction_prev", null, null);
            addRegion(cx + 52, cy + ch - 18, 44, 16, "auction_next", null, null);
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.page", auctionPage + 1, pages),
                    cx + cw / 2, cy + ch - 14, COLOR_MUTED);
        }
    }

    // =========================================================== 摊位

    private void drawStall(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        var stall = ClientShopData.stall;
        // 需求 8：上架物品界面改成 6 行 2 列。
        int rows = 6;
        int cols = 2;
        int gapX = 8;
        int gapY = 4;
        int cellW = (cw - gapX * (cols - 1)) / cols;
        int cellH = Math.min(30, Math.max(18, (ch - 26 - gapY * (rows - 1)) / rows));
        int startX = cx + 2;
        int startY = cy + 6;
        for (int i = 0; i < rows * cols; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = startX + col * (cellW + gapX);
            int sy = startY + row * (cellH + gapY);
            g.fill(sx, sy, sx + cellW, sy + cellH, 0xFF1B1C1F);
            int slotY = sy + (cellH - 16) / 2;
            Bricks.slot(g, sx + 4, slotY);
            if (i < stall.size()) {
                var entry = stall.get(i);
                g.renderItem(entry.stack, sx + 4, slotY);
                g.renderItemDecorations(this.font, entry.stack, sx + 4, slotY);
                // 需求 4/6：单件价格 + 剩余库存，售空显示“已售空”。
                g.drawString(this.font, Component.literal(ClientShopData.money(entry.price)), sx + 26, sy + 4, COLOR_GOLD, true);
                String stockText;
                int stockColor;
                if (entry.stock <= 0) {
                    stockText = Component.translatable("gui.server_shop_mod.stall_sold_out").getString();
                    stockColor = 0xFF5555;
                } else {
                    stockText = Component.translatable("gui.server_shop_mod.stall_stock", entry.stock).getString();
                    stockColor = COLOR_MUTED;
                }
                g.drawString(this.font, stockText, sx + 26, sy + 15, stockColor, true);
                // 需求 9：在商品旁空白处显示该商品累计已售出件数。
                g.drawString(this.font, Component.translatable("gui.server_shop_mod.stall_sold_count", entry.sold),
                        sx + 26 + this.font.width(stockText) + 8, sy + 15, 0x88BBFF, true);
                CompoundTag extra = new CompoundTag();
                extra.putInt("index", i);
                // 右侧小按钮先注册，保证点击优先命中“下架”而不是整格改价。
                addRegion(sx + cellW - 22, sy + (cellH - 14) / 2, 20, 14, "remove_stall", null, extra);
                drawSmallButton(g, sx + cellW - 22, sy + (cellH - 14) / 2, Component.literal("X"));
                // 需求五：已取消补货；售空的商品整格不再响应点击，只能点右侧“X”下架后重新上架。
                if (entry.stock > 0) {
                    addRegion(sx + 2, sy, cellW - 26, cellH, "stall_edit_gui", null, extra);
                }
                if (mx >= sx + 4 && mx < sx + 20 && my >= sy && my < sy + cellH) {
                    g.renderTooltip(this.font, entry.stack, mx, my);
                }
            }
        }
        int buttonY = cy + ch - 16;
        addRegion(cx + 4, buttonY, 80, 16, "create_stall", null, null);
        drawButton(g, cx + 4, buttonY, 80, 16, Component.translatable("gui.server_shop_mod.stall_create"));
        addRegion(cx + 90, buttonY, 80, 16, "open_avatar", null, null);
        drawButton(g, cx + 90, buttonY, 80, 16, Component.translatable("gui.server_shop_mod.stall_browse"));
    }

    private void drawSmallButton(GuiGraphics g, int x, int y, Component label) {
        g.fill(x, y, x + 20, y + 14, 0xFF5A2A2A);
        g.fill(x, y, x + 20, y + 1, 0xFF8A4A4A);
        g.drawCenteredString(this.font, label, x + 10, y + 3, COLOR_TEXT);
    }

    // =========================================================== 邮箱

    private void drawMail(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        var mail = ClientShopData.mail;
        // 需求：每页固定 45 封（9 列 5 行）。
        int cols = 9;
        int rows = 5;
        int pageSize = cols * rows;
        int cellW = 36;
        int cellH = 30;
        int totalW = cols * cellW;
        int startX = cx + Math.max(0, (cw - totalW) / 2);
        int startY = cy + 4;
        int pages = Math.max(1, (mail.size() + pageSize - 1) / pageSize);
        this.mailPage = Mth.clamp(this.mailPage, 0, pages - 1);
        var hovered = (cn.autoforged.server_shop_mod_1789689358.data.MailEntry) null;
        for (int i = 0; i < pageSize; i++) {
            int index = this.mailPage * pageSize + i;
            if (index >= mail.size()) {
                break;
            }
            int col = i % cols;
            int row = i / cols;
            int sx = startX + col * cellW;
            int sy = startY + row * cellH;
            int iconX = sx + 10;
            int iconY = sy + 1;
            var entry = mail.get(index);
            Bricks.slot(g, iconX - 1, iconY - 1);
            boolean done = entry.type == cn.autoforged.server_shop_mod_1789689358.data.MailType.MONEY
                    ? entry.isViewed : entry.isClaimed;
            // 需求：变灰逻辑只在渲染时处理，通过 setColor 降低亮度，绝不修改 ItemStack 的 NBT。
            if (done) {
                g.setColor(0.4F, 0.4F, 0.4F, 1.0F);
            }
            if (!entry.stack.isEmpty()) {
                g.renderItem(entry.stack, iconX, iconY);
                g.renderItemDecorations(this.font, entry.stack, iconX, iconY);
            }
            if (done) {
                g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                g.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, 0x66000000);
            }
            // 金额类邮件在图标下显示星币数额；物品类在图标下显示状态。
            String state;
            if (entry.type == cn.autoforged.server_shop_mod_1789689358.data.MailType.MONEY) {
                state = ClientShopData.money(entry.currencyAmount);
                if (done) {
                    state = Component.translatable("gui.server_shop_mod.mail.viewed").getString();
                }
                g.drawCenteredString(this.font, state, sx + cellW / 2, sy + 20, done ? COLOR_MUTED : COLOR_GOLD);
            } else {
                state = Component.translatable(done ? "gui.server_shop_mod.mail.claimed"
                        : "gui.server_shop_mod.mail.unclaimed").getString();
                g.drawCenteredString(this.font, state, sx + cellW / 2, sy + 20, done ? COLOR_MUTED : 0x88FF88);
            }
            // 点击图标：物品类领取、金额类查看/领取（已处理的邮件不再响应）。
            CompoundTag claimExtra = new CompoundTag();
            claimExtra.putString("mail_id", entry.mailId);
            if (entry.type == cn.autoforged.server_shop_mod_1789689358.data.MailType.MONEY) {
                if (!entry.isViewed) {
                    addRegion(iconX - 1, iconY - 1, 18, 18, "view_mail", null, claimExtra);
                }
            } else if (!entry.isClaimed) {
                addRegion(iconX - 1, iconY - 1, 18, 18, "claim_mail", null, claimExtra);
            }
            // 需求：每封邮件一个删除按钮（右上角叉），未处理时点击弹二次确认。
            // 放在图标区域外侧，避免与“领取”点击区重叠。
            addRegion(sx + cellW - 9, sy, 9, 9, "mail_delete", null, claimExtra);
            g.drawString(this.font, "x", sx + cellW - 8, sy + 1, 0xFF6B6B, false);
            if (mx >= iconX && mx < iconX + 16 && my >= iconY && my < iconY + 16) {
                if (!entry.stack.isEmpty()) {
                    // 悬停显示名称/附魔/属性/NBT/标签等完整信息。
                    g.renderTooltip(this.font, entry.stack, mx, my);
                }
                hovered = entry;
            }
        }
        if (mail.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.mail_empty"), cx + cw / 2, cy + 60, COLOR_MUTED);
        }
        // 悬停邮件时在底部显示发送者、时间、说明与状态。
        if (hovered != null) {
            String sender = hovered.sender == null || hovered.sender.isEmpty() ? "-" : hovered.sender;
            String time = hovered.sentAt > 0L
                    ? java.time.Instant.ofEpochMilli(hovered.sentAt)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "-";
            String desc = mailDescription(hovered);
            String line = Component.translatable("gui.server_shop_mod.mail_info", sender, time).getString();
            g.drawString(this.font, line, cx + 4, cy + ch - 42, COLOR_MUTED, true);
            g.drawString(this.font, desc, cx + 4, cy + ch - 30, COLOR_TEXT, true);
        }
        int buttonY = cy + ch - 18;
        addRegion(cx + 4, buttonY, 96, 16, "claim_all_mail", null, null);
        drawButton(g, cx + 4, buttonY, 96, 16, Component.translatable("gui.server_shop_mod.mail_claim_all"));
        // 需求四：一键删除已领取/已查看的邮件。
        addRegion(cx + 104, buttonY, 88, 16, "delete_read_mail", null, null);
        drawButton(g, cx + 104, buttonY, 88, 16, Component.translatable("gui.server_shop_mod.mail_delete_read"));
        // 邮箱翻页：只要邮箱非空就显示页码（只有一页时也显示“第 1 / 1 页”，让玩家知道这里能翻页）。
        if (!mail.isEmpty()) {
            int prevX = cx + cw - 130;
            int nextX = cx + cw - 44;
            if (pages > 1) {
                addRegion(prevX, buttonY, 40, 16, "mail_prev", null, null);
                addRegion(nextX, buttonY, 40, 16, "mail_next", null, null);
                drawButton(g, prevX, buttonY, 40, 16, Component.translatable("gui.server_shop_mod.page_prev"));
                drawButton(g, nextX, buttonY, 40, 16, Component.translatable("gui.server_shop_mod.page_next"));
            }
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.page", this.mailPage + 1, pages),
                    cx + cw - 67, buttonY + 4, COLOR_MUTED);
        }
    }

    /** 需求：根据邮件来源生成说明文字（物品退还 / 星币到账 / 管理员发放）。 */
    private static String mailDescription(cn.autoforged.server_shop_mod_1789689358.data.MailEntry entry) {
        String item = entry.stack.isEmpty() ? "-" : entry.stack.getHoverName().getString();
        // 物品类邮件带上数量，避免“64 个钻石”和“1 个钻石”在邮箱里描述完全一样。
        String itemWithCount = entry.stack.isEmpty() ? "-"
                : entry.stack.getHoverName().getString() + "×" + entry.stack.getCount();
        String sender = entry.sender == null || entry.sender.isEmpty() ? "-" : entry.sender;
        // 管理员发放的邮件单独说明：物品类显示物品名，金额类显示金币数。
        if ("admin_grant".equals(entry.source)) {
            return entry.type == cn.autoforged.server_shop_mod_1789689358.data.MailType.MONEY
                    ? Component.translatable("gui.server_shop_mod.mail.desc.admin_grant_money", sender,
                            ClientShopData.money(entry.currencyAmount)).getString()
                    : Component.translatable("gui.server_shop_mod.mail.desc.admin_grant_item", sender, itemWithCount)
                            .getString();
        }
        String key = switch (entry.source == null ? "" : entry.source) {
            case "stall_unsold" -> "gui.server_shop_mod.mail.desc.stall_unsold";
            case "stall_bought" -> "gui.server_shop_mod.mail.desc.stall_bought";
            case "auction_refund" -> "gui.server_shop_mod.mail.desc.auction_refund";
            case "auction_unsold" -> "gui.server_shop_mod.mail.desc.auction_unsold";
            case "auction_won" -> "gui.server_shop_mod.mail.desc.auction_won";
            case "stall_sold" -> "gui.server_shop_mod.mail.desc.stall_sold";
            case "auction_sold" -> "gui.server_shop_mod.mail.desc.auction_sold";
            default -> "gui.server_shop_mod.mail.desc.default";
        };
        if (entry.type == cn.autoforged.server_shop_mod_1789689358.data.MailType.MONEY) {
            return Component.translatable(key, sender, item, ClientShopData.money(entry.currencyAmount)).getString();
        }
        return Component.translatable(key, item).getString();
    }

    // =========================================================== 管理员配置（仅 /shop admin 打开）

    private void drawAdmin(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        boolean shop = !"buyback".equals(adminMode);
        // 需求 11：顶部明确切换“每日商店/每日收购”，并高亮当前编辑模块。
        addRegion(cx + 4, cy + 2, 70, 16, "admin_mode", null, modeTag("shop"));
        addRegion(cx + 78, cy + 2, 70, 16, "admin_mode", null, modeTag("buyback"));
        drawButton(g, cx + 4, cy + 2, 70, 16, Component.translatable("gui.server_shop_mod.admin_shop"), shop);
        drawButton(g, cx + 78, cy + 2, 70, 16, Component.translatable("gui.server_shop_mod.admin_buyback"), !shop);
        g.drawString(this.font, Component.translatable("gui.server_shop_mod.admin_current",
                Component.translatable(shop ? "gui.server_shop_mod.admin_shop" : "gui.server_shop_mod.admin_buyback")),
                cx + 156, cy + 6, COLOR_GOLD, true);

        List<ConfiguredOffer> pool = shop ? ClientShopData.adminShop : ClientShopData.adminBuyback;
        int pageSize = 6;
        int pages = Math.max(1, (pool.size() + pageSize - 1) / pageSize);
        adminPage = Mth.clamp(adminPage, 0, pages - 1);
        for (int i = 0; i < pageSize; i++) {
            int index = adminPage * pageSize + i;
            if (index >= pool.size()) {
                break;
            }
            ConfiguredOffer offer = pool.get(index);
            int y = cy + 24 + i * 24;
            g.fill(cx + 6, y, cx + 12, y + 12, 0xFF000000 | offer.rarity.color());
            Bricks.slot(g, cx + 16, y);
            g.renderItem(offer.stack, cx + 16, y);
            g.renderItemDecorations(this.font, offer.stack, cx + 16, y);
            g.drawString(this.font, offer.stack.getHoverName(), cx + 40, y + 1, COLOR_TEXT, true);
            g.drawString(this.font, Component.translatable(offer.rarity.translationKey()), cx + 130, y + 1,
                    offer.rarity.color(), true);
            g.drawString(this.font, ClientShopData.money(offer.price), cx + 176, y + 1, COLOR_GOLD, true);
            // 需求 6/7：显示管理员配置的数量（未配置时展示实际生效的默认上限）。
            int effectiveAmount = shop ? offer.amountOr(offer.rarity.purchaseLimit())
                    : offer.amountOr(Config.INSTANCE.buybackCapacity.get());
            g.drawString(this.font, Component.translatable("gui.server_shop_mod.admin_amount", effectiveAmount),
                    cx + 244, y + 1, COLOR_MUTED, true);
            CompoundTag extra = new CompoundTag();
            extra.putString("mode", shop ? "shop" : "buyback");
            extra.putInt("index", index);
            addRegion(cx + 14, y - 1, cw - 18, 20, "admin_edit_gui", "admin_remove", extra);
            if (mx >= cx + 16 && mx < cx + 32 && my >= y && my < y + 16) {
                g.renderTooltip(this.font, offer.stack, mx, my);
            }
        }
        if (pool.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.admin_empty"), cx + cw / 2, cy + 70, COLOR_MUTED);
        }
        int buttonY = cy + ch - 18;
        CompoundTag addExtra = new CompoundTag();
        addExtra.putString("mode", shop ? "shop" : "buyback");
        addRegion(cx + 4, buttonY, 90, 16, "admin_add", null, addExtra);
        drawButton(g, cx + 4, buttonY, 90, 16, Component.translatable("gui.server_shop_mod.admin_add"));
        // 管理员发放入口 + 交易记录：两枚按钮都排在翻页按钮左侧，避免相互遮挡（原来会盖住“上一页”）。
        int grantY = buttonY - 18;
        addRegion(cx + 4, grantY, 106, 16, "admin_grant_open", null, null);
        drawButton(g, cx + 4, grantY, 106, 16, Component.translatable("gui.server_shop_mod.admin_grant_open"));
        addRegion(cx + 114, grantY, 76, 16, "admin_history_gui", null, null);
        drawButton(g, cx + 114, grantY, 76, 16, Component.translatable("gui.server_shop_mod.admin_history"));
        if (pages > 1) {
            // 翻页按钮与页码单独占一行，避免与“添加条目/提示”叠在一起（需求 1/9）。
            int pageY = buttonY - 18;
            int prevX = cx + cw - 130;
            int nextX = cx + cw - 44;
            addRegion(prevX, pageY, 40, 16, "admin_prev", null, null);
            addRegion(nextX, pageY, 40, 16, "admin_next", null, null);
            drawButton(g, prevX, pageY, 40, 16, Component.translatable("gui.server_shop_mod.page_prev"));
            drawButton(g, nextX, pageY, 40, 16, Component.translatable("gui.server_shop_mod.page_next"));
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.page", adminPage + 1, pages),
                    cx + cw - 67, pageY + 4, COLOR_MUTED);
        }
        g.drawString(this.font, Component.translatable("gui.server_shop_mod.admin_hint"), cx + 100, buttonY + 4, COLOR_MUTED, true);
    }

    // =========================================================== 交互

    /** 鼠标滚轮翻页：对当前页签的列表上滚/下滚即可翻页。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            int step = scrollY > 0.0D ? -1 : 1;
            switch (currentTab) {
                case "auction" -> {
                    int pages = Math.max(1, (ClientShopData.auctions.size() + 4) / 5);
                    auctionPage = Mth.clamp(auctionPage + step, 0, pages - 1);
                    return true;
                }
                case "mail" -> {
                    int pages = Math.max(1, (ClientShopData.mail.size() + 44) / 45);
                    mailPage = Mth.clamp(mailPage + step, 0, pages - 1);
                    return true;
                }
                case "admin" -> {
                    int size = "buyback".equals(adminMode)
                            ? ClientShopData.adminBuyback.size() : ClientShopData.adminShop.size();
                    int pages = Math.max(1, (size + 5) / 6);
                    adminPage = Mth.clamp(adminPage + step, 0, pages - 1);
                    return true;
                }
                default -> {
                    // 每日商店/收购没有分页，交给默认处理。
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Region region : this.regions) {
            if (mouseX < region.x || mouseX >= region.x + region.w || mouseY < region.y || mouseY >= region.y + region.h) {
                continue;
            }
            if (button == 0 && region.action != null) {
                Bricks.click();
                onAction(region.action, region.extra);
                return true;
            }
            if (button == 1 && region.rightAction != null) {
                Bricks.click();
                onAction(region.rightAction, region.extra);
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
            List<String> tabs = visibleTabs();
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < tabs.size()) {
                String tab = tabs.get(index);
                // 需求 3：点击“玩家摊位”改为进入上架物品（摊位管理）界面，逛摊位走单独的按钮。
                currentTab = tab;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onAction(String action, CompoundTag extra) {
        switch (action) {
            case "set_tab" -> {
                String tab = extra.getString("tab");
                // 需求 3：点击“玩家摊位”进入上架物品界面；逛摊位用下方“逛摊位”按钮。
                currentTab = tab;
                auctionPage = 0;
                adminPage = 0;
                mailPage = 0;
                // 需求 10：打开对应模块即视为已读，请求服务端清除该类红点。
                clearTabNotification(tab);
            }
            case "auction_prev" -> auctionPage = Math.max(0, auctionPage - 1);
            case "auction_next" -> auctionPage++;
            case "mail_prev" -> mailPage = Math.max(0, mailPage - 1);
            case "mail_next" -> mailPage++;
            case "admin_prev" -> adminPage = Math.max(0, adminPage - 1);
            case "admin_next" -> adminPage++;
            case "admin_mode" -> {
                adminMode = extra.getString("mode");
                adminPage = 0;
                // 需求 11：切换编辑模块时通知服务端，后续放入的物品写入对应列表。
                sendAdminType();
            }
            case "flip_shop" -> {
                if (extra != null) {
                    this.flipStart.putIfAbsent(extra.getInt("index"), tickCounter);
                }
                // 需求 11：通知服务端持久化翻开状态并播放对应稀有度音效。
                send("flip_shop", extra);
            }
            // 付费刷新每日商店：服务端校验次数与余额后重新生成整页卡片。
            case "refresh_daily_shop" -> send("refresh_daily_shop", null);
            // 需求 8：每日商店购买前弹出数量输入（可多件），底部左侧确认/右侧取消。
            case "buy_shop" -> {
                int idx = extra == null ? -1 : extra.getInt("index");
                if (idx >= 0 && idx < ClientShopData.dailyShop.size()) {
                    var entry = ClientShopData.dailyShop.get(idx);
                    CompoundTag req = new CompoundTag();
                    req.putInt("index", idx);
                    req.putString("default_quantity", "1");
                    req.putLong("unit_price", entry.price);
                    req.putLong("balance", ClientShopData.balance);
                    req.putString("info", Component.translatable("gui.server_shop_mod.buy_shop_info",
                            entry.stack.getHoverName(), ClientShopData.money(entry.price), entry.remaining()).getString());
                    req.putBoolean("confirm_right", false);
                    openInput("buy_shop", "gui.server_shop_mod.input.buy_quantity", req, "quantity");
                }
            }
            // 需求 9：收购前弹出数量输入，玩家自行调节卖出数量（并提示该物品的收购上限）。
            case "sell_buyback_gui" -> {
                CompoundTag req = extra == null ? new CompoundTag() : extra.copy();
                int idx = req.getInt("index");
                if (idx >= 0 && idx < ClientShopData.dailyBuyback.size()) {
                    req.putString("info", Component.translatable("gui.server_shop_mod.buyback_max",
                            ClientShopData.dailyBuyback.get(idx).remaining()).getString());
                }
                openInput("sell_buyback", "gui.server_shop_mod.input.sell_quantity", req, "quantity");
            }
            // 需求 2：竞拍界面改为带物品图标、快捷加减价的专用界面。
            case "bid_gui" -> {
                ClientShopData.AuctionView view = findAuction(extra == null ? 0L : extra.getLong("id"));
                if (view != null) {
                    Minecraft.getInstance().setScreen(new AuctionBidScreen(view));
                }
            }
            // 需求：删除邮件；未领取/未查看时必须先二次确认，防止误删资产。
            case "mail_delete" -> {
                String mailId = extra == null ? "" : extra.getString("mail_id");
                cn.autoforged.server_shop_mod_1789689358.data.MailEntry entry = findMail(mailId);
                if (entry == null) {
                    return;
                }
                if (entry.pending()) {
                    confirmDeleteMail(entry);
                } else {
                    send("delete_mail", extra);
                }
            }
            // 需求 4：转账先打开在线玩家列表（带搜索框），点选玩家后再输入金额。
            case "transfer_gui" -> {
                clearNotification(NotificationType.WALLET);
                Minecraft.getInstance().setScreen(new TradeSelectScreen(true));
            }
            case "trade_select" -> Minecraft.getInstance().setScreen(new TradeSelectScreen(false));
            case "trade_gui" -> openInput("trade_request", "gui.server_shop_mod.input.trade_target", null, "target");
            case "stall_edit_gui" -> openInput("stall_apply_price", "gui.server_shop_mod.input.stall_price", extra, "price");
            // 需求 6/7：管理界面可编辑价值(price)、品质(rarity)、数量(quantity)，并带出当前值。
            case "admin_edit_gui" -> openAdminEdit(extra);
            // 管理员发放界面由服务端打开容器界面（物品放服务端槽位里，客户端不可伪造）。
            case "admin_grant_open" -> send("admin_grant_open", null);
            // 交易记录：复用输入弹窗收集玩家名，服务端把流水回传到聊天栏。
            case "admin_history_gui" -> openInput("admin_history", "gui.server_shop_mod.input.history", null, "target");
            // 需求 10-5：左下角齿轮打开提醒音效设置。
            case "notif_settings" -> {
                Bricks.click();
                Minecraft.getInstance().setScreen(new NotificationSettingsScreen());
            }
            case "open_avatar" -> {
                clearNotification(NotificationType.STALL);
                Minecraft.getInstance().setScreen(new AvatarScreen());
            }
            default -> send(action, extra);
        }
    }

    /** 需求：按 mailId 取本地缓存的邮件（仅用于渲染/二次确认，不作为权威数据）。 */
    private static cn.autoforged.server_shop_mod_1789689358.data.MailEntry findMail(String mailId) {
        if (mailId == null || mailId.isEmpty()) {
            return null;
        }
        for (cn.autoforged.server_shop_mod_1789689358.data.MailEntry entry : ClientShopData.mail) {
            if (mailId.equals(entry.mailId)) {
                return entry;
            }
        }
        return null;
    }

    /** 需求：删除未领取/未查看邮件前的二次确认弹窗。 */
    private void confirmDeleteMail(cn.autoforged.server_shop_mod_1789689358.data.MailEntry entry) {
        Minecraft.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                CompoundTag tag = new CompoundTag();
                tag.putString("mail_id", entry.mailId);
                tag.putBoolean("confirmed", true);
                send("delete_mail", tag);
            }
            Minecraft.getInstance().setScreen(this);
        },
                Component.translatable("gui.server_shop_mod.mail.delete_title"),
                Component.literal(mailDescription(entry))));
    }

    /** 需求 6/7：管理界面编辑条目时带出当前价值/品质/数量。 */
    private void openAdminEdit(CompoundTag extra) {
        if (extra == null) {
            return;
        }
        boolean shop = !"buyback".equals(extra.getString("mode"));
        int index = extra.getInt("index");
        List<ConfiguredOffer> pool = shop ? ClientShopData.adminShop : ClientShopData.adminBuyback;
        CompoundTag req = extra.copy();
        if (index >= 0 && index < pool.size()) {
            ConfiguredOffer offer = pool.get(index);
            req.putString("default_price", Long.toString(offer.price));
            req.putString("default_rarity", offer.rarity.id());
            req.putString("default_quantity", Integer.toString(offer.amount));
        }
        openInput("admin_apply_price", "gui.server_shop_mod.input.admin_offer", req, "price", "rarity", "quantity");
    }

    private static ClientShopData.AuctionView findAuction(long id) {
        for (ClientShopData.AuctionView view : ClientShopData.auctions) {
            if (view.id == id) {
                return view;
            }
        }
        return null;
    }

    private void send(String action, CompoundTag extra) {
        CompoundTag tag = extra == null ? new CompoundTag() : extra.copy();
        tag.putString("action", action);
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
    }

    /** 需求 10：本地先清红点，并请求服务端权威清除对应类型未读。 */
    private void clearNotification(NotificationType type) {
        if (type == null) {
            return;
        }
        ClientNotifications.clearLocal(type);
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "notif_clear");
        tag.putString("type", type.id());
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
    }

    private void clearTabNotification(String tab) {
        switch (tab) {
            case "daily", "buyback" -> clearNotification(NotificationType.SHOP);
            case "auction" -> clearNotification(NotificationType.AUCTION);
            case "stall" -> clearNotification(NotificationType.STALL);
            case "mail" -> clearNotification(NotificationType.MAIL);
            default -> {
            }
        }
    }

    private void openInput(String action, String titleKey, CompoundTag extra, String... fields) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "input");
        tag.putString("action", action);
        tag.putString("title", titleKey);
        ListTag list = new ListTag();
        for (String field : fields) {
            list.add(StringTag.valueOf(field));
        }
        tag.put("fields", list);
        if (extra != null) {
            tag.merge(extra);
        }
        Minecraft.getInstance().setScreen(new ShopInputScreen(tag));
    }

    private void addRegion(int x, int y, int w, int h, String action, String rightAction, CompoundTag extra) {
        this.regions.add(new Region(x, y, w, h, action, rightAction, extra));
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label) {
        drawButton(g, x, y, w, h, label, false);
    }

    /** 需求 11：选中的编辑模块用金色边框与高亮底色。 */
    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label, boolean selected) {
        g.fill(x, y, x + w, y + h, selected ? 0xFF4C4C4C : 0xFF3A3A3A);
        g.fill(x, y, x + w, y + 1, selected ? COLOR_GOLD : 0xFF6A6A6A);
        if (selected) {
            g.fill(x, y + h - 1, x + w, y + h, COLOR_GOLD);
        }
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, selected ? COLOR_GOLD : COLOR_TEXT);
    }

    private static CompoundTag modeTag(String mode) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode);
        return tag;
    }

    private static String symbol() {
        return ClientShopData.symbol;
    }

    /** 剩余时间以现实时间毫秒计（需求 1）。 */
    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    private record Region(int x, int y, int w, int h, String action, String rightAction, CompoundTag extra) {
    }
}
