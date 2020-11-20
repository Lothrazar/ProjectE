package moze_intel.projecte;

import java.util.Map;
import moze_intel.projecte.gameObjs.gui.AlchBagScreen;
import moze_intel.projecte.gameObjs.gui.GUIMercurialEye;
import moze_intel.projecte.gameObjs.registration.impl.ContainerTypeRegistryObject;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEContainerTypes;
import moze_intel.projecte.gameObjs.registries.PEEntityTypes;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.gameObjs.registries.PETileEntityTypes;
import moze_intel.projecte.rendering.LayerYue;
import moze_intel.projecte.rendering.PedestalRenderer;
import moze_intel.projecte.rendering.entity.ExplosiveLensRenderer;
import moze_intel.projecte.rendering.entity.FireballRenderer;
import moze_intel.projecte.rendering.entity.LavaOrbRenderer;
import moze_intel.projecte.rendering.entity.LightningRenderer;
import moze_intel.projecte.rendering.entity.RandomizerRenderer;
import moze_intel.projecte.rendering.entity.WaterOrbRenderer;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.IHasContainer;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.gui.ScreenManager.IScreenFactory;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.client.renderer.entity.TippedArrowRenderer;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemModelsProperties;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

@Mod.EventBusSubscriber(modid = PECore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientRegistration {

  @SubscribeEvent
  public static void registerContainerTypes(RegistryEvent.Register<ContainerType<?>> event) {
    registerScreen(PEContainerTypes.ALCH_BAG_CONTAINER, AlchBagScreen::new);
    registerScreen(PEContainerTypes.MERCURIAL_EYE_CONTAINER, GUIMercurialEye::new);
  }

  @SubscribeEvent
  public static void clientSetup(FMLClientSetupEvent evt) {
    evt.enqueueWork(ClientKeyHelper::registerKeyBindings);
    //Tile Entity
    ClientRegistry.bindTileEntityRenderer(PETileEntityTypes.DARK_MATTER_PEDESTAL.get(), PedestalRenderer::new);
    //Entities
    RenderingRegistry.registerEntityRenderingHandler(PEEntityTypes.WATER_PROJECTILE.get(), WaterOrbRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(PEEntityTypes.LAVA_PROJECTILE.get(), LavaOrbRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(PEEntityTypes.MOB_RANDOMIZER.get(), RandomizerRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(PEEntityTypes.LENS_PROJECTILE.get(), ExplosiveLensRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(PEEntityTypes.FIRE_PROJECTILE.get(), FireballRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(PEEntityTypes.SWRG_PROJECTILE.get(), LightningRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(PEEntityTypes.HOMING_ARROW.get(), TippedArrowRenderer::new);
    //Render layers
    RenderTypeLookup.setRenderLayer(PEBlocks.INTERDICTION_TORCH.getBlock(), RenderType.getCutout());
    RenderTypeLookup.setRenderLayer(PEBlocks.INTERDICTION_TORCH.getWallBlock(), RenderType.getCutout());
    //Property Overrides
    addPropertyOverrides(PECore.rl("active"), (stack, world, entity) -> stack.hasTag() && stack.getTag().getBoolean(Constants.NBT_KEY_ACTIVE) ? 1F : 0F,
        // PEItems.GEM_OF_ETERNAL_DENSITY, PEItems.VOID_RING, 
        PEItems.ARCANA_RING, PEItems.ARCHANGEL_SMITE, PEItems.BLACK_HOLE_BAND, PEItems.BODY_STONE,
        PEItems.HARVEST_GODDESS_BAND, PEItems.IGNITION_RING, PEItems.LIFE_STONE, PEItems.MIND_STONE, PEItems.SOUL_STONE, PEItems.WATCH_OF_FLOWING_TIME,
        PEItems.ZERO_RING);
    addPropertyOverrides(PECore.rl("mode"), (stack, world, entity) -> stack.hasTag() ? stack.getTag().getInt(Constants.NBT_KEY_MODE) : 0F,
        PEItems.ARCANA_RING, PEItems.SWIFTWOLF_RENDING_GALE);
  }

  @SubscribeEvent
  public static void loadComplete(FMLLoadCompleteEvent evt) {
    // ClientSetup is too early to do this
    evt.enqueueWork(() -> {
      Map<String, PlayerRenderer> skinMap = Minecraft.getInstance().getRenderManager().getSkinMap();
      PlayerRenderer render = skinMap.get("default");
      render.addLayer(new LayerYue(render));
      render = skinMap.get("slim");
      render.addLayer(new LayerYue(render));
    });
  }

  private static void addPropertyOverrides(ResourceLocation override, IItemPropertyGetter propertyGetter, IItemProvider... itemProviders) {
    for (IItemProvider itemProvider : itemProviders) {
      ItemModelsProperties.registerProperty(itemProvider.asItem(), override, propertyGetter);
    }
  }

  private static <C extends Container, U extends Screen & IHasContainer<C>> void registerScreen(ContainerTypeRegistryObject<C> type, IScreenFactory<C, U> factory) {
    ScreenManager.registerFactory(type.get(), factory);
  }
}