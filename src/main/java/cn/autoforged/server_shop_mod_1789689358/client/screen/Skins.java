package cn.autoforged.server_shop_mod_1789689358.client.screen;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

/** 在纯 Screen 中绘制玩家头像的公共工具（需求 1：摊位/名片显示对应玩家头像）。 */
public final class Skins {
    private Skins() {
    }

    public static void drawHead(GuiGraphics g, @Nullable UUID uuid, int x, int y, int size) {
        PlayerSkin skin = skinOf(uuid);
        PlayerFaceRenderer.draw(g, skin, x, y, size);
    }

    private static PlayerSkin skinOf(@Nullable UUID uuid) {
        if (uuid != null) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkin();
            }
            return DefaultPlayerSkin.get(uuid);
        }
        var player = Minecraft.getInstance().player;
        return player != null ? DefaultPlayerSkin.get(player.getUUID()) : DefaultPlayerSkin.get(new UUID(0L, 0L));
    }
}
