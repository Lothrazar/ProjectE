package moze_intel.projecte.gameObjs.items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import moze_intel.projecte.capability.ItemCapability;
import moze_intel.projecte.capability.ItemCapabilityWrapper;
import moze_intel.projecte.utils.Constants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.ModList;

public class ItemPE extends Item {

  private final List<Supplier<ItemCapability<?>>> supportedCapabilities = new ArrayList<>();

  public ItemPE(Properties props) {
    super(props);
  }

  protected void addItemCapability(Supplier<ItemCapability<?>> capabilitySupplier) {
    supportedCapabilities.add(capabilitySupplier);
  }

  protected void addItemCapability(String modid, Supplier<Supplier<ItemCapability<?>>> capabilitySupplier) {
    if (ModList.get().isLoaded(modid)) {
      supportedCapabilities.add(capabilitySupplier.get());
    }
  }

  @Override
  public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    if (oldStack.getItem() != newStack.getItem()) {
      return true;
    }
    if (oldStack.hasTag() && newStack.hasTag()) {
      CompoundNBT newTag = newStack.getTag();
      CompoundNBT oldTag = oldStack.getTag();
      boolean diffActive = oldTag.contains(Constants.NBT_KEY_ACTIVE) && newTag.contains(Constants.NBT_KEY_ACTIVE)
          && !oldTag.get(Constants.NBT_KEY_ACTIVE).equals(newTag.get(Constants.NBT_KEY_ACTIVE));
      boolean diffMode = oldTag.contains(Constants.NBT_KEY_MODE) && newTag.contains(Constants.NBT_KEY_MODE)
          && !oldTag.get(Constants.NBT_KEY_MODE).equals(newTag.get(Constants.NBT_KEY_MODE));
      return diffActive || diffMode;
    }
    return false;
  }

  @Override
  public ICapabilityProvider initCapabilities(ItemStack stack, CompoundNBT nbt) {
    if (supportedCapabilities.isEmpty()) {
      return super.initCapabilities(stack, nbt);
    }
    return new ItemCapabilityWrapper(stack, supportedCapabilities);
  }
}