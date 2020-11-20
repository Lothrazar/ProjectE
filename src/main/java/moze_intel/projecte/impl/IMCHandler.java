package moze_intel.projecte.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.imc.IMCMethods;
import moze_intel.projecte.api.imc.WorldTransmutationEntry;
import moze_intel.projecte.gameObjs.items.rings.TimeWatch;
import moze_intel.projecte.utils.EntityRandomizerHelper;
import moze_intel.projecte.utils.WorldHelper;
import moze_intel.projecte.utils.WorldTransmutations;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.InterModComms;

public class IMCHandler {

  public static void handleMessages() {
    Set<EntityType<?>> interd = new HashSet<>();
    InterModComms.getMessages(PECore.MODID, IMCMethods.BLACKLIST_INTERDICTION::equals)
        .filter(msg -> msg.getMessageSupplier().get() instanceof EntityType)
        .forEach(msg -> {
          EntityType<?> entityType = (EntityType<?>) msg.getMessageSupplier().get();
          interd.add(entityType);
          PECore.debugLog("Mod: '{}' registered Interdiction Torch Blacklist for EntityType: '{}'", msg.getSenderModId(), entityType.getRegistryName());
        });
    WorldHelper.setInterdictionBlacklist(interd);
    Set<EntityType<?>> swrg = new HashSet<>();
    InterModComms.getMessages(PECore.MODID, IMCMethods.BLACKLIST_SWRG::equals)
        .filter(msg -> msg.getMessageSupplier().get() instanceof EntityType)
        .forEach(msg -> {
          EntityType<?> entityType = (EntityType<?>) msg.getMessageSupplier().get();
          swrg.add(entityType);
          PECore.debugLog("Mod: '{}' registered Swiftwolf Rending Gale Blacklist for EntityType: '{}'", msg.getSenderModId(), entityType.getRegistryName());
        });
    WorldHelper.setSwrgBlacklist(swrg);
    Set<TileEntityType<?>> timeWatch = new HashSet<>();
    InterModComms.getMessages(PECore.MODID, IMCMethods.BLACKLIST_TIMEWATCH::equals)
        .filter(msg -> msg.getMessageSupplier().get() instanceof TileEntityType)
        .forEach(msg -> {
          TileEntityType<?> tileEntityType = (TileEntityType<?>) msg.getMessageSupplier().get();
          timeWatch.add(tileEntityType);
          PECore.debugLog("Mod: '{}' registered Time Watch Blacklist for TileEntityType: '{}'", msg.getSenderModId(), tileEntityType.getRegistryName());
        });
    TimeWatch.setInternalBlacklist(timeWatch);
    EntityRandomizerHelper.setDefaultPeacefulRandomizers(getRandomizerEntities(true));
    EntityRandomizerHelper.setDefaultHostileRandomizers(getRandomizerEntities(false));
    List<WorldTransmutationEntry> entries = new ArrayList<>();
    InterModComms.getMessages(PECore.MODID, IMCMethods.REGISTER_WORLD_TRANSMUTATION::equals)
        .filter(msg -> msg.getMessageSupplier().get() instanceof WorldTransmutationEntry)
        .forEach(msg -> {
          WorldTransmutationEntry transmutationEntry = (WorldTransmutationEntry) msg.getMessageSupplier().get();
          entries.add(transmutationEntry);
          if (transmutationEntry.getAltResult() == null) {
            PECore.debugLog("Mod: '{}' registered World Transmutation from: '{}', to: '{}'", msg.getSenderModId(),
                transmutationEntry.getOrigin(), transmutationEntry.getResult());
          }
          else {
            PECore.debugLog("Mod: '{}' registered World Transmutation from: '{}', to: '{}', with sneak output of: '{}'", msg.getSenderModId(),
                transmutationEntry.getOrigin(), transmutationEntry.getResult(), transmutationEntry.getAltResult());
          }
        });
    WorldTransmutations.setWorldTransmutation(entries);
  }

  private static List<EntityType<? extends MobEntity>> getRandomizerEntities(boolean peaceful) {
    List<EntityType<? extends MobEntity>> entities = new ArrayList<>();
    String imcMethod = peaceful ? IMCMethods.ENTITY_RANDOMIZATION_PEACEFUL : IMCMethods.ENTITY_RANDOMIZATION_HOSTILE;
    String type = peaceful ? "peaceful" : "hostile";
    InterModComms.getMessages(PECore.MODID, imcMethod::equals)
        .filter(msg -> msg.getMessageSupplier().get() instanceof EntityType<?>)
        .forEach(msg -> {
          EntityType<?> genericEntityType = (EntityType<?>) msg.getMessageSupplier().get();
          EntityType<? extends MobEntity> entityType = EntityRandomizerHelper.getEntityIfMob(genericEntityType);
          if (entityType == null) {
            PECore.LOGGER.warn("Mod: '{}' tried to register a {} entity randomizer for the entity: '{}', but that entity is not a MobEntity.",
                msg.getSenderModId(), type, genericEntityType.getRegistryName());
          }
          else {
            entities.add(entityType);
            PECore.debugLog("Mod: '{}' registered a {} entity randomizer for the entity: '{}'", msg.getSenderModId(), type, entityType.getRegistryName());
          }
        });
    return entities;
  }
}