package moze_intel.projecte.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.EnumMatterType;
import moze_intel.projecte.gameObjs.registries.PESoundEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RotatedPillarBlock;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ITag;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceContext.FluidMode;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.common.util.Constants.BlockFlags;

public class ToolHelper {

  public static final ToolType TOOL_TYPE_SHEARS = ToolType.get("shears");
  public static final ToolType TOOL_TYPE_HAMMER = ToolType.get("hammer");
  public static final ToolType TOOL_TYPE_KATAR = ToolType.get("katar");
  public static final ToolType TOOL_TYPE_MORNING_STAR = ToolType.get("morning_star");
  private static final UUID CHARGE_MODIFIER = UUID.fromString("69ADE509-46FF-3725-92AC-F59FB052BEC7");
  //Note: These all also do the check that super did before of making sure the entity is not spectating
  private static final Predicate<Entity> SHEARABLE = entity -> !entity.isSpectator() && entity instanceof IForgeShearable;
  private static final Predicate<Entity> SLAY_MOB = entity -> !entity.isSpectator() && entity instanceof IMob;
  private static final Predicate<Entity> SLAY_ALL = entity -> !entity.isSpectator() && (entity instanceof IMob || entity instanceof LivingEntity);

  public static Multimap<Attribute, AttributeModifier> addChargeAttributeModifier(Multimap<Attribute, AttributeModifier> currentModifiers, @Nonnull EquipmentSlotType slot, ItemStack stack) {
    if (slot == EquipmentSlotType.MAINHAND) {
      int charge = getCharge(stack);
      if (charge > 0) {
        //TODO - 1.16: Do this in a better way that allows for some caching?
        Builder<Attribute, AttributeModifier> attributesBuilder = ImmutableMultimap.builder();
        attributesBuilder.putAll(currentModifiers);
        //If we have any charge take it into account for calculating the damage
        attributesBuilder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(CHARGE_MODIFIER, "Charge modifier", charge, Operation.ADDITION));
        return attributesBuilder.build();
      }
    }
    return currentModifiers;
  }

  /**
   * Performs a set of actions, until we find a success or run out of actions.
   *
   * @implNote Only returns that we failed if all the tested actions failed.
   */
  @SafeVarargs
  public static ActionResultType performActions(ActionResultType firstAction, Supplier<ActionResultType>... secondaryActions) {
    if (firstAction == ActionResultType.SUCCESS) {
      return ActionResultType.SUCCESS;
    }
    ActionResultType result = firstAction;
    boolean hasFailed = result == ActionResultType.FAIL;
    for (Supplier<ActionResultType> secondaryAction : secondaryActions) {
      result = secondaryAction.get();
      if (result == ActionResultType.SUCCESS) {
        //If we were successful
        return ActionResultType.SUCCESS;
      }
      hasFailed &= result == ActionResultType.FAIL;
    }
    if (hasFailed) {
      //If at least one step failed, consider ourselves unsuccessful
      return ActionResultType.FAIL;
    }
    return ActionResultType.PASS;
  }

  /**
   * Clears the given tag in an AOE. Charge affects the AOE. Optional per-block EMC cost.
   */
  public static ActionResultType clearTagAOE(World world, PlayerEntity player, Hand hand, long emcCost, ITag<Block> tag) {
    if (ProjectEConfig.server.items.disableAllRadiusMining.get()) {
      return ActionResultType.PASS;
    }
    ItemStack stack = player.getHeldItem(hand);
    int charge = getCharge(stack);
    if (charge == 0) {
      return ActionResultType.PASS;
    }
    int scaled1 = 5 * charge;
    int scaled2 = 10 * charge;
    BlockPos corner1 = player.getPosition().add(-scaled1, -scaled2, -scaled1);
    BlockPos corner2 = player.getPosition().add(scaled1, scaled2, scaled1);
    boolean hasAction = false;
    List<ItemStack> drops = new ArrayList<>();
    for (BlockPos pos : WorldHelper.getPositionsFromBox(corner1, corner2)) {
      BlockState state = world.getBlockState(pos);
      if (state.isIn(tag)) {
        if (world.isRemote) {
          return ActionResultType.SUCCESS;
        }
        //Ensure we are immutable so that changing blocks doesn't act weird
        pos = pos.toImmutable();
        if (PlayerHelper.hasBreakPermission((ServerPlayerEntity) player, pos)) {
          //          if (ItemPE.consumeFuel(player, stack, emcCost, true)) {
          drops.addAll(Block.getDrops(state, (ServerWorld) world, pos, world.getTileEntity(pos), player, stack));
          world.removeBlock(pos, false);
          hasAction = true;
          if (world.rand.nextInt(5) == 0) {
            ((ServerWorld) world).spawnParticle(ParticleTypes.LARGE_SMOKE, pos.getX(), pos.getY(), pos.getZ(), 2, 0, 0, 0, 0);
          }
          //          }
          //          else {
          //            //If we failed to consume EMC but needed EMC just break out early as we won't have the required EMC for any of the future blocks
          //            break;
          //          }
        }
      }
    }
    if (hasAction) {
      WorldHelper.createLootDrop(drops, world, player.getPosX(), player.getPosY(), player.getPosZ());
      PlayerHelper.swingItem(player, hand);
      return ActionResultType.SUCCESS;
    }
    return ActionResultType.PASS;
  }

  /**
   * Tills in an AOE using a hoe. Charge affects the AOE. Optional per-block EMC cost.
   */
  public static ActionResultType tillHoeAOE(ItemUseContext context, long emcCost) {
    return tillAOE(context, emcCost, ToolType.HOE, SoundEvents.ITEM_HOE_TILL);
  }

  /**
   * Tills in an AOE using a shovel (ex: grass to grass path). Charge affects the AOE. Optional per-block EMC cost.
   */
  public static ActionResultType tillShovelAOE(ItemUseContext context, long emcCost) {
    return tillAOE(context, emcCost, ToolType.SHOVEL, SoundEvents.ITEM_SHOVEL_FLATTEN);
  }

  /**
   * Tills in an AOE using the specified lookup map and tester. Charge affects the AOE. Optional per-block EMC cost.
   */
  private static ActionResultType tillAOE(ItemUseContext context, long emcCost, ToolType toolType, SoundEvent sound) {
    PlayerEntity player = context.getPlayer();
    if (player == null) {
      return ActionResultType.PASS;
    }
    Direction sideHit = context.getFace();
    if (sideHit == Direction.DOWN) {
      //Don't allow tilling a block from underneath
      return ActionResultType.PASS;
    }
    Hand hand = context.getHand();
    ItemStack stack = player.getHeldItem(hand);
    World world = context.getWorld();
    BlockPos pos = context.getPos();
    BlockState tilledState = world.getBlockState(pos).getToolModifiedState(world, pos, player, stack, toolType);
    if (tilledState == null) {
      //Skip tilling the blocks if the one we clicked cannot be tilled
      return ActionResultType.PASS;
    }
    BlockPos abovePos = pos.up();
    BlockState aboveState = world.getBlockState(abovePos);
    //Check to make sure the block above is not opaque
    if (aboveState.isOpaqueCube(world, abovePos)) {
      //If the block above our source is opaque, just skip tiling in general
      return ActionResultType.PASS;
    }
    if (world.isRemote) {
      return ActionResultType.SUCCESS;
    }
    //Processing did not happen, so we need to process it
    //Note: For more detailed comments on why/how we set the block and remove the block above see the for loop below
    world.setBlockState(pos, tilledState, BlockFlags.DEFAULT_AND_RERENDER);
    Material aboveMaterial = aboveState.getMaterial();
    if ((aboveMaterial == Material.PLANTS || aboveMaterial == Material.TALL_PLANTS) && !aboveState.getBlock().hasTileEntity(aboveState)) {
      if (PlayerHelper.hasBreakPermission((ServerPlayerEntity) player, abovePos)) {
        world.destroyBlock(abovePos, true);
      }
    }
    world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
    int charge = getCharge(stack);
    if (charge > 0) {
      for (BlockPos newPos : BlockPos.getAllInBoxMutable(pos.add(-charge, 0, -charge), pos.add(charge, 0, charge))) {
        if (pos.equals(newPos)) {
          //Skip the source position as it is free and we manually handled it before the loop
          continue;
        }
        BlockState stateAbove = world.getBlockState(newPos.up());
        //Check to make sure the block above is not opaque and that the result we would get from tilling the other block is
        // the same as the one we got on the initial block we interacted with
        if (!stateAbove.isOpaqueCube(world, newPos.up()) && tilledState == world.getBlockState(newPos).getToolModifiedState(world, newPos, player, stack, toolType)) {
          //          if (ItemPE.consumeFuel(player, stack, emcCost, true)) {
          //Some of the below methods don't behave properly when the BlockPos is mutable, so now that we are onto ones where it may actually
          // matter we make sure to get an immutable instance of newPos
          newPos = newPos.toImmutable();
          //Replace the block. Note it just directly sets it (in the same way that HoeItem/ShovelItem do), rather than using our
          // checkedReplaceBlock so as to make the blocks not "blink" when getting changed. We don't bother using
          // checkedReplaceBlock as we already fired all the events/checks for seeing if we are allowed to use this item in this
          // location and were told that we are allowed to use our item.
          world.setBlockState(newPos, tilledState, BlockFlags.DEFAULT_AND_RERENDER);
          aboveMaterial = stateAbove.getMaterial();
          if ((aboveMaterial == Material.PLANTS || aboveMaterial == Material.TALL_PLANTS) && !stateAbove.getBlock().hasTileEntity(stateAbove)) {
            //If the block above the one we tilled is a plant (and not a tile entity because you never know), then we try to remove it
            //Note: We do check for breaking the block above that we attempt to break it though, as we
            // have not done any events that have told use we are allowed to break blocks in that spot.
            if (PlayerHelper.hasBreakPermission((ServerPlayerEntity) player, newPos.up())) {
              world.destroyBlock(newPos.up(), true);
            }
          }
          //          }
          //          else {
          //            //If we failed to consume EMC but needed EMC just break out early as we won't have the required EMC for any of the future blocks
          //            break;
          //          }
        }
      }
    }
    world.playSound(null, player.getPosX(), player.getPosY(), player.getPosZ(), PESoundEvents.CHARGE.get(), SoundCategory.PLAYERS, 1.0F, 1.0F);
    return ActionResultType.SUCCESS;
  }

  /**
   * Strips logs in an AOE using a shovel (ex: grass to grass path). Charge affects the AOE. Optional per-block EMC cost.
   */
  public static ActionResultType stripLogsAOE(ItemUseContext context, long emcCost) {
    PlayerEntity player = context.getPlayer();
    if (player == null) {
      return ActionResultType.PASS;
    }
    Hand hand = context.getHand();
    ItemStack stack = player.getHeldItem(hand);
    World world = context.getWorld();
    BlockPos pos = context.getPos();
    BlockState clickedState = world.getBlockState(pos);
    BlockState strippedState = clickedState.getToolModifiedState(world, pos, player, stack, ToolType.AXE);
    if (strippedState == null) {
      //Skip stripping the blocks if the one we clicked cannot be stripped
      return ActionResultType.PASS;
    }
    else if (world.isRemote) {
      return ActionResultType.SUCCESS;
    }
    Axis axis = clickedState.get(RotatedPillarBlock.AXIS);
    //Process the block we interacted with initially and play the sound
    //Note: For more detailed comments on why/how we set the block and remove the block above see the for loop below
    world.setBlockState(pos, strippedState, BlockFlags.DEFAULT_AND_RERENDER);
    world.playSound(null, pos, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0F, 1.0F);
    int charge = getCharge(stack);
    if (charge > 0) {
      Direction side = context.getFace();
      for (BlockPos newPos : WorldHelper.getPositionsFromBox(WorldHelper.getBroadBox(pos, side, charge))) {
        if (pos.equals(newPos)) {
          //Skip the source position as it is free and we manually handled it before the loop
          continue;
        }
        //Check to make that the result we would get from stripping the other block is the same as the one we got on the initial block we interacted with
        // Also make sure that it is on the same axis as the block we initially clicked
        BlockState state = world.getBlockState(newPos);
        if (strippedState == state.getToolModifiedState(world, newPos, player, stack, ToolType.AXE) && axis == state.get(RotatedPillarBlock.AXIS)) {
          //          if (ItemPE.consumeFuel(player, stack, emcCost, true)) {
          //Some of the below methods don't behave properly when the BlockPos is mutable, so now that we are onto ones where it may actually
          // matter we make sure to get an immutable instance of newPos
          newPos = newPos.toImmutable();
          //Replace the block. Note it just directly sets it (in the same way that AxeItem does), rather than using our
          // checkedReplaceBlock so as to make the blocks not "blink" when getting changed. We don't bother using
          // checkedReplaceBlock as we already fired all the events/checks for seeing if we are allowed to use this item in this
          // location and were told that we are allowed to use our item.
          world.setBlockState(newPos, strippedState, BlockFlags.DEFAULT_AND_RERENDER);
          //          }
          //          else {
          //            //If we failed to consume EMC but needed EMC just break out early as we won't have the required EMC for any of the future blocks
          //            break;
          //          }
        }
      }
    }
    world.playSound(null, player.getPosX(), player.getPosY(), player.getPosZ(), PESoundEvents.CHARGE.get(), SoundCategory.PLAYERS, 1.0F, 1.0F);
    return ActionResultType.SUCCESS;
  }

  /**
   * Called by multiple tools' left click function. Charge has no effect. Free operation.
   */
  public static void digBasedOnMode(ItemStack stack, World world, BlockPos pos, LivingEntity living, RayTracePointer tracePointer) {
    if (world.isRemote || ProjectEConfig.server.items.disableAllRadiusMining.get() || !(living instanceof PlayerEntity)) {
      return;
    }
    byte mode = getMode(stack);
    if (mode == 0) {
      //Standard
      return;
    }
    PlayerEntity player = (PlayerEntity) living;
    RayTraceResult mop = tracePointer.rayTrace(world, player, RayTraceContext.FluidMode.NONE);
    if (!(mop instanceof BlockRayTraceResult)) {
      return;
    }
    BlockRayTraceResult rayTraceResult = (BlockRayTraceResult) mop;
    if (rayTraceResult.getType() == Type.MISS || !pos.equals(rayTraceResult.getPos())) {
      //Ensure that the ray trace agrees with the position we were told about
      return;
    }
    Direction sideHit = rayTraceResult.getFace();
    AxisAlignedBB box = new AxisAlignedBB(pos, pos);
    switch (mode) {
      case 1: //3x Tallshot
        box = new AxisAlignedBB(pos.down(), pos.up());
      break;
      case 2: //3x Wideshot
        switch (sideHit.getAxis()) {
          case X:
            box = new AxisAlignedBB(pos.south(), pos.north());
          break;
          case Y:
            switch (player.getHorizontalFacing().getAxis()) {
              case X:
                box = new AxisAlignedBB(pos.south(), pos.north());
              break;
              case Z:
                box = new AxisAlignedBB(pos.west(), pos.east());
              break;
            }
          break;
          case Z:
            box = new AxisAlignedBB(pos.west(), pos.east());
          break;
        }
      break;
      case 3: //3x Longshot
        box = new AxisAlignedBB(pos, pos.offset(sideHit.getOpposite(), 2));
      break;
    }
    List<ItemStack> drops = new ArrayList<>();
    for (BlockPos digPos : WorldHelper.getPositionsFromBox(box)) {
      if (world.isAirBlock(digPos)) {
        continue;
      }
      BlockState state = world.getBlockState(digPos);
      if (state.getBlockHardness(world, digPos) != -1 && stack.canHarvestBlock(state)) {
        //Ensure we are immutable so that changing blocks doesn't act weird
        digPos = digPos.toImmutable();
        if (PlayerHelper.hasBreakPermission((ServerPlayerEntity) player, digPos)) {
          drops.addAll(Block.getDrops(state, (ServerWorld) world, digPos, world.getTileEntity(digPos), player, stack));
          world.removeBlock(digPos, false);
        }
      }
    }
    WorldHelper.createLootDrop(drops, world, pos);
  }

  /**
   * Carves in an AOE. Charge affects the breadth and/or depth of the AOE. Optional per-block EMC cost.
   */
  public static ActionResultType digAOE(World world, PlayerEntity player, Hand hand, BlockPos pos, Direction sideHit, boolean affectDepth, long emcCost) {
    if (ProjectEConfig.server.items.disableAllRadiusMining.get()) {
      return ActionResultType.PASS;
    }
    ItemStack stack = player.getHeldItem(hand);
    int charge = getCharge(stack);
    if (charge == 0) {
      return ActionResultType.PASS;
    }
    AxisAlignedBB box = affectDepth ? WorldHelper.getBroadDeepBox(pos, sideHit, charge) : WorldHelper.getFlatYBox(pos, charge);
    boolean hasAction = false;
    List<ItemStack> drops = new ArrayList<>();
    for (BlockPos newPos : WorldHelper.getPositionsFromBox(box)) {
      if (world.isAirBlock(newPos)) {
        continue;
      }
      BlockState state = world.getBlockState(newPos);
      if (state.getBlockHardness(world, newPos) != -1 && stack.canHarvestBlock(state)) {
        if (world.isRemote) {
          return ActionResultType.SUCCESS;
        }
        //Ensure we are immutable so that changing blocks doesn't act weird
        newPos = newPos.toImmutable();
        if (PlayerHelper.hasBreakPermission((ServerPlayerEntity) player, newPos)) {
          //          if (ItemPE.consumeFuel(player, stack, emcCost, true)) {
          drops.addAll(Block.getDrops(state, (ServerWorld) world, newPos, world.getTileEntity(newPos), player, stack));
          world.removeBlock(newPos, false);
          hasAction = true;
          //          }
          //          else {
          //            //If we failed to consume EMC but needed EMC just break out early as we won't have the required EMC for any of the future blocks
          //            break;
          //          }
        }
      }
    }
    if (hasAction) {
      WorldHelper.createLootDrop(drops, world, pos);
      PlayerHelper.swingItem(player, hand);
      player.getEntityWorld().playSound(null, player.getPosX(), player.getPosY(), player.getPosZ(), PESoundEvents.DESTRUCT.get(), SoundCategory.PLAYERS, 1.0F, 1.0F);
      return ActionResultType.SUCCESS;
    }
    return ActionResultType.PASS;
  }

  /**
   * Attacks through armor. Charge affects damage. Free operation.
   */
  public static void attackWithCharge(ItemStack stack, LivingEntity damaged, LivingEntity damager, float baseDmg) {
    if (!(damager instanceof PlayerEntity) || damager.getEntityWorld().isRemote) {
      return;
    }
    DamageSource dmg = DamageSource.causePlayerDamage((PlayerEntity) damager);
    int charge = getCharge(stack);
    float totalDmg = baseDmg;
    if (charge > 0) {
      dmg.setDamageBypassesArmor();
      totalDmg += charge;
    }
    damaged.attackEntityFrom(dmg, totalDmg);
  }

  /**
   * Attacks in an AOE. Charge affects AOE, not damage (intentional). Optional per-entity EMC cost.
   */
  public static void attackAOE(ItemStack stack, PlayerEntity player, boolean slayAll, float damage, long emcCost, Hand hand) {
    World world = player.getEntityWorld();
    if (world.isRemote) {
      return;
    }
    int charge = getCharge(stack);
    List<Entity> toAttack = world.getEntitiesInAABBexcluding(player, player.getBoundingBox().grow(2.5F * charge), slayAll ? SLAY_ALL : SLAY_MOB);
    DamageSource src = DamageSource.causePlayerDamage(player).setDamageBypassesArmor();
    boolean hasAction = false;
    for (Entity entity : toAttack) {
      //      if (ItemPE.consumeFuel(player, stack, emcCost, true)) {
      entity.attackEntityFrom(src, damage);
      hasAction = true;
      //      }
      //      else {
      //        //If we failed to consume EMC but needed EMC just break out early as we won't have the required EMC for any of the future blocks
      //        break;
      //      }
    }
    if (hasAction) {
      world.playSound(null, player.getPosX(), player.getPosY(), player.getPosZ(), PESoundEvents.CHARGE.get(), SoundCategory.PLAYERS, 1.0F, 1.0F);
      PlayerHelper.swingItem(player, hand);
    }
  }

  /**
   * Called when tools that act as shears start breaking a block. Free operation.
   */
  public static ActionResultType shearBlock(ItemStack stack, BlockPos pos, PlayerEntity player) {
    World world = player.getEntityWorld();
    Block block = world.getBlockState(pos).getBlock();
    if (block instanceof IForgeShearable) {
      IForgeShearable target = (IForgeShearable) block;
      if (target.isShearable(stack, world, pos) && (world.isRemote || PlayerHelper.hasBreakPermission((ServerPlayerEntity) player, pos))) {
        List<ItemStack> drops = target.onSheared(player, stack, world, pos, EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, stack));
        if (!drops.isEmpty()) {
          if (!world.isRemote) {
            WorldHelper.createLootDrop(drops, world, pos);
            player.addStat(Stats.BLOCK_MINED.get(block), 1);
          }
          //NOTE: We only mark it as a success if we actually got drops otherwise we let it continue breaking the block
          return ActionResultType.SUCCESS;
        }
      }
    }
    return ActionResultType.PASS;
  }

  /**
   * Shears entities in an AOE. Charge affects AOE. Optional per-entity EMC cost.
   */
  public static ActionResultType shearEntityAOE(PlayerEntity player, Hand hand, long emcCost) {
    World world = player.getEntityWorld();
    ItemStack stack = player.getHeldItem(hand);
    int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, stack);
    int offset = (int) Math.pow(2, 2 + getCharge(stack));
    //Get all entities also making sure that they are shearable
    List<Entity> list = world.getEntitiesWithinAABB(Entity.class, player.getBoundingBox().grow(offset, offset / 2.0, offset), SHEARABLE);
    boolean hasAction = false;
    List<ItemStack> drops = new ArrayList<>();
    for (Entity ent : list) {
      BlockPos entityPosition = ent.getPosition();
      IForgeShearable target = (IForgeShearable) ent;
      if (target.isShearable(stack, world, entityPosition)) {
        if (world.isRemote) {
          return ActionResultType.SUCCESS;
        }
        //        if (ItemPE.consumeFuel(player, stack, emcCost, true)) {
        List<ItemStack> entDrops = target.onSheared(player, stack, world, entityPosition, fortune);
        if (!entDrops.isEmpty()) {
          //Double all drops (just add them all twice because we compact the list later anyways)
          //Note: The reason we don't grow the stacks like we used to is to ensure if a modded mob drops
          // items with over half their max stack size, we don't end up potentially messing up the logic
          // in the stack/trying to spawn in overly full stacks
          drops.addAll(entDrops);
          drops.addAll(entDrops);
        }
        hasAction = true;
        //        }
        //        else {
        //          //If we failed to consume EMC but needed EMC just break out early as we won't have the required EMC for any of the future blocks
        //          break;
        //        }
      }
      if (!world.isRemote && Math.random() < 0.01) {
        Entity e = ent.getType().create(world);
        if (e != null) {
          e.setPosition(ent.getPosX(), ent.getPosY(), ent.getPosZ());
          if (e instanceof MobEntity) {
            ((MobEntity) e).onInitialSpawn((ServerWorld) world, world.getDifficultyForLocation(entityPosition), SpawnReason.EVENT, null, null);
          }
          if (e instanceof SheepEntity) {
            ((SheepEntity) e).setFleeceColor(DyeColor.byId(MathUtils.randomIntInRange(0, 15)));
          }
          if (e instanceof AgeableEntity) {
            ((AgeableEntity) e).setGrowingAge(-24000);
          }
          world.addEntity(e);
        }
      }
    }
    if (hasAction) {
      WorldHelper.createLootDrop(drops, world, player.getPosX(), player.getPosY(), player.getPosZ());
      PlayerHelper.swingItem(player, hand);
      return ActionResultType.SUCCESS;
    }
    return ActionResultType.PASS;
  }

  /**
   * Scans and harvests an ore vein.
   */
  public static ActionResultType tryVeinMine(Hand hand, PlayerEntity player, BlockPos pos, Direction sideHit) {
    if (ProjectEConfig.server.items.disableAllRadiusMining.get()) {
      return ActionResultType.PASS;
    }
    World world = player.getEntityWorld();
    ItemStack stack = player.getHeldItem(hand);
    BlockState target = world.getBlockState(pos);
    if (target.getBlockHardness(world, pos) <= -1 || !stack.canHarvestBlock(target)) {
      return ActionResultType.FAIL;
    }
    boolean hasAction = false;
    List<ItemStack> drops = new ArrayList<>();
    for (BlockPos newPos : WorldHelper.getPositionsFromBox(WorldHelper.getBroadDeepBox(pos, sideHit, getCharge(stack)))) {
      if (!world.isAirBlock(newPos)) {
        BlockState state = world.getBlockState(newPos);
        if (target.getBlock() == state.getBlock()) {
          if (world.isRemote) {
            return ActionResultType.SUCCESS;
          }
          //Ensure we are immutable so that changing blocks doesn't act weird
          if (WorldHelper.harvestVein(world, player, stack, newPos.toImmutable(), state.getBlock(), drops, 0) > 0) {
            hasAction = true;
          }
        }
      }
    }
    if (hasAction) {
      WorldHelper.createLootDrop(drops, world, pos);
      world.playSound(null, player.getPosX(), player.getPosY(), player.getPosZ(), PESoundEvents.DESTRUCT.get(), SoundCategory.PLAYERS, 1.0F, 1.0F);
      return ActionResultType.SUCCESS;
    }
    return ActionResultType.PASS;
  }

  /**
   * Mines all ore veins in a Box around the player.
   */
  public static ActionResultType mineOreVeinsInAOE(PlayerEntity player, Hand hand) {
    if (ProjectEConfig.server.items.disableAllRadiusMining.get()) {
      return ActionResultType.PASS;
    }
    World world = player.getEntityWorld();
    ItemStack stack = player.getHeldItem(hand);
    boolean hasAction = false;
    List<ItemStack> drops = new ArrayList<>();
    for (BlockPos pos : WorldHelper.getPositionsFromBox(player.getBoundingBox().grow(getCharge(stack) + 3))) {
      if (world.isAirBlock(pos)) {
        continue;
      }
      BlockState state = world.getBlockState(pos);
      if (ItemHelper.isOre(state) && state.getBlockHardness(world, pos) != -1 && stack.canHarvestBlock(state)) {
        if (world.isRemote) {
          return ActionResultType.SUCCESS;
        }
        //Ensure we are immutable so that changing blocks doesn't act weird
        if (WorldHelper.harvestVein(world, player, stack, pos.toImmutable(), state.getBlock(), drops, 0) > 0) {
          hasAction = true;
        }
      }
    }
    if (hasAction) {
      WorldHelper.createLootDrop(drops, world, player.getPosX(), player.getPosY(), player.getPosZ());
      PlayerHelper.swingItem(player, hand);
      return ActionResultType.SUCCESS;
    }
    return ActionResultType.PASS;
  }

  public static float getDestroySpeed(float parentDestroySpeed, EnumMatterType matterType, int charge) {
    if (parentDestroySpeed == 1) {
      //If we cannot harvest the block leave the value be
      return parentDestroySpeed;
    }
    return parentDestroySpeed + matterType.getChargeModifier() * charge;
  }

  private static int getCharge(ItemStack stack) {
    return stack.getCapability(ProjectEAPI.CHARGE_ITEM_CAPABILITY).map(itemCharge -> itemCharge.getCharge(stack)).orElse(0);
  }

  private static byte getMode(ItemStack stack) {
    return stack.getCapability(ProjectEAPI.MODE_CHANGER_ITEM_CAPABILITY).map(itemMode -> itemMode.getMode(stack)).orElse((byte) 0);
  }

  @FunctionalInterface
  public interface RayTracePointer {

    RayTraceResult rayTrace(World world, PlayerEntity player, FluidMode fluidMode);
  }
}