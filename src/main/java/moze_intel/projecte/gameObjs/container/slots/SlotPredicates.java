package moze_intel.projecte.gameObjs.container.slots;

import java.util.function.Predicate;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.AbstractFurnaceTileEntity;

public final class SlotPredicates {

  // slotrelayklein, slotmercurialklein
  public static final Predicate<ItemStack> EMC_HOLDER = input -> !input.isEmpty() && input.getCapability(ProjectEAPI.EMC_HOLDER_ITEM_CAPABILITY).isPresent();
  public static final Predicate<ItemStack> FURNACE_FUEL = input -> AbstractFurnaceTileEntity.isFuel(input);
  public static final Predicate<ItemStack> MERCURIAL_TARGET = input -> {
    if (input.isEmpty()) {
      return false;
    }
    BlockState state = ItemHelper.stackToState(input);
    return state != null && !state.getBlock().hasTileEntity(state);//&& EMCHelper.doesItemHaveEmc(input);
  };
}