package moze_intel.projecte;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.mojang.authlib.GameProfile;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.capabilities.item.IAlchBagItem;
import moze_intel.projecte.api.capabilities.item.IExtraFunction;
import moze_intel.projecte.api.capabilities.item.IItemCharge;
import moze_intel.projecte.api.capabilities.item.IModeChanger;
import moze_intel.projecte.api.capabilities.item.IPedestalItem;
import moze_intel.projecte.api.capabilities.item.IProjectileShooter;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.emc.nbt.NBTManager;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEContainerTypes;
import moze_intel.projecte.gameObjs.registries.PEEntityTypes;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.gameObjs.registries.PESoundEvents;
import moze_intel.projecte.gameObjs.registries.PETileEntityTypes;
import moze_intel.projecte.handlers.InternalAbilities;
import moze_intel.projecte.handlers.InternalTimers;
import moze_intel.projecte.impl.IMCHandler;
import moze_intel.projecte.impl.capability.AlchBagImpl;
import moze_intel.projecte.impl.capability.AlchBagItemDefaultImpl;
import moze_intel.projecte.impl.capability.ChargeItemDefaultImpl;
import moze_intel.projecte.impl.capability.ExtraFunctionItemDefaultImpl;
import moze_intel.projecte.impl.capability.ModeChangerItemDefaultImpl;
import moze_intel.projecte.impl.capability.PedestalItemDefaultImpl;
import moze_intel.projecte.impl.capability.ProjectileShooterItemDefaultImpl;
import moze_intel.projecte.integration.IntegrationHelper;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.ThreadCheckUUID;
import moze_intel.projecte.utils.DummyIStorage;
import moze_intel.projecte.utils.EntityRandomizerHelper;
import moze_intel.projecte.utils.WorldTransmutations;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod(PECore.MODID)
@Mod.EventBusSubscriber(modid = PECore.MODID)
public class PECore {

  public static final String MODID = ProjectEAPI.PROJECTE_MODID;
  public static final String MODNAME = "ProjectE";
  public static final GameProfile FAKEPLAYER_GAMEPROFILE = new GameProfile(UUID.fromString("590e39c7-9fb6-471b-a4c2-c0e539b2423d"), "[" + MODNAME + "]");
  public static boolean DEV_ENVIRONMENT;
  public static final Logger LOGGER = LogManager.getLogger(MODID);
  public static final List<String> uuids = new ArrayList<>();
  public static ModContainer MOD_CONTAINER;

  public static void debugLog(String msg, Object... args) {
    if (DEV_ENVIRONMENT || ProjectEConfig.common.debugLogging.get()) {
      LOGGER.info(msg, args);
    }
    else {
      LOGGER.debug(msg, args);
    }
  }

  public static ResourceLocation rl(String path) {
    return new ResourceLocation(MODID, path);
  }

  public PECore() {
    MOD_CONTAINER = ModLoadingContext.get().getActiveContainer();
    IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
    modEventBus.addListener(this::commonSetup);
    modEventBus.addListener(this::imcQueue);
    modEventBus.addListener(this::imcHandle);
    PEBlocks.BLOCKS.register(modEventBus);
    PEContainerTypes.CONTAINER_TYPES.register(modEventBus);
    PEEntityTypes.ENTITY_TYPES.register(modEventBus);
    PEItems.ITEMS.register(modEventBus);
    PESoundEvents.SOUND_EVENTS.register(modEventBus);
    PETileEntityTypes.TILE_ENTITY_TYPES.register(modEventBus);
    //    MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::addReloadListenersLowest);
    //    MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    MinecraftForge.EVENT_BUS.addListener(this::serverStarting);
    //    MinecraftForge.EVENT_BUS.addListener(this::serverQuit);
    //Register our config files
    ProjectEConfig.register();
  }

  private void commonSetup(FMLCommonSetupEvent event) {
    DEV_ENVIRONMENT = FMLLoader.getNameFunction("srg").isPresent();
    AlchBagImpl.init();
    CapabilityManager.INSTANCE.register(InternalTimers.class, new DummyIStorage<>(), InternalTimers::new);
    CapabilityManager.INSTANCE.register(InternalAbilities.class, new DummyIStorage<>(), () -> new InternalAbilities(null));
    CapabilityManager.INSTANCE.register(IAlchBagItem.class, new DummyIStorage<>(), AlchBagItemDefaultImpl::new);
    //    CapabilityManager.INSTANCE.register(IAlchChestItem.class, new DummyIStorage<>(), AlchChestItemDefaultImpl::new);
    CapabilityManager.INSTANCE.register(IExtraFunction.class, new DummyIStorage<>(), ExtraFunctionItemDefaultImpl::new);
    CapabilityManager.INSTANCE.register(IItemCharge.class, new DummyIStorage<>(), ChargeItemDefaultImpl::new);
    CapabilityManager.INSTANCE.register(IModeChanger.class, new DummyIStorage<>(), ModeChangerItemDefaultImpl::new);
    CapabilityManager.INSTANCE.register(IPedestalItem.class, new DummyIStorage<>(), PedestalItemDefaultImpl::new);
    CapabilityManager.INSTANCE.register(IProjectileShooter.class, new DummyIStorage<>(), ProjectileShooterItemDefaultImpl::new);
    NBTManager.loadProcessors();
    event.enqueueWork(() -> {
      PacketHandler.register();
      // internals unsafe
      //      CraftingHelper.register(TomeEnabledCondition.SERIALIZER);
      //      ArgumentTypes.register(MODID + ":uuid", UUIDArgument.class, new ArgumentSerializer<>(UUIDArgument::new));
      //      ArgumentTypes.register(MODID + ":color", ColorArgument.class, new ArgumentSerializer<>(ColorArgument::new));
      //      ArgumentTypes.register(MODID + ":nss", NSSItemArgument.class, new ArgumentSerializer<>(NSSItemArgument::new));
    });
  }

  private void imcQueue(InterModEnqueueEvent event) {
    EntityRandomizerHelper.init();
    WorldTransmutations.init();
    IntegrationHelper.sendIMCMessages(event);
  }

  private void imcHandle(InterModProcessEvent event) {
    IMCHandler.handleMessages();
  }

  private void serverStarting(FMLServerStartingEvent event) {
    if (!ThreadCheckUUID.hasRunServer()) {
      new ThreadCheckUUID(true).start();
    }
  }
}