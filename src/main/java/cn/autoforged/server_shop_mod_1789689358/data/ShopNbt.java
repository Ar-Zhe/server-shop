package cn.autoforged.server_shop_mod_1789689358.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** 统一的 NBT 读写工具，保证 ItemStack 的完整组件数据被序列化。 */
public final class ShopNbt {
    private ShopNbt() {
    }

    public static CompoundTag saveStack(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (stack != null && !stack.isEmpty()) {
            tag.put("item", stack.saveOptional(registries));
        }
        return tag;
    }

    public static ItemStack loadStack(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || !tag.contains("item", Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(registries, tag.getCompound("item"));
    }

    /**
     * 需求四：容器类物品（潜影盒、精妙背包等）深拷贝。
     * 普通 {@link ItemStack#copy()} 与源栈共享组件对象（例如容器内容物列表），
     * 这里强制走一次 NBT 序列化/反序列化，保证内容物、自定义 NBT、标签与属性数据
     * 完全独立且不丢失，任一方向后续修改都不会影响另一方。
     */
    public static ItemStack deepCopy(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return loadStack(saveStack(stack, registries), registries);
    }

    public static ListTag newList() {
        return new ListTag();
    }
}
