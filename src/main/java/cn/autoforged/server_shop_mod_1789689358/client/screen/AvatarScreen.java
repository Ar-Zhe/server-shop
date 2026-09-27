package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.client.ClientShopData;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 摊位列表：按序号排列所有拥有摊位的玩家（已按需求取消头像），点击即可进入选购。 */
public class AvatarScreen extends Screen {
    private static final int PANEL_W = 240;
    private static final int PANEL_H = 210;
    private static final int ROW_H = 30;
    private static final int PAGE_SIZE = 5;
    private int page;
    private final List<int[]> rowRects = new ArrayList<>();

    public AvatarScreen() {
        super(Component.translatable("gui.server_shop_mod.avatar.title"));
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
        g.drawCenteredString(this.font, this.title, x + PANEL_W / 2, y + 10, 0xFFD700);

        List<ClientShopData.StallPlayer> players = ClientShopData.stallPlayers;
        int pages = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(this.page, pages - 1));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = this.page * PAGE_SIZE + i;
            if (index >= players.size()) {
                break;
            }
            ClientShopData.StallPlayer player = players.get(index);
            int rowY = y + 30 + i * ROW_H;
            g.fill(x + 10, rowY, x + PANEL_W - 10, rowY + ROW_H - 4, 0xFF232427);
            // 需求 1：摊位列表显示对应玩家头像。
            Skins.drawHead(g, player.uuid, x + 14, rowY + 4, 16);
            g.drawString(this.font, "#" + (index + 1), x + 36, rowY + 8, 0xFFD700, true);
            g.drawString(this.font, player.name, x + 58, rowY + 4, 0xFFFFFF, true);
            g.drawString(this.font, Component.translatable("gui.server_shop_mod.avatar_items", player.count),
                    x + 58, rowY + 14, 0xAAAAAA, true);
            this.rowRects.add(new int[]{x + 10, rowY, PANEL_W - 20, ROW_H - 4, index});
        }
        if (players.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.avatar_empty"),
                    x + PANEL_W / 2, y + 90, 0xAAAAAA);
        }
        if (pages > 1) {
            g.drawCenteredString(this.font, Component.translatable("gui.server_shop_mod.page", this.page + 1, pages),
                    x + PANEL_W / 2, y + PANEL_H - 36, 0xFFAAAAAA);
        }
        drawButton(g, x + 6, y + PANEL_H - 24, 48, 16, Component.translatable("gui.server_shop_mod.back"));
        drawButton(g, x + 58, y + PANEL_H - 24, 80, 16, Component.translatable("gui.server_shop_mod.stall_manage"));
        if (pages > 1) {
            drawButton(g, x + 142, y + PANEL_H - 24, 44, 16, Component.translatable("gui.server_shop_mod.page_prev"));
            drawButton(g, x + 190, y + PANEL_H - 24, 44, 16, Component.translatable("gui.server_shop_mod.page_next"));
        }
    }

    /** 鼠标滚轮翻页。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            int pages = Math.max(1, (ClientShopData.stallPlayers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
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
        Bricks.click();
        if (mouseX >= x + 6 && mouseX < x + 54 && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Minecraft.getInstance().setScreen(new ShopScreen("daily"));
            return true;
        }
        if (mouseX >= x + 58 && mouseX < x + 138 && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            // 需求 7/8：进入自己的摊位上架管理界面。
            Minecraft.getInstance().setScreen(new ShopScreen("stall"));
            return true;
        }
        List<ClientShopData.StallPlayer> players = ClientShopData.stallPlayers;
        int pages = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pages > 1 && mouseX >= x + 142 && mouseX < x + 186
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            this.page = Math.max(0, this.page - 1);
            return true;
        }
        if (pages > 1 && mouseX >= x + 190 && mouseX < x + 234
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            this.page = Math.min(pages - 1, this.page + 1);
            return true;
        }
        for (int[] rect : this.rowRects) {
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                ClientShopData.StallPlayer player = players.get(rect[4]);
                if (player.uuid != null) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("action", "open_stall");
                    tag.putString("owner", player.uuid.toString());
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
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
    }
}
