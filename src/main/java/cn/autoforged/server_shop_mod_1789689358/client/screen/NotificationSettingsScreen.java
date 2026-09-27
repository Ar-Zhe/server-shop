package cn.autoforged.server_shop_mod_1789689358.client.screen;

import cn.autoforged.server_shop_mod_1789689358.client.ClientNotifications;
import cn.autoforged.server_shop_mod_1789689358.data.NotificationType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 需求 10-5：提醒音效设置界面。
 * 顶部是整体提醒音效总开关，下面是各类型单独开关。
 * 界面点击音效（ui_click）不经过本开关，因此关闭提醒音效仍能听到界面点击声。
 */
public class NotificationSettingsScreen extends Screen {
    private static final int PANEL_W = 240;
    private static final int PANEL_H = 194;
    private static final int ROW_H = 20;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_GOLD = 0xFFD700;
    private static final int COLOR_ON = 0xFF3FA34D;
    private static final int COLOR_OFF = 0xFF5A2A2A;

    private final List<Row> rows = new ArrayList<>();

    public NotificationSettingsScreen() {
        super(Component.translatable("gui.server_shop_mod.notify.settings_title"));
    }

    private int left() {
        return (this.width - PANEL_W) / 2;
    }

    private int top() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.rows.clear();
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        Bricks.panel(g, x, y, PANEL_W, PANEL_H);
        g.drawCenteredString(this.font, this.title, x + PANEL_W / 2, y + 9, COLOR_GOLD);

        int rowY = y + 26;
        addRow(g, x, rowY, Component.translatable("gui.server_shop_mod.notify.master"), null);
        rowY += ROW_H;
        for (NotificationType type : NotificationType.values()) {
            addRow(g, x, rowY, Component.translatable("gui.server_shop_mod.notify." + type.id()), type);
            rowY += ROW_H;
        }

        drawButton(g, x + 10, y + PANEL_H - 24, 70, 16, Component.translatable("gui.server_shop_mod.back"));
    }

    private void addRow(GuiGraphics g, int x, int y, Component label, NotificationType type) {
        g.fill(x + 10, y, x + PANEL_W - 10, y + ROW_H - 3, 0xFF1B1C1F);
        g.drawString(this.font, label, x + 16, y + 5, COLOR_TEXT, true);
        boolean on = type == null ? ClientNotifications.masterSound : ClientNotifications.typeSound(type);
        int bx = x + PANEL_W - 62;
        int by = y + 2;
        g.fill(bx, by, bx + 48, by + 13, on ? COLOR_ON : COLOR_OFF);
        g.fill(bx, by, bx + 48, by + 1, on ? 0xFF6FE07F : 0xFF8A4A4A);
        g.drawCenteredString(this.font, on ? Component.translatable("gui.server_shop_mod.notify.on")
                : Component.translatable("gui.server_shop_mod.notify.off"), bx + 24, by + 3, COLOR_TEXT);
        this.rows.add(new Row(bx, by, 48, 13, type));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left();
        int y = top();
        if (button == 0 && mouseX >= x + 10 && mouseX < x + 80
                && mouseY >= y + PANEL_H - 24 && mouseY < y + PANEL_H - 8) {
            Bricks.click();
            this.onClose();
            return true;
        }
        for (Row row : this.rows) {
            if (button == 0 && mouseX >= row.x && mouseX < row.x + row.w
                    && mouseY >= row.y && mouseY < row.y + row.h) {
                Bricks.click();
                if (row.type == null) {
                    ClientNotifications.masterSound = !ClientNotifications.masterSound;
                } else {
                    ClientNotifications.setTypeSound(row.type, !ClientNotifications.typeSound(row.type));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        // 返回主界面，不重新请求数据，保留当前页面上下文。
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ShopScreen("daily"));
        }
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

    private record Row(int x, int y, int w, int h, NotificationType type) {
    }
}
