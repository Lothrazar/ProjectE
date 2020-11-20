package moze_intel.projecte.gameObjs.registries;

import moze_intel.projecte.gameObjs.blocks.InterdictionTorch;
import moze_intel.projecte.gameObjs.blocks.InterdictionTorchWall;
import moze_intel.projecte.gameObjs.blocks.Pedestal;
import moze_intel.projecte.gameObjs.registration.impl.BlockDeferredRegister;
import moze_intel.projecte.gameObjs.registration.impl.BlockRegistryObject;
import moze_intel.projecte.gameObjs.registration.impl.BlockRegistryObject.WallOrFloorBlockRegistryObject;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.WallOrFloorItem;

public class PEBlocks {

  public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister();
  public static final BlockRegistryObject<Pedestal, BlockItem> DARK_MATTER_PEDESTAL = BLOCKS.register("dm_pedestal", () -> new Pedestal(AbstractBlock.Properties.create(Material.ROCK).setRequiresTool().hardnessAndResistance(1, 3).setLightLevel(state -> 12)));
  public static final WallOrFloorBlockRegistryObject<InterdictionTorch, InterdictionTorchWall, WallOrFloorItem> INTERDICTION_TORCH = BLOCKS.registerWallOrFloorItem("interdiction_torch", InterdictionTorch::new, InterdictionTorchWall::new, AbstractBlock.Properties.create(Material.MISCELLANEOUS).doesNotBlockMovement().hardnessAndResistance(0).setLightLevel(state -> 14).tickRandomly());
}