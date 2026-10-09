package xyz.nifeather.morph.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;
import xiamomc.pluginbase.Exceptions.NullDependencyException;
import xyz.nifeather.morph.client.graphics.toasts.DisguiseEntryToast;
import xyz.nifeather.morph.client.graphics.toasts.NewDisguiseSetToast;
import xyz.nifeather.morph.client.properties.ClientProperty;
import xyz.nifeather.morph.client.properties.ClientDisguiseProperties;
import xyz.nifeather.morph.client.syncers.ClientDisguiseSyncer;
import xyz.nifeather.morph.client.syncers.DisguiseSyncer;
import xyz.nifeather.morph.client.syncers.OtherClientDisguiseSyncer;
import xyz.nifeather.morph.client.syncers.animations.AnimHandlerIndex;
import xyz.nifeather.morph.shared.AnimationNames;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ClientMorphManager extends MorphClientObject
{
    private final SortedSet<String> availableMorphs = new ObjectAVLTreeSet<>();

    public List<String> getAvailableMorphs()
    {
        return availableMorphs.stream().toList();
    }

    public void clearAvailableDisguises()
    {
        var disguises = new ObjectArrayList<>(availableMorphs);
        availableMorphs.clear();

        invokeRevoke(disguises);
    }

    //region Common

    public final Bindable<String> selectedIdentifier = new Bindable<>(null);

    public final Bindable<String> currentIdentifier = new Bindable<>(null);

    @Deprecated(forRemoval = true)
    public final Bindable<Boolean> equipOverriden = new Bindable<>(false);

    @Deprecated(forRemoval = true)
    public final Bindable<CompoundTag> currentNbtCompound = new Bindable<>(null);

    public final Bindable<Float> revealingValue = new Bindable<>(0f);

    @Resolved
    private DisguiseInstanceTracker instanceTracker;

    //todo: Pure history shit, remove this after we have done implementing OutputHandle on the client.
    private final Map<Integer, Map<String, String>> storedProperties = new ConcurrentHashMap<>();

    public Map<String, String> getNetworkPropertiesFor(int id)
    {
        return storedProperties.getOrDefault(id, new ConcurrentHashMap<>());
    }

    public void setNetworkPropertiesFor(int id, Map<String, String> map)
    {
        var asConcurrent = map instanceof ConcurrentHashMap<String, String> concurrent
                           ? concurrent
                           : new ConcurrentHashMap<>(map);

        storedProperties.put(id, asConcurrent);
    }

    public void discardPropertiesFor(int id, List<String> propertiesToDiscard)
    {
        var properties = storedProperties.getOrDefault(id, null);
        if (properties == null) return;

        propertiesToDiscard.forEach(properties::remove);
    }

    public void mergeNetworkPropertiesFor(int id, Map<String, String> input)
    {
        var map = getNetworkPropertiesFor(id);
        map.putAll(input);

        setNetworkPropertiesFor(id, map);
    }

    public void dropNetworkPropertiesFor(int id)
    {
        storedProperties.remove(id);
    }

    //endregion

    private final List<String> emotes = new ObjectArrayList<>();

    public void setEmotes(List<String> emotes)
    {
        if (emotes.size() > 4)
            logger.warn("Server send a emote that has more than 4 elements!");

        this.emotes.clear();
        this.emotes.addAll(emotes);
    }

    @Nullable
    public String lastEmote;

    @Nullable
    public String emoteDisplayName;

    public void setEmoteDisplay(String id)
    {
        this.emoteDisplayName = id;
    }

    public void playEmote(String emote)
    {
        if (!emote.equals(AnimationNames.RESET) && !emote.equals(AnimationNames.TRY_RESET))
            this.lastEmote = emote;
        else
            this.lastEmote = null;

        if (localPlayerSyncer != null)
            localPlayerSyncer.playAnimation(emote);
    }

    public List<String> getEmotes()
    {
        return new ObjectArrayList<>(emotes);
    }

    @Nullable
    private DisguiseSyncer localPlayerSyncer;

    @Initializer
    private void load()
    {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            if (RenderSystem.isOnRenderThread())
                onDisconnect();
            else
                this.addSchedule(this::onDisconnect);
        });

        this.addSchedule(this::update);
    }

    private void onDisconnect()
    {
        world = null;
        prevWorld = null;

        reset();
    }

    private ClientLevel world;
    private ClientLevel prevWorld;

    private void update()
    {
        this.addSchedule(this::update);

        world = Minecraft.getInstance().level;

        if (world == null) return;

        if (prevWorld == null)
            prevWorld = world;

        if (world != prevWorld)
            prevWorld = world;

        // The whole ClientMorphManager is a TOTALLY mess
        var currentClientPlayer = Minecraft.getInstance().player;

        if (lastClientPlayer != null && lastClientPlayer != currentClientPlayer)
            refreshLocalSyncer(currentIdentifier.get()).ifPresent(this::setBindingClientSyncer);

        if (currentClientPlayer == null || lastClientPlayer == currentClientPlayer) return;

        lastClientPlayer = currentClientPlayer;
    }

    @Nullable
    private Player lastClientPlayer;

    private void disposeExistingSyncerIfPresent()
    {
        if (this.localPlayerSyncer == null)
            return;

        logger.info("Removing previous syncer " + localPlayerSyncer);
        instanceTracker.removeSyncer(localPlayerSyncer);
        localPlayerSyncer.dispose();
        localPlayerSyncer = null;
    }

    //region Add/Remove/Set disguises

    public final Bindable<Boolean> selfVisibleEnabled = new Bindable<>(false);

    private final List<Function<List<String>, Boolean>> onGrantConsumers = new ObjectArrayList<>();
    public void onMorphGrant(Function<List<String>, Boolean> consumer)
    {
        onGrantConsumers.add(consumer);
    }

    private final List<Function<List<String>, Boolean>> onRevokeConsumers = new ObjectArrayList<>();
    public void onMorphRevoke(Function<List<String>, Boolean> consumer)
    {
        onRevokeConsumers.add(consumer);
    }

    private void invokeRevoke(List<String> diff)
    {
        var tobeRemoved = new ObjectArrayList<Function<List<String>, Boolean>>();

        onRevokeConsumers.forEach(f ->
        {
            if (!f.apply(diff)) tobeRemoved.add(f);
        });

        onRevokeConsumers.removeAll(tobeRemoved);
    }

    private void invokeGrant(List<String> diff)
    {
        var tobeRemoved = new ObjectArrayList<Function<List<String>, Boolean>>();

        onGrantConsumers.forEach(f ->
        {
            if (!f.apply(diff)) tobeRemoved.add(f);
        });

        onGrantConsumers.removeAll(tobeRemoved);
    }

    public void setDisguises(List<String> identifiers, boolean displayToasts)
    {
        invokeRevoke(availableMorphs.stream().toList());

        availableMorphs.clear();

        this.addDisguises(identifiers, false);

        DisguiseEntryToast.invalidateAll();

        if (displayToasts)
            Minecraft.getInstance().gui.toastManager().addToast(new NewDisguiseSetToast(availableMorphs.size() <= 0));
    }

    public void addDisguises(List<String> identifiers, boolean displayToasts)
    {
        identifiers = new ObjectArrayList<>(identifiers);

        identifiers.removeIf(availableMorphs::contains);
        identifiers.forEach(i -> addDisguisePrivate(i, displayToasts));

        invokeGrant(identifiers);
    }

    public void addDisguise(String identifier, boolean displayToasts)
    {
        addDisguisePrivate(identifier, displayToasts);
    }

    public void removeDisguises(List<String> identifiers, boolean displayToasts)
    {
        identifiers.forEach(i -> removeDisguisePrivate(i, displayToasts));

        invokeRevoke(identifiers);
    }

    public void removeDisguise(String identifier, boolean displayToasts)
    {
        removeDisguisePrivate(identifier, displayToasts);
    }

    private void addDisguisePrivate(String identifier, boolean displayToasts)
    {
        if (identifier.isEmpty()) return;

        availableMorphs.add(identifier);

        if (displayToasts)
            Minecraft.getInstance().gui.toastManager().addToast(new DisguiseEntryToast(identifier, true));
    }

    private void removeDisguisePrivate(String identifier, boolean displayToasts)
    {
        availableMorphs.remove(identifier);

        if (displayToasts)
            Minecraft.getInstance().gui.toastManager().addToast(new DisguiseEntryToast(identifier, false));
    }

    //endregion Add/Remove/Set disguises

    //region Items

    @Deprecated(forRemoval = true)
    private final Map<EquipmentSlot, ItemStack> equipmentSlotItemStackMap = new Object2ObjectOpenHashMap<>();

    @Deprecated(forRemoval = true)
    public ItemStack getOverridedItemStackOn(EquipmentSlot slot)
    {
        return equipmentSlotItemStackMap.getOrDefault(slot, air);
    }

    @Deprecated(forRemoval = true)
    public void swapHand()
    {
        var mainHand = equipmentSlotItemStackMap.getOrDefault(EquipmentSlot.MAINHAND, air);
        var offHand = equipmentSlotItemStackMap.getOrDefault(EquipmentSlot.OFFHAND, air);
        equipmentSlotItemStackMap.put(EquipmentSlot.MAINHAND, offHand);
        equipmentSlotItemStackMap.put(EquipmentSlot.OFFHAND, mainHand);
    }

    @Deprecated(forRemoval = true)
    public void setEquip(EquipmentSlot slot, ItemStack item)
    {
        equipmentSlotItemStackMap.put(slot, item);
    }

    private final ItemStack air = ItemStack.EMPTY;

    //endregion Items

    public void reset()
    {
        this.clearAvailableDisguises();

        this.setEmotes(List.of());

        selectedIdentifier.set(null);
        currentIdentifier.set(null);

        revealingValue.set(0f);
        if (localPlayerSyncer != null)
            localPlayerSyncer.dispose();

        localPlayerSyncer = null;
        lastEmote = null;

        EntityCache.getGlobalCache().dropAll();

        prevWorld = null;
        world = null;
        lastClientPlayer = null;
    }

    public void setCurrent(String val)
    {
        RenderSystem.assertOnRenderThread();

        if (localPlayerSyncer != null)
            localPlayerSyncer.dispose();

        localPlayerSyncer = null;
        lastEmote = null;
        emoteDisplayName = null;
        serverSkin = null;

        dropNetworkPropertiesFor(Minecraft.getInstance().player.getId());
        refreshLocalSyncer(val).ifPresent(this::setBindingClientSyncer);

        if (val != null && val.isBlank())
            val = null;

        currentIdentifier.set(val);

        equipOverriden.set(false);
        equipmentSlotItemStackMap.clear();
        currentNbtCompound.set(null);
    }

    private Optional<DisguiseSyncer> refreshLocalSyncer(@Nullable String disguiseIdentifier)
    {
        if (disguiseIdentifier == null || disguiseIdentifier.isBlank())
            return Optional.empty();

        disposeExistingSyncerIfPresent();

        var clientPlayer = Minecraft.getInstance().player;
        assert clientPlayer != null;

        var newDisguiseSyncer = this.createSyncerFor(clientPlayer, disguiseIdentifier, clientPlayer.getId());
        if (newDisguiseSyncer == null) return Optional.empty();

        if (lastEmote != null)
            newDisguiseSyncer.playAnimation(lastEmote);

        if (serverSkin != null)
            newDisguiseSyncer.updateSkin(serverSkin);

        return Optional.of(newDisguiseSyncer);
    }

    private void setBindingClientSyncer(@NotNull DisguiseSyncer disguiseSyncer)
    {
        instanceTracker.setSyncer(Minecraft.getInstance().player.getId(), disguiseSyncer);
        localPlayerSyncer = disguiseSyncer;
    }

    @Resolved
    private AnimHandlerIndex animIndex;

    @Nullable
    public DisguiseSyncer createSyncerFor(AbstractClientPlayer player, String disguiseId, int networkId)
    {
        var clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null)
            throw new NullDependencyException("Required non-null client player to get DisguiseSyncer");

        var isClient = clientPlayer == player;
        EntityCache entityCache = isClient ? EntityCache.getGlobalCache() : new EntityCache();
        var entity = entityCache.getEntity(disguiseId, player);
        if (entity == null)
            return null;

        addSchedule(() -> Minecraft.getInstance().level.addEntity(entity));

        DisguiseSyncer syncer;
        if (isClient)
            syncer = new ClientDisguiseSyncer(player, disguiseId, networkId, entity);
        else
            syncer = new OtherClientDisguiseSyncer(player, disguiseId, networkId, entity);

        var handler = animIndex.get(disguiseId);
        syncer.setAnimationHandler(handler);

        var properties = getNetworkPropertiesFor(player.getId());
        var propertyCollection = ClientDisguiseProperties.INSTANCE.getHandler(entity).orElse(null);

        if (propertyCollection != null)
            syncer.propertyHolder().registerFromPropertyCollection(propertyCollection);

        syncer.propertyHolder().updateFromPropertiesInput(properties).forEach((p, v) ->
        {
            ((ClientProperty<Object, Entity>) p).apply(entity, v);
        });

        return syncer;
    }

    @Nullable
    @Deprecated(forRemoval = true)
    private GameProfile serverSkin;

    @Deprecated(forRemoval = true)
    public void updateSkin(GameProfile profile)
    {
        serverSkin = profile;

        if (localPlayerSyncer != null)
        {
            localPlayerSyncer.updateSkin(profile);
        }
        else
        {
            logger.warn("Calling UpdateSkin while localPlayerSyncer is null!");
            Thread.dumpStack();
        }
    }

    public void handlePropertiesUpdate(DisguiseSyncer syncer, Map<String, String> input)
    {
    }
}