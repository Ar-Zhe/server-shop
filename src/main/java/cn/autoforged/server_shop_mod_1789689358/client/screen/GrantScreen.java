package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.client.ClientShopData;
import cn.autoforged.server_shop_mod_1789689358.menu.GrantMenu;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 管理员发放界面：左侧 27 个物品槽（真实槽位，可放入要发放的物品），
 * 右侧勾选玩家（支持搜索 / 翻页 / 一键全体），底部填写金额，点“发送”后：
 * 物品以邮件形式进入玩家邮箱（玩家在邮箱界面领取），金额按配置直接到账。
 * <p>
 * 界面只提交“发给谁 / 发多少钱”，物品以服务端容器内容为准，客户端无法伪造。
 */
public class GrantScreen extends AbstractContainerScreen<GrantMenu> {
    private static final int PANEL_W = 336;
    private static final int PANEL_H = 236;
    private static final int LIST_X = 182;
    private static final int LIST_W = 146;
    private static final int LIST_Y = 40;
    private static final int ROW_H = 20;
    private static final int PAGE_SIZE = 6;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_GOLD = 0xFFD700;
    private static final int COLOR_ONLINE = 0x55FF55;

    /** 已勾选的玩家 UUID；发送时若等于全部目标则改发 all 标记，节省包体。 */
    private final Set<UUID> selected = new HashSet<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private final List<ClientShopData.GrantTarget> pageTargets = new ArrayList<>();
    private EditBox amountBox;
    private EditBox searchBox;
    private int page;

    public GrantScreen(GrantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = GrantMenu.INVENTORY_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        this.amountBox = new EditBox(this.font, x + 44, y + 74, 100, 16,
                Component.translatable("gui.server_shop_mod.grant.amount"));
        this.amountBox.setMaxLength(12);
        // 只允许数字，避免管理员输入非法金额。
        this.amountBox.setFilter(text -> text.isEmpty() || text.matches("\\d{1,12}"));
        this.amountBox.setHint(Component.translatable("gui.server_shop_mod.grant.amount_hint"));
        this.addRenderableWidget(this.amountBox);

        this.searchBox = new EditBox(this.font, x + LIST_X, y + 18, LIST_W, 16,
                Component.translatable("gui.server_shop_mod.grant.search_hint"));
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.translatable("gui.server_shop_mod.grant.search_hint"));
        this.searchBox.setResponder(text -> this.page = 0);
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.server_shop_mod.grant.all"),
                        b -> selectAll())
                .pos(x + 8, y + 96).size(78, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.server_shop_mod.grant.none"),
                        b -> this.selected.clear())
                .pos(x + 90, y + 96).size(78, 16).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.server_shop_mod.page_prev"),
                        b -> this.page = Math.max(0, this.page - 1))
                .pos(x + LIST_X, y + 162).size(40, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.server_shop_mod.page_next"),
                        b -> this.page++)
                .pos(x + LIST_X + LIST_W - 40, y + 162).size(40, 16).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.server_shop_mod.grant.send"),
                        b -> sendGrant())
                .pos(x + LIST_X, y + 184).size(LIST_W, 20).build());

        // 界面打开后主动请求一次目标列表，避免“先发包后开界面”的时序问题导致列表为空。
        CompoundTag request = new CompoundTag();
        request.putString("action", "admin_grant_request");
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(request));
    }

    /** 服务端下发新的目标列表后由 ClientPayloadHandler 调用（界面已打开时原地刷新）。 */
    public void onDataUpdated() {
        // 名单刷新后清掉已经不存在的选择，避免把无效 UUID 发给服务端。
        Set<UUID> live = new HashSet<>();
        for (ClientShopData.GrantTarget target : ClientShopData.grantTargets) {
            if (target.uuid != null) {
                live.add(target.uuid);
            }
        }
        this.selected.retainAll(live);
        this.page = Math.max(0, this.page);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =========================================================== 数据

    private List<ClientShopData.GrantTarget> filtered() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase();
        List<ClientShopData.GrantTarget> result = new ArrayList<>();
        for (ClientShopData.GrantTarget target : ClientShopData.grantTargets) {
            if (target.uuid == null) {
                continue;
            }
            if (!query.isEmpty() && !target.name.toLowerCase().contains(query)) {
                continue;
            }
            result.add(target);
        }
        return result;
    }

    private void selectAll() {
        this.selected.clear();
        // 只选中当前搜索结果里可见的玩家：管理员搜了某个名字再点“全体玩家”却发给全服，是很容易踩的坑。
        for (ClientShopData.GrantTarget target : filtered()) {
            if (target.uuid != null) {
                this.selected.add(target.uuid);
            }
        }
    }

    private long parseAmount() {
        String text = this.amountBox == null ? "" : this.amountBox.getValue().trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(text));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void sendGrant() {
        // 始终发送显式 UUID 列表：客户端持有的名单只是“打开界面那一刻”的快照。
        // 若改发 all 标记，服务端会按当前全表展开，把管理员从未在界面上看到的玩家（例如刚登录的）也一起发放。
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "admin_grant_send");
        tag.putLong("amount", parseAmount());
        tag.putBoolean("all", false);
        ListTag targets = new ListTag();
        for (UUID id : this.selected) {
            targets.add(StringTag.valueOf(id.toString()));
        }
        tag.put("targets", targets);
        PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
    }

    // =========================================================== 渲染

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        Bricks.panel(g, x, y, PANEL_W, PANEL_H);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Bricks.slot(g, x + 8 + col * 18, y + 18 + row * 18);
            }
        }
        // 玩家背包区域沿用统一的积木底图（3 行 + 快捷栏）。
        g.blit(Bricks.PLAYER_SLOTS, x + 7, y + GrantMenu.INVENTORY_Y - 1, 0, 0, 162, 76, 162, 76);
        // 右侧目标列表的浅色底，便于和面板区分。
        g.fill(x + LIST_X - 3, y + 15, x + LIST_X + LIST_W + 3, y + 205, 0x30101114);
        // 列表与文字都画在 renderBg 里：这样物品悬浮提示（super.render 之后绘制）永远在最上层。
        drawLabelsAndTargets(g, x, y, mouseX, mouseY);
    }

    private void drawLabelsAndTargets(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        this.rowRects.clear();
        // 计数用“当前搜索结果”的数量：用全表长度会出现“已选 5 / 200”却只看到 2 行的怪现象。
        List<ClientShopData.GrantTarget> visibleTargets = filtered();
        g.drawString(this.font, Component.translatable("gui.server_shop_mod.grant.amount_label"), x + 8, y + 78,
                COLOR_MUTED, true);
        g.drawString(this.font,
                Component.translatable("gui.server_shop_mod.grant.selected", this.selected.size(),
                        visibleTargets.size()),
                x + 8, y + 119, COLOR_GOLD, true);
        g.drawString(this.font, Component.translatable("gui.server_shop_mod.grant.hint"), x + 8, y + 131,
                COLOR_MUTED, true);

        List<ClientShopData.GrantTarget> targets = visibleTargets;
        int pages = Math.max(1, (targets.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Mth.clamp(this.page, 0, pages - 1);
        this.pageTargets.clear();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = this.page * PAGE_SIZE + i;
            if (index >= targets.size()) {
                break;
            }
            ClientShopData.GrantTarget target = targets.get(index);
            int rowY = y + LIST_Y + i * ROW_H;
            boolean checked = this.selected.contains(target.uuid);
            g.fill(x + LIST_X, rowY, x + LIST_X + LIST_W, rowY + ROW_H - 2, checked ? 0xFF39442F : 0xFF232427);
            if (checked) {
                g.fill(x + LIST_X, rowY, x + LIST_X + 2, rowY + ROW_H - 2, COLOR_GOLD);
            }
            drawCheckbox(g, x + LIST_X + 4, rowY + 5, checked);
            Skins.drawHead(g, target.uuid, x + LIST_X + 17, rowY + 1, 16);
            g.drawString(this.font, target.name, x + LIST_X + 37, rowY + 5, COLOR_TEXT, true);
            g.drawString(this.font,
                    Component.translatable(target.online
                            ? "gui.server_shop_mod.grant.online"
                            : "gui.server_shop_mod.grant.offline"),
                    x + LIST_X + LIST_W - 28, rowY + 5, target.online ? COLOR_ONLINE : COLOR_MUTED, true);
            this.rowRects.add(new int[]{x + LIST_X, rowY, LIST_W, ROW_H - 2, index});
            this.pageTargets.add(target);
        }
        if (targets.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.grant.empty"),
                    x + LIST_X + LIST_W / 2, y + 100, COLOR_MUTED);
        }
        if (pages > 1) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.page", this.page + 1, pages),
                    x + LIST_X + LIST_W / 2, y + 166, COLOR_MUTED);
        }
    }

    /** 自绘勾选框，避免依赖字体里的 ✔/☐ 字形。 */
    private static void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        g.fill(x, y, x + 9, y + 9, 0xFF101114);
        g.fill(x, y, x + 9, y + 1, 0xFF6A6A6A);
        g.fill(x, y + 8, x + 9, y + 9, 0xFF6A6A6A);
        g.fill(x, y, x + 1, y + 9, 0xFF6A6A6A);
        g.fill(x + 8, y, x + 9, y + 9, 0xFF6A6A6A);
        if (checked) {
            g.fill(x + 2, y + 2, x + 7, y + 7, COLOR_GOLD);
        }
    }

    // =========================================================== 交互

    /** 鼠标滚轮翻页（右侧玩家列表）。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            int pages = Math.max(1, (filtered().size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int next = Mth.clamp(this.page + (scrollY > 0.0D ? -1 : 1), 0, pages - 1);
            if (next != this.page) {
                this.page = next;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // rowRects 与 pageTargets 在同一轮渲染里按相同顺序填充，用下标直接对应，避免翻页瞬间点错人。
        for (int i = 0; i < this.rowRects.size(); i++) {
            int[] rect = this.rowRects.get(i);
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                ClientShopData.GrantTarget target = i < this.pageTargets.size() ? this.pageTargets.get(i) : null;
                if (target != null && target.uuid != null) {
                    Bricks.click();
                    if (!this.selected.remove(target.uuid)) {
                        this.selected.add(target.uuid);
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
