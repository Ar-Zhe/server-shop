package cn.autoforged.server_shop_mod_1789689358.network.payload;

import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** 客户端 -> 服务端。所有商店操作都通过这个包，data 里至少包含 "action"。 */
public record ServerboundShopActionPayload(CompoundTag data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundShopActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ServerShopMod.MODID, "shop_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundShopActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.COMPOUND_TAG, ServerboundShopActionPayload::data,
                    ServerboundShopActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
