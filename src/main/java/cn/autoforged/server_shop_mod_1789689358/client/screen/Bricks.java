package cn.autoforged.server_shop_mod_1789689358.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;

/** autoforge_bricks 积木纹理的统一入口。所有界面背景都由这些积木拼装而成。 */
public final class Bricks {
    public static final ResourceLocation SLOT = tex("slot_default");
    public static final ResourceLocation PLAYER_SLOTS = tex("player_slots_9x4");
    public static final ResourceLocation FILL = tex("fill_white");
    public static final ResourceLocation SPLIT = tex("split_bar");
    public static final ResourceLocation CORNER_TL = tex("border_corner_tl");
    public static final ResourceLocation CORNER_TR = tex("border_corner_tr");
    public static final ResourceLocation CORNER_BL = tex("border_corner_bl");
    public static final ResourceLocation CORNER_BR = tex("border_corner_br");
    public static final ResourceLocation EDGE_TOP = tex("border_edge_top");
    public static final ResourceLocation EDGE_BOTTOM = tex("border_edge_bottom");
    public static final ResourceLocation EDGE_LEFT = tex("border_edge_left");
    public static final ResourceLocation EDGE_RIGHT = tex("border_edge_right");

    private Bricks() {
    }

    public static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("autoforge_bricks", "textures/gui/" + name + ".png");
    }

    /** 画一个带边框的窗口背景。需求 10：暗色半透明面板 + 细白边框。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        // 底层半透明暗色，配合积木白色边框形成简约暗黑风格。
        g.fill(x, y, x + w, y + h, 0xB0101114);
        g.setColor(0.10F, 0.11F, 0.13F, 0.96F);
        g.blit(FILL, x + 5, y + 5, 0, 0, w - 10, h - 10, 1, 1);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(CORNER_TL, x, y, 0, 0, 5, 5, 5, 5);
        g.blit(CORNER_TR, x + w - 5, y, 0, 0, 5, 5, 5, 5);
        g.blit(CORNER_BL, x, y + h - 5, 0, 0, 5, 5, 5, 5);
        g.blit(CORNER_BR, x + w - 5, y + h - 5, 0, 0, 5, 5, 5, 5);
        g.blit(EDGE_TOP, x + 5, y, 0, 0, w - 10, 5, 1, 5);
        g.blit(EDGE_BOTTOM, x + 5, y + h - 5, 0, 0, w - 10, 5, 1, 5);
        g.blit(EDGE_LEFT, x, y + 5, 0, 0, 5, h - 10, 5, 1);
        g.blit(EDGE_RIGHT, x + w - 5, y + 5, 0, 0, 5, h - 10, 5, 1);
    }

    public static void slot(GuiGraphics g, int x, int y) {
        g.blit(SLOT, x - 1, y - 1, 0, 0, 18, 18, 18, 18);
    }

    /** 需求 10：界面点击时播放自定义音效 sounds/fy.ogg。 */
    public static void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(
                        cn.autoforged.server_shop_mod_1789689358.sound.ModSounds.UI_CLICK.get(), 1.0F));
    }
}
