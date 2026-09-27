package cn.autoforged.server_shop_mod_1789689358;

import cn.autoforged.server_shop_mod_1789689358.menu.ModMenus;
import cn.autoforged.server_shop_mod_1789689358.sound.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ServerShopMod.MODID)
public class ServerShopMod {
    public static final String MODID = "server_shop_mod_1789689358";

    public ServerShopMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModMenus.register(modEventBus);
        ModSounds.register(modEventBus);
    }
}
