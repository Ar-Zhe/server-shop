package cn.autoforged.server_shop_mod_1789689358.client;

import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import cn.autoforged.server_shop_mod_1789689358.client.screen.DepositScreen;
import cn.autoforged.server_shop_mod_1789689358.client.screen.GrantScreen;
import cn.autoforged.server_shop_mod_1789689358.menu.ModMenus;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ServerShopMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final String CATEGORY = "key.categories." + ServerShopMod.MODID;
    public static final Lazy<KeyMapping> OPEN_SHOP = Lazy.of(() -> new KeyMapping(
            "key." + ServerShopMod.MODID + ".open_shop",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY));

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.DEPOSIT.get(), DepositScreen::new);
        event.register(ModMenus.GRANT.get(), GrantScreen::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SHOP.get());
    }
}
