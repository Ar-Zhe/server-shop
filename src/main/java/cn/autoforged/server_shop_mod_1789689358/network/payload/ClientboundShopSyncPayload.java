package cn.autoforged.server_shop_mod_1789689358.network.payload;

import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端。kind=snapshot 时同步整个商店界面数据；kind=input 时打开数值/文本输入界面。
 */
public record ClientboundShopSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundShopSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ServerShopMod.MODID, "shop_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundShopSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.COMPOUND_TAG, ClientboundShopSyncPayload::data,
                    ClientboundShopSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
