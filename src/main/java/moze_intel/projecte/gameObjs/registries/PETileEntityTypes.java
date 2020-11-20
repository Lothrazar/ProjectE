package moze_intel.projecte.gameObjs.registries;

import moze_intel.projecte.gameObjs.registration.impl.TileEntityTypeDeferredRegister;
import moze_intel.projecte.gameObjs.registration.impl.TileEntityTypeRegistryObject;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.gameObjs.tiles.InterdictionTile;

public class PETileEntityTypes {

  public static final TileEntityTypeDeferredRegister TILE_ENTITY_TYPES = new TileEntityTypeDeferredRegister();
  public static final TileEntityTypeRegistryObject<InterdictionTile> INTERDICTION_TORCH = TILE_ENTITY_TYPES.register(PEBlocks.INTERDICTION_TORCH, InterdictionTile::new);
  public static final TileEntityTypeRegistryObject<DMPedestalTile> DARK_MATTER_PEDESTAL = TILE_ENTITY_TYPES.register(PEBlocks.DARK_MATTER_PEDESTAL, DMPedestalTile::new);
}