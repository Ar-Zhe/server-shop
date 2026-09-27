package cn.autoforged.server_shop_mod_1789689358.sound;

import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import cn.autoforged.server_shop_mod_1789689358.data.Rarity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义音效。每日商店翻牌时按稀有度播放对应文件：
 * legendary=传说.ogg、epic=史诗.ogg、rare=稀有.ogg、common=普通.ogg。
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, ServerShopMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> RARITY_LEGENDARY = sound("rarity_legendary");
    public static final DeferredHolder<SoundEvent, SoundEvent> RARITY_EPIC = sound("rarity_epic");
    public static final DeferredHolder<SoundEvent, SoundEvent> RARITY_RARE = sound("rarity_rare");
    public static final DeferredHolder<SoundEvent, SoundEvent> RARITY_COMMON = sound("rarity_common");
    /** 需求 10：界面点击音效，对应 sounds/fy.ogg。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = sound("ui_click");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(ServerShopMod.MODID, name)));
    }

    public static SoundEvent forRarity(Rarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> RARITY_LEGENDARY.get();
            case EPIC -> RARITY_EPIC.get();
            case RARE -> RARITY_RARE.get();
            case COMMON -> RARITY_COMMON.get();
        };
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
