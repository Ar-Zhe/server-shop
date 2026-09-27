package cn.autoforged.server_shop_mod_1789689358.menu;

import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ServerShopMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<DepositMenu>> DEPOSIT =
            MENUS.register("deposit", () -> IMenuTypeExtension.<DepositMenu>create(
                    (id, inventory, data) -> DepositMenu.clientSide(id, inventory)));

    /** 管理员发放界面（物品槽 + 管理员背包）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<GrantMenu>> GRANT =
            MENUS.register("grant", () -> IMenuTypeExtension.<GrantMenu>create(
                    (id, inventory, data) -> GrantMenu.clientSide(id, inventory)));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
