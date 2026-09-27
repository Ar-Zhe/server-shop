package cn.autoforged.server_shop_mod_1789689358.client;

import cn.autoforged.server_shop_mod_1789689358.data.NotificationType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * 需求 10：客户端通知状态与渲染。
 * <p>
 * 未读数量完全来自服务端下发的 notify 包，客户端只缓存显示、不参与修改。
 * 红点带数字角标（>99 显示 99+），并用 sin 做透明度呼吸；提示音按类型开关且有 1 秒节流。
 */
public final class ClientNotifications {
    private static final Map<NotificationType, Integer> COUNTS = new EnumMap<>(NotificationType.class);

    /** 需求 10-5：整体提醒音效开关（不包含界面点击音效）。 */
    public static boolean masterSound = true;
    /** 需求 10-5：各类型提醒音效单独开关。 */
    private static final Map<NotificationType, Boolean> TYPE_SOUND = new EnumMap<>(NotificationType.class);

    private static long lastSoundMs;

    private ClientNotifications() {
    }

    static {
        for (NotificationType type : NotificationType.values()) {
            TYPE_SOUND.put(type, Boolean.TRUE);
        }
    }

    public static boolean typeSound(NotificationType type) {
        return TYPE_SOUND.getOrDefault(type, Boolean.TRUE);
    }

    public static void setTypeSound(NotificationType type, boolean enabled) {
        TYPE_SOUND.put(type, enabled);
    }

    public static void update(CompoundTag data) {
        CompoundTag counts = data.getCompound("counts");
        for (NotificationType type : NotificationType.values()) {
            if (counts.contains(type.id())) {
                COUNTS.put(type, counts.getInt(type.id()));
            }
        }
        if (data.getBoolean("bump")) {
            NotificationType bumped = NotificationType.byId(data.getString("type"));
            playSound(bumped);
        }
    }

    /** 本地立即清除（服务端随后会回传权威计数）。 */
    public static void clearLocal(NotificationType type) {
        COUNTS.put(type, 0);
    }

    public static int count(NotificationType type) {
        return COUNTS.getOrDefault(type, 0);
    }

    /** 需求 10-4：短时间内多次触发只播一次音（1 秒节流）。 */
    private static void playSound(NotificationType type) {
        if (!masterSound) {
            return;
        }
        if (type != null && !typeSound(type)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSoundMs < 1000L) {
            return;
        }
        lastSoundMs = now;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3F));
    }

    /**
     * 在 (right, top) 的右上角锚点绘制红点角标。
     * 使用相对坐标，天然兼容 GUI Scale；呼吸效果由时间计算 alpha，无需额外重绘。
     */
    public static void drawBadge(GuiGraphics g, Font font, NotificationType type, int right, int top) {
        int count = count(type);
        if (count <= 0) {
            return;
        }
        String text = count > 99 ? "99+" : String.valueOf(count);
        int textWidth = font.width(text);
        int width = Math.max(9, textWidth + 4);
        int height = 9;
        int x = right - width;
        int y = Math.max(0, top);
        // 需求 10：呼吸灯——透明度和时间相关。
        float pulse = Mth.sin((float) (System.currentTimeMillis() % 2000L) / 500.0F) * 0.5F + 0.5F;
        int alpha = 0x88 + (int) (0x77 * pulse);
        g.fill(x, y, right, y + height, (alpha << 24) | 0xFF3030);
        g.fill(x, y, right, y + 1, (alpha << 24) | 0xFF8080);
        g.drawString(font, text, x + (width - textWidth) / 2, y + 1, 0xFFFFFF, false);
    }
}
