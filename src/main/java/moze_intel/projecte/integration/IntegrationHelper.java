package moze_intel.projecte.integration;

import java.util.function.Supplier;
import moze_intel.projecte.capability.ItemCapability;
import moze_intel.projecte.integration.curios.CurioItemCapability;
import moze_intel.projecte.integration.curios.CuriosIntegration;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

public class IntegrationHelper {

  public static final String CURIO_MODID = "curios";
  //Double supplier to make sure it does not resolve early
  public static final Supplier<Supplier<ItemCapability<?>>> CURIO_CAP_SUPPLIER = () -> CurioItemCapability::new;

  public static void sendIMCMessages(InterModEnqueueEvent event) {
    ModList modList = ModList.get();
    if (modList.isLoaded(CURIO_MODID)) {
      CuriosIntegration.sendIMC(event);
    }
  }
}