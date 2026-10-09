package xyz.nifeather.morph.client.syncers;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.LoggerFactory;
import xyz.nifeather.morph.client.FeatherMorphClientBootstrap;
import xyz.nifeather.morph.client.MorphClientObject;
import xyz.nifeather.morph.client.entities.*;
import xyz.nifeather.morph.client.mixin.accessors.AbstractHorseEntityMixin;
import xyz.nifeather.morph.client.mixin.accessors.EntityAccessor;
import xyz.nifeather.morph.client.mixin.accessors.LimbAnimatorAccessor;
import xyz.nifeather.morph.client.mixin.accessors.LivingEntityAccessor;
import xyz.nifeather.morph.client.properties.*;
import xyz.nifeather.morph.client.syncers.animations.AnimationHandler;
import xyz.nifeather.morph.client.utilties.ClientItemUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DisguiseSyncer extends MorphClientObject
{
    @NotNull
    protected final Entity disguiseInstance;

    @NotNull
    public Entity getDisguiseInstance()
    {
        return disguiseInstance;
    }

    @NotNull
    protected AbstractClientPlayer bindingPlayer;

    protected final String disguiseId;
    public String disguiseIdentifier()
    {
        return disguiseId;
    }

    protected final int bindingNetworkId;

    protected final ClientPropertyHolder propertyHolder = new ClientPropertyHolder();

    public ClientPropertyHolder propertyHolder()
    {
        return propertyHolder;
    }

    @NotNull
    public AbstractClientPlayer getBindingPlayer()
    {
        return bindingPlayer;
    }

    public DisguiseSyncer(@NotNull AbstractClientPlayer bindingPlayer,
                          String morphId, int networkId,
                          @NonNull Entity disguiseEntity)
    {
        this.bindingPlayer = bindingPlayer;
        this.disguiseId = morphId;
        this.bindingNetworkId = networkId;
        this.disguiseInstance = disguiseEntity;

        propertyHolder.hookOnPropertyWrite(this::onPropertyWrite);
        propertyHolder.hookOnPropertyDiscard(this::onPropertyDiscard);
        propertyHolder.hookOnTemporaryPropertyDiscard(this::onTemporaryPropertyDiscard);

        if (disguiseEntity instanceof IMorphClientEntity iMorphClientEntity)
            iMorphClientEntity.featherMorph$setIsDisguiseEntity(networkId, this);

        initialize();
    }

    private <X, E> void onTemporaryPropertyDiscard(ClientProperty<X, E> clientProperty)
    {
        var existing = propertyHolder.getOr(clientProperty, null);
        if (existing != null)
        {
            clientProperty.apply((E) disguiseInstance, existing);
            return;
        }

        if (clientProperty.identifier().equals(PropertyNames.LIVING_ENTITY_BED_POS)
                && disguiseInstance instanceof LivingEntity living)
            living.clearSleepingPos();

        if (clientProperty.restoreDefaultsBeforeDiscard())
        {
            clientProperty.tryCastEntity(disguiseInstance)
                    .ifPresent(e -> clientProperty.entityHandle().handle(e, clientProperty.defaultValue()));
        }
    }

    private <X, E> void onPropertyDiscard(ClientProperty<X, E> clientProperty)
    {
        if (clientProperty.identifier().equals(PropertyNames.LIVING_ENTITY_BED_POS)
                && disguiseInstance instanceof LivingEntity living)
            living.clearSleepingPos();

        if (!clientProperty.restoreDefaultsBeforeDiscard())
            return;

        clientProperty.tryCastEntity(disguiseInstance)
                .ifPresent(e -> clientProperty.entityHandle().handle(e, clientProperty.defaultValue()));
    }

    private void onPropertyWrite(ClientProperty<?, ?> property, @Nullable Object oldValue, @NotNull Object newValue)
    {
        if (property.identifier().equals(PropertyNames.ENTITY_EQUIPMENT))
        {
            mergeEquipment(oldValue == null ? DisguiseEquipment.empty() : (DisguiseEquipment) oldValue, (DisguiseEquipment) newValue);
        }
        else if (property.identifier().equals(PropertyNames.ENTITY_DISPLAY_DISGUISE_EQUIPMENT))
        {
            var equipmentProperty = (ClientProperty<DisguiseEquipment, LivingEntity>) propertyHolder().getProperty(PropertyNames.ENTITY_EQUIPMENT);
            var display = (Boolean) newValue;
            updateDisplayingEquipment(display, propertyHolder().get(equipmentProperty).contents(false));
        }
    }

    private void mergeEquipment(@NotNull DisguiseEquipment existing, DisguiseEquipment newEquipment)
    {
        var equipmentProperty = (ClientProperty<DisguiseEquipment, LivingEntity>) propertyHolder().getProperty(PropertyNames.ENTITY_EQUIPMENT);
        if (equipmentProperty == null) return;

        var builder = DisguiseEquipment.builder();
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            var upcoming = newEquipment.getItemOrNull(slot);
            builder.forSlot(slot, Objects.requireNonNullElseGet(upcoming, () -> existing.getItem(slot)));
        }

        var finalEquipment = builder.build();
        propertyHolder().set(equipmentProperty, finalEquipment);

        var displayDisguiseEquipProperty = propertyHolder().getProperty(PropertyNames.ENTITY_DISPLAY_DISGUISE_EQUIPMENT);
        var displayDisguiseEquip = (Boolean) propertyHolder().get(displayDisguiseEquipProperty);
        updateDisplayingEquipment(displayDisguiseEquip, finalEquipment.contents(false));
    }

    private void updateDisplayingEquipment(boolean displayDisguiseEquip, Map<EquipmentSlot, ItemStack> disguiseEquipment)
    {
        if (!(disguiseInstance instanceof LivingEntity livingEntity)) return;

        if (displayDisguiseEquip)
        {
            disguiseEquipment.forEach(livingEntity::setItemSlot);
        }
        else
        {
            var slots = EnumSet.allOf(EquipmentSlot.class);
            for (EquipmentSlot slot : slots)
            {
                if (!livingEntity.canUseSlot(slot) || !bindingPlayer.canUseSlot(slot))
                    continue;

                livingEntity.setItemSlot(slot, bindingPlayer.getItemBySlot(slot));
            }
        }
    }

    private AnimationHandler animHandler;

    public void setAnimationHandler(AnimationHandler handler)
    {
        this.animHandler = handler;
    }

    public void playAnimation(String animation)
    {
        if (animHandler == null)
        {
            logger.warn("No animation handler for disguise '%s'!".formatted(disguiseId));
            return;
        }

        animHandler.play(disguiseInstance, animation);
    }

    private int crystalId;

    protected Entity beamTarget;

    private void scheduleCrystalSearch()
    {
        if (beamTarget != null || crystalId == 0) return;

        this.addSchedule(this::scheduleCrystalSearch, 10);

        this.beamTarget = findCrystalBy(crystalId);
    }

    @Nullable
    private Entity findCrystalBy(int id)
    {
        if (id == 0) return null;

        return bindingPlayer.level().getEntity(id);
    }

    public void playAttackAnimation()
    {
        disguiseInstance.handleEntityEvent(EntityEvent.START_ATTACKING);
    }

    private static final ItemStack air = new ItemStack(Items.AIR);

    protected void mergeNbt(CompoundTag nbtCompound)
    {
        var entity = disguiseInstance;

        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), FeatherMorphClientBootstrap.LOGGER))
        {
            entity.load(TagValueInput.create(scopedCollector, entity.registryAccess(), nbtCompound));
        }

        if (entity instanceof Horse horse)
        {
            var haveSaddle = nbtCompound.contains("SaddleItem");

            if (haveSaddle)
            {
                ItemStack itemStack = ClientItemUtils.fromCompound(bindingPlayer.level().registryAccess(), nbtCompound.getCompound("SaddleItem").orElseThrow())
                        .orElse(air);

                var isSaddle = itemStack.is(Items.SADDLE);

                ((AbstractHorseEntityMixin) horse).callSetFlag(4, isSaddle);
            }

            //Doesn't work for unknown reason
            if (nbtCompound.contains("ArmorItem"))
            {
                ItemStack armorItem = ClientItemUtils.fromCompound(bindingPlayer.level().registryAccess(), nbtCompound.getCompound("ArmorItem").orElseThrow())
                        .orElse(air);

                horse.setItemSlot(EquipmentSlot.BODY, armorItem);
            }
        }

        bindingPlayer.refreshDimensions();

        var crystalPosition = nbtCompound.getInt("BeamTarget").orElse(-1);
        crystalId = crystalPosition;
        this.beamTarget = findCrystalBy(crystalPosition);

        if (beamTarget == null)
            this.scheduleCrystalSearch();
    }

    //region DisguiseSyncing

    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    protected void markSyncing()
    {
        isSyncing.set(true);
    }

    protected void markNotSyncing()
    {
        isSyncing.set(false);
    }

    public boolean isSyncing()
    {
        return isSyncing.get();
    }

    private boolean allowTick = true;

    protected void onTickError()
    {
    }

    private void onSyncError(Exception e)
    {
        allowTick = false;
        markNotSyncing();

        logger.error(e.getMessage());
        e.printStackTrace();

        try
        {
            disposeEntity(disguiseInstance);
        }
        catch (Exception ee)
        {
            LoggerFactory.getLogger("MorphClient").error("Unable to remove entity:" + ee.getMessage());
            ee.printStackTrace();
        }

        onTickError();
    }

    public void preEntityTick()
    {
        if (!allowTick || disposed()) return;

        var profiler = Profiler.get();
        try
        {
            profiler.push("feathermorph:pre_entity");
            onPreEntityTick();
        }
        catch (Exception ee)
        {
            onSyncError(ee);
        }
        finally
        {
            profiler.pop();
        }
    }

    public void postEntityTick()
    {
        if (!allowTick || disposed()) return;

        var profiler = Profiler.get();
        try
        {
            profiler.push("feathermorph:post_entity");
            onPostEntityTick();
        }
        catch (Exception ee)
        {
            onSyncError(ee);
        }
        finally
        {
            profiler.pop();
        }
    }

    /**
     * Called when the disguise entity finished tick.
     */
    protected abstract void onPostEntityTick();

    /**
     * Called when the disguise entity is about to tick.
     */
    protected abstract void onPreEntityTick();

    protected abstract void initialSync();

    private Vec3 cachedEntityOldPosition;
    private Vec3 cachedEntityPosition;

    public void preRenderStateSetup(float partialTicks)
    {
        var entityPos = disguiseInstance.position();
        cachedEntityPosition = new Vec3(entityPos.x, entityPos.y, entityPos.z);
        cachedEntityOldPosition = disguiseInstance.oldPosition();

        disguiseInstance.setPos(bindingPlayer.position());
        disguiseInstance.setOldPosAndRot(bindingPlayer.oldPosition(), disguiseInstance.yRotO, disguiseInstance.xRotO);
    }

    public void modifyRenderState(EntityRenderState renderState, float partialTicks)
    {
        if (!allowTick) return;

        if (disguiseInstance.getType() != EntityTypes.PLAYER && disguiseInstance.hasCustomName())
        {
            renderState.nameTag = disguiseInstance.getName();
            renderState.nameTagAttachment = disguiseInstance.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, disguiseInstance.getYRot(1));
        }

        if (renderState instanceof GuardianRenderState guardianRenderState)
            guardianRenderState.eyePosition = new Vec3(bindingPlayer.xo, bindingPlayer.yo, bindingPlayer.zo);
    }

    public void postRenderStateSetup(float partialTicks)
    {
        if (cachedEntityPosition != null)
        {
            disguiseInstance.setPos(cachedEntityPosition);
            cachedEntityPosition = null;
        }

        if (cachedEntityOldPosition != null)
        {
            disguiseInstance.setOldPosAndRot(cachedEntityOldPosition, disguiseInstance.yRotO, disguiseInstance.xRotO);
            cachedEntityOldPosition = null;
        }
    }

    public void updateSkin(GameProfile profile)
    {
        if (disposed.get())
        {
            logger.warn("Trying to update skin for a disposed DisguiseSyncer " + this);
            Thread.dumpStack();
            return;
        }

        if (!RenderSystem.isOnRenderThread())
            throw new RuntimeException("May not invoke updateSkin() while not on the render thread.");

        if (!(disguiseInstance instanceof MorphLocalPlayer localPlayer))
        {
            FeatherMorphClientBootstrap.LOGGER.warn(this + " Received a GameProfile while current disguise is not a player! Current instance is %s".formatted(disguiseInstance));
            return;
        }

        localPlayer.updateSkin(profile);
    }

    public void onMasterEquipmentChange(EquipmentSlot slot, ItemStack itemStack)
    {
        var shouldDisplayDisguiseEquipment = propertyHolder.getOr(PropertyNames.ENTITY_DISPLAY_DISGUISE_EQUIPMENT, false);
        if (shouldDisplayDisguiseEquipment || !(disguiseInstance instanceof LivingEntity livingEntity)) return;

        if (livingEntity.canUseSlot(slot))
            livingEntity.setItemSlot(slot, itemStack);
    }

    public void updateSelectedItem(ItemStack selectedItem)
    {
        onMasterEquipmentChange(EquipmentSlot.MAINHAND, selectedItem);
    }

    public void syncEquipment()
    {
        if (!(disguiseInstance instanceof LivingEntity livingEntity))
            return;

        var shouldDisplayDisguiseEquipment = propertyHolder.getOr(PropertyNames.ENTITY_DISPLAY_DISGUISE_EQUIPMENT, false);
        if (shouldDisplayDisguiseEquipment) return;

        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            if (!livingEntity.canUseSlot(slot))
                continue;

            var stack = bindingPlayer.getItemBySlot(slot);
            livingEntity.setItemSlot(slot, stack);
        }
    }

    protected void syncRotation()
    {
        @Nullable Float xRot = null;
        @Nullable Float yRot = null;

        var player = bindingPlayer;

        if (!propertyHolder.contains(PropertyNames.ENTITY_STATIC_PITCH))
        {
            BlockPos sleepingPos = null;
            if (disguiseInstance instanceof LivingEntity livingEntity)
                sleepingPos = livingEntity.getSleepingPos().orElse(null);

            // 幻翼的pitch需要倒转
            // Don't sync pitch when sleeping position is present -- Match plugin behavior (maybe?)
            if (sleepingPos == null && !(disguiseInstance instanceof Panda panda && panda.isSitting())) // Fix: Panda lock themselves pitch to zero when sitting
            {
                xRot = (disguiseInstance.getType() == EntityTypes.PHANTOM)
                       ? -player.getXRot()
                       : player.getXRot();
            }
        }

        if (!propertyHolder.contains(PropertyNames.ENTITY_STATIC_YAW))
        {
            yRot = (disguiseInstance.getType() == EntityTypes.ENDER_DRAGON)
                   ? 180 + player.getYRot()
                   : player.getYRot();

            if (disguiseInstance instanceof LivingEntity livingEntity)
            {
                livingEntity.yHeadRot = player.yHeadRot;
                livingEntity.yHeadRotO = player.yHeadRotO;

                if (livingEntity.getType() == EntityTypes.ARMOR_STAND)
                {
                    livingEntity.yBodyRot = player.yHeadRot;
                    livingEntity.yBodyRotO = player.yHeadRotO;
                }
            }
        }

        if (yRot != null) disguiseInstance.setYRot(yRot % 360f);
        if (xRot != null) disguiseInstance.setXRot(xRot % 360f);
    }

    protected void syncPosition()
    {
        // 2026/03/28:
        // I'm spending 3 days on why the disguise's `yBodyRot` is abnormal.
        // But I still have no clue why `setPos` would not make entity's yBodyRot change.
        // One possible way to *workaround* is to use `moveOrInterpolateTo` like what ClientPacketListener does.
        // But that makes the disguise look weird when moving around.
        // So let's just set `yBodyRot` manually and hope this won't break anything...
        //
        // Also 2026/03/28:
        // Yes, calling `setYBodyRot` can break things dang it :>
        //
        // 2026/03/31:
        // I guess I figured out how to make the entity not get culled, so let's try teleporting to Y4096 again :D
        var playerPos = bindingPlayer.position();
        disguiseInstance.setPos(playerPos.x, playerPos.y + 4096, playerPos.z);
    }

    private ClientLevel world;
    private ClientLevel prevWorld;
    protected void baseSync()
    {
        var entity = disguiseInstance;

        if (this.disposed())
        {
            logger.warn("Trying to update a disposed DisguiseSyncer(%s)!".formatted(this));
            Thread.dumpStack();
            return;
        }

        if (bindingPlayer.isRemoved() || bindingPlayer.level() != Minecraft.getInstance().level)
        {
            logger.info(this + " Player removed, scheduling syncer dispose");
            this.addSchedule(this::dispose);
            return;
        }

        world = Minecraft.getInstance().level;

        if (world != prevWorld)
        {
            var prev = prevWorld;
            prevWorld = world;

            if (prev != null)
            {
                logger.info(this + " World changed, scheduling syncer dispose");
                addSchedule(this::dispose);
            }

            return;
        }

        if (disguiseInstance.isRemoved())
        {
            logger.info(this + " Instance removed, scheduling syncer dispose");
            addSchedule(this::dispose);
            return;
        }

        var profiler = Profiler.get();
        profiler.push("feathermorph:syncer_base_sync");

        markSyncing();

        profiler.push("feathermorph:rotation");
        syncRotation();
        profiler.pop();

        if (beamTarget != null && beamTarget.isRemoved())
            beamTarget = null;

        if (entity instanceof LivingEntity livingEntity)
        {
            profiler.push("feathermorph:sync_on_living_entity");
            syncOnLivingEntity(livingEntity);
            profiler.pop();
        }

        profiler.push("feathermorph:misc_attributes");

        //todo: Move this out of DisguiseSyncer
        if (entity instanceof Display.ItemDisplay itemDisplay)
        {
            itemDisplay.setItemStack(bindingPlayer.getMainHandItem());
        }

        entity.setSharedFlagOnFire(bindingPlayer.isOnFire());

        // Hand and sneaking
        if (entity.getType() != EntityTypes.MANNEQUIN)
            entity.setShiftKeyDown(bindingPlayer.isShiftKeyDown());

        entity.setSprinting(bindingPlayer.isSprinting());

        //entity.inPowderSnow = clientPlayer.inPowderSnow;
        entity.setTicksFrozen(bindingPlayer.getTicksFrozen());

        entity.setDeltaMovement(bindingPlayer.getDeltaMovement());

        entity.setOnGround(bindingPlayer.onGround());

        ((EntityAccessor) entity).setWasTouchingWater(bindingPlayer.isInWater());

        // Glowing
        if (bindingPlayer.isCurrentlyGlowing() != entity.isCurrentlyGlowing())
            entity.setGlowingTag(bindingPlayer.isCurrentlyGlowing());

        //同步Pose
        if (!propertyHolder().contains(PropertyNames.ENTITY_STATIC_POSE))
            entity.setPose(bindingPlayer.getPose());

        entity.setSwimming(bindingPlayer.isSwimming());

        if (bindingPlayer.isPassenger() && entity.getVehicle() != bindingPlayer)
        {
            if (entity instanceof IMorphClientEntity asMorphClientEntity)
                asMorphClientEntity.featherMorph$overridePose(null);

            entity.startRiding(bindingPlayer);
        }
        else if (!bindingPlayer.isPassenger() && entity.isPassenger())
        {
            entity.stopRiding();
        }

        if (entity instanceof MorphLocalPlayer player)
            player.fishing = bindingPlayer.fishing;

        if (!propertyHolder().contains(PropertyNames.LIVING_ENTITY_INVISIBLE))
            entity.setInvisible(bindingPlayer.isInvisible());
        profiler.pop();

        markNotSyncing();
        profiler.pop();
    }

    protected void syncOnLivingEntity(LivingEntity entity)
    {
        var entitylimbAnimatorAccessor = (LimbAnimatorAccessor) entity.walkAnimation;
        var playerLimbAccessor = (LimbAnimatorAccessor) bindingPlayer.walkAnimation;
        var playerLimb = bindingPlayer.walkAnimation;

        entitylimbAnimatorAccessor.setSpeedOld(playerLimbAccessor.getSpeedOld());
        entitylimbAnimatorAccessor.setPosition(playerLimb.position());
        entitylimbAnimatorAccessor.setSpeed(playerLimb.speed());

        // Sleep Pos
        if (!propertyHolder().contains(PropertyNames.LIVING_ENTITY_BED_POS))
        {
            var sleepPos = bindingPlayer.getSleepingPos().orElse(null);

            if (sleepPos != null)
                entity.setSleepingPos(sleepPos);
            else
                entity.clearSleepingPos();
        }

        // Health
        var healthAttribute = entity.getAttribute(Attributes.MAX_HEALTH);

        if (healthAttribute != null)
            healthAttribute.setBaseValue(bindingPlayer.getMaxHealth());

        if (!propertyHolder.contains(PropertyNames.LIVING_ENTITY_STATIC_HEALTH))
            entity.setHealth(bindingPlayer.getHealth());

        // Scale
        var scaleAttribute = entity.getAttribute(Attributes.SCALE);

        if (scaleAttribute != null && scaleAttribute.getValue() != bindingPlayer.getAttributeValue(Attributes.SCALE))
            scaleAttribute.setBaseValue(bindingPlayer.getAttributeValue(Attributes.SCALE));

        if (entity.isFallFlying() != bindingPlayer.isFallFlying())
            ((EntityAccessor) entity).callSetSharedFlag(7, bindingPlayer.isFallFlying());

        // Hurt and death
        entity.hurtTime = bindingPlayer.hurtTime;
        entity.deathTime = bindingPlayer.deathTime;

        if (entity.isUsingItem() != bindingPlayer.isUsingItem())
        {
            entity.startUsingItem(bindingPlayer.getUsedItemHand());
            ((LivingEntityAccessor)entity).callSetLivingEntityFlag(1, bindingPlayer.isUsingItem());
            ((LivingEntityAccessor)entity).callSetLivingEntityFlag(2, bindingPlayer.getUsedItemHand() == InteractionHand.OFF_HAND);
        }

        entity.setArrowCount(bindingPlayer.getArrowCount());
    }

    //endregion

    private final List<String> maskedAnimations = new ObjectArrayList<>();

    public void maskEntityAnimation(String animationName)
    {
        if (!maskedAnimations.contains(animationName))
            maskedAnimations.add(animationName);
    }

    public void unmaskEntityAnimation(String animationName)
    {
        maskedAnimations.remove(animationName);
    }

    public boolean isEntityAnimationMasked(String animationName)
    {
        return maskedAnimations.contains(animationName);
    }

    //region Disposal

    protected void disposeEntity(@NotNull Entity disguise)
    {
        if (disguise instanceof Guardian guardian && guardian.getActiveAttackTarget() instanceof IHasOverrideGlowing overrideGlowing)
            overrideGlowing.morphclient$overrideClientGlowing(false);

        if (RenderSystem.isOnRenderThread())
            disguise.discard();
        else
            addSchedule(disguise::discard);
    }

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public final boolean disposed()
    {
        return disposed.get();
    }

    protected abstract void onDispose();

    public final void dispose()
    {
        if (disposed())
            return;

        disposeEntity(disguiseInstance);

        try
        {
            this.onDispose();
        }
        catch (Throwable t)
        {
            logger.warn("Error calling onDispose() for DisguiseSyncer: %s".formatted(t.getMessage()));
            t.printStackTrace();
        }

        world = null;
        prevWorld = null;
        disposed.set(true);

        propertyHolder.reset();

        bindingPlayer.refreshDimensions();

        try
        {
            postDispose();
        }
        catch (Throwable t)
        {
            logger.warn("Error calling postDispose() for DisguiseSyncer: %s".formatted(t.getMessage()));
            t.printStackTrace();
        }
    }

    protected void postDispose()
    {
    }

    public void initialize()
    {
        initialSync();
    }

    //endregion Disposal
}
