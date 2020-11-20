package moze_intel.projecte.gameObjs.registries;

import moze_intel.projecte.gameObjs.container.AlchBagContainer;
import moze_intel.projecte.gameObjs.container.MercurialEyeContainer;
import moze_intel.projecte.gameObjs.registration.impl.ContainerTypeDeferredRegister;
import moze_intel.projecte.gameObjs.registration.impl.ContainerTypeRegistryObject;

public class PEContainerTypes {

  public static final ContainerTypeDeferredRegister CONTAINER_TYPES = new ContainerTypeDeferredRegister();
  public static final ContainerTypeRegistryObject<AlchBagContainer> ALCH_BAG_CONTAINER = CONTAINER_TYPES.register("alchemical_bag", AlchBagContainer::fromNetwork);
  public static final ContainerTypeRegistryObject<MercurialEyeContainer> MERCURIAL_EYE_CONTAINER = CONTAINER_TYPES.register(PEItems.MERCURIAL_EYE, MercurialEyeContainer::fromNetwork);
}