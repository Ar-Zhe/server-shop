package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** 浏览某个玩家的摊位，物品按上架先后顺序排列。 */
public class StallBrowseScreen extends Screen {
    private static final int PANEL_W = 260;
    private static final int PANEL_H = 220;
    private static final int ROW_H = 30;
    private static final int PAGE_SIZE = 5;

    private final String owner;
    private final String ownerName;
    private final UUID ownerUuid;
    private final List<Entry> entries = new ArrayList<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private int page;

    private record Entry(ItemStack stack, long price, int stock, int sold, int index) {
    }

    public StallBrowseScreen(CompoundTag tag) {
        super(Component.translatable("gui.server_shop_mod.stall_browse_title"));
        this.owner = tag.getString("owner");
        this.ownerName = tag.getString("ownerName");
        UUID parsedOwner = null;
        try {
            parsedOwner = UUID.fromString(this.owner);
        } catch (IllegalArgumentException ignored) {
        }
        this.ownerUuid = parsedOwner;
        applyEntries(tag);
    }

    /** 需求 5：购买后服务端推来最新库存，就地刷新列表而不是重建界面。 */
    public void updateEntries(CompoundTag tag) {
        this.entries.clear();
        applyEntries(tag);
    }

    private void applyEntries(CompoundTag tag) {
        ListTag list = tag.getList("entries", 10);
        var registries = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ItemStack stack = registries == null ? ItemStack.EMPTY : ItemStack.parseOptional(registries, c.getCompound("item"));
            // 需求 5/6：携带单件价格与剩余库存（旧数据没有 stock 时以栈数量回退）。
            int stock = c.contains("stock") ? c.getInt("stock") : Math.max(1, stack.getCount());
            // 需求 9：按商品分别显示已售出件数。
            int sold = c.contains("sold") ? c.getInt("sold") : 0;
            this.entries.add(new Entry(stack, c.getLong("price"), stock, sold, c.getInt("index")));
        }
    }

    private int left() {
        return (this.width - PANEL_W) / 2;
    }

    private int top() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.rowRects.clear();
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        Bricks.panel(g, x, y, PANEL_W, PANEL_H);
        g.drawCenteredString(this.font, Component.literal(ownerName), x + PANEL_W / 2, y + 10, 0xFFD700);
        // 需求 1：摊位界面显示摊主头像。
        if (ownerUuid != null) {
            Skins.drawHead(g, ownerUuid, x + 10, y + 6, 14);
        }

        int pages = Math.max(1, (this.entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(this.page, pages - 1));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = this.page * PAGE_SIZE + i;
            if (index >= this.entries.size()) {
                break;
            }
            Entry entry = this.entries.get(index);
            int rowY = y + 28 + i * ROW_H;
            g.fill(x + 10, rowY, x + PANEL_W - 10, rowY + ROW_H - 4, 0xFF2A2A2A);
            Bricks.slot(g, x + 15, rowY + 3);
            g.renderItem(entry.stack, x + 15, rowY + 3);
            g.renderItemDecorations(this.font, entry.stack, x + 15, rowY + 3);
            g.drawString(this.font, entry.stack.getHoverName(), x + 38, rowY + 3, 0xFFFFFF, true);
            // 需求 4/5：单价按单件计算。
            String priceText = Component.translatable("gui.server_shop_mod.stall_unit_price",
                    cn.autoforged.server_shop_mod_1789689358.client.ClientShopData.money(entry.price)).getString();
            g.drawString(this.font, priceText, x + 38, rowY + 14, 0xFFD700, true);
            if (entry.stock <= 0) {
                g.drawString(this.font, Component.translatable("gui.server_shop_mod.stall_sold_out"),
                        x + PANEL_W - 58, rowY + 3, 0xFF5555, true);
            } else {
                g.drawString(this.font, Component.translatable("gui.server_shop_mod.stall_stock", entry.stock),
                        x + PANEL_W - 58, rowY + 3, 0xAAAAAA, true);
            }
            // 需求 9：在商品旁显示该商品累计已售出件数。
            g.drawString(this.font, Component.translatable("gui.server_shop_mod.stall_sold_count", entry.sold),
                    x + PANEL_W - 58, rowY + 14, 0x88BBFF, true);
            this.rowRects.add(new int[]{x + 10, rowY, PANEL_W - 20, ROW_H - 4, index});
            if (mouseX >= x + 15 && mouseX < x + 31 && mouseY >= rowY + 3 && mouseY < rowY + 19) {
                g.renderTooltip(this.font, entry.stack, mouseX, mouseY);
            }
        }
        if (this.entries.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.stall_empty"),
                    x + PANEL_W / 2, y + 100, 0xAAAAAA);
        }
        if (pages > 1) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.page", this.page + 1, pages),
                    x + PANEL_W / 2, y + PANEL_H - 34, 0xAAAAAA);
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
            int pages = Math.max(1, (this.entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
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
        int x = left();
        int y = top();
        if (mouseX >= x + 10 && mouseX < x + 80 && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            Minecraft.getInstance().setScreen(new AvatarScreen());
            return true;
        }
        int pages = Math.max(1, (this.entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pages > 1 && mouseX >= x + 88 && mouseX < x + 146
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            this.page = Math.max(0, this.page - 1);
            return true;
        }
        if (pages > 1 && mouseX >= x + PANEL_W - 68 && mouseX < x + PANEL_W - 10
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            this.page = Math.min(pages - 1, this.page + 1);
            return true;
        }
        for (int[] rect : this.rowRects) {
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                Bricks.click();
                Entry entry = this.entries.get(rect[4]);
                if (entry.stock <= 0) {
                    return true;
                }
                // 需求 5：点击商品弹出数量选择小窗口（中间数量、下方输入框、底部取消/购买）。
                CompoundTag request = new CompoundTag();
                request.putString("kind", "input");
                request.putString("action", "stall_buy");
                request.putString("title", "gui.server_shop_mod.input.stall_buy");
                request.putString("owner", this.owner);
                request.putInt("index", entry.index);
                request.putString("default_quantity", "1");
                // 需求 7：防误触按钮对调——左侧确认、右侧取消。
                request.putBoolean("confirm_right", false);
                // 需求 7：弹窗内实时显示总价与余额变化。
                request.putLong("unit_price", entry.price);
                request.putLong("balance",
                        cn.autoforged.server_shop_mod_1789689358.client.ClientShopData.balance);
                request.putString("info", Component.translatable("gui.server_shop_mod.stall_buy_info",
                        entry.stack.getHoverName(),
                        cn.autoforged.server_shop_mod_1789689358.client.ClientShopData.money(entry.price),
                        entry.stock).getString());
                ListTag fields = new ListTag();
                fields.add(net.minecraft.nbt.StringTag.valueOf("quantity"));
                request.put("fields", fields);
                Minecraft.getInstance().setScreen(new ShopInputScreen(request));
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
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
    }
}
