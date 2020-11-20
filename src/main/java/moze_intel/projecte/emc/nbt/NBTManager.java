package moze_intel.projecte.emc.nbt;

import java.util.ArrayList;
import java.util.List;
import moze_intel.projecte.api.nbt.INBTProcessor;
import moze_intel.projecte.config.NBTProcessorConfig;
import moze_intel.projecte.utils.AnnotationHelper;

public class NBTManager {

  private static final List<INBTProcessor> processors = new ArrayList<>();

  public static void loadProcessors() {
    if (processors.isEmpty()) {
      processors.addAll(AnnotationHelper.getNBTProcessors());
      //Setup the config for the NBT Processors
      NBTProcessorConfig.setup(processors);
    }
  }
}