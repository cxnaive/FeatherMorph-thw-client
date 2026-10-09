package xyz.nifeather.morph.client.entities;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nifeather.morph.client.FeatherMorphClientBootstrap;
import xyz.nifeather.morph.client.graphics.capes.ICapeProvider;
import xyz.nifeather.morph.client.graphics.capes.providers.KappaCapeProvider;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MorphLocalPlayer extends RemotePlayer
{
    private final String playerName;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @NotNull
    private Identifier morphTextureIdentifier = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    @Nullable
    private Identifier capeTextureIdentifier;

    @Nullable
    private Identifier ofCapeIdentifier;

    @Nullable
    private String skinTextureUrl;

    @NotNull
    private PlayerModelType model = PlayerModelType.SLIM;

    public boolean personEquals(MorphLocalPlayer other)
    {
        return other.playerName.equals(this.playerName);
    }

    public void copyFrom(MorphLocalPlayer prevPlayer)
    {
        requestId++;

        this.capeTextureIdentifier = prevPlayer.capeTextureIdentifier;
        this.morphTextureIdentifier = prevPlayer.morphTextureIdentifier;

        this.model = prevPlayer.model;

        this.skinTextureUrl = prevPlayer.skinTextureUrl;
    }

    @Nullable
    private Player bindingPlayer;

    @Nullable
    public Player getBindingPlayer()
    {
        return bindingPlayer;
    }

    public boolean hasBindingPlayer()
    {
        return bindingPlayer != null;
    }

    public void setBindingPlayer(@Nullable Player newInstance)
    {
        bindingPlayer = newInstance;
    }

    public MorphLocalPlayer(ClientLevel clientWorld, GameProfile initialProfile, @Nullable Player bindingPlayer)
    {
        super(clientWorld, initialProfile);

        this.setBindingPlayer(bindingPlayer);

        //if (bindingPlayer == null) bindingPlayer = MinecraftClient.getInstance().player;
        //this.bindingPlayer = bindingPlayer;

        this.playerName = initialProfile.name();

        this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte)127);
        this.updateSkin(initialProfile, true);
    }

    private volatile int requestId = 0;

    private final static ICapeProvider customCapeProvider = new KappaCapeProvider();

    //region From SkullBlockEntity

    private static CompletableFuture<Optional<GameProfile>> fetchProfile(String playerName)
    {
        //var logger = LoggerFactory.getLogger("MorphLocalPlayer#fetchProfile");

        return CompletableFuture.supplyAsync(() ->
        {
            // Fetch UUID first, first lookup in cache, if null, call profileRepository to find
            var services = Minecraft.getInstance().services();
            var cachedNameIdPair = services.nameToIdCache().get(playerName).orElse(null);

            //logger.info("Cached id pair is " + cachedNameIdPair);

            if (cachedNameIdPair == null)
            {
                var repoProfile = services.profileRepository().findProfileByName(playerName).orElse(null);
                //logger.info("From profile repo is " + repoProfile);

                if (repoProfile != null)
                    cachedNameIdPair = new NameAndId(repoProfile.id(), repoProfile.name());
            }

            //logger.info("The final value is " + cachedNameIdPair);
            if (cachedNameIdPair == null)
                return Optional.empty();

            // Then fetch skin
            //logger.info("Start fetch task!");
            var profileFetchResult = services.sessionService().fetchProfile(cachedNameIdPair.id(), true);

            //logger.info("Task completed with " + profileFetchResult);
            if (profileFetchResult == null)
                return Optional.empty();

            return Optional.of(profileFetchResult.profile());
        });
    }

    //endregion From SkullBlockEntity

    private final AtomicBoolean initialFetchFired = new AtomicBoolean(false);

    public void updateSkin(GameProfile profile)
    {
        updateSkin(profile, false);
    }

    private void updateSkin(GameProfile profile, boolean isInitial)
    {
        if (!RenderSystem.isOnRenderThread())
        {
            FeatherMorphClientBootstrap.getInstance().schedule(() -> updateSkin(profile));
            return;
        }

        if (isInitial && initialFetchFired.get())
            return;

        initialFetchFired.set(true);

        //logger.info("Fetching skin for " + profile);

        // No longer care about this
        //if (!profile.name().equals(playerName))
        //{
        //    logger.info("Profile %s player name not match : '%s' <-> '%s'".formatted(profile.id(), profile.name(), playerName));
        //    return;
        //}

        var reqId = requestId;
        requestId = reqId + 1;

        var invokeId = requestId;

        if (profile.properties().containsKey("textures"))
        {
            this.updateSkinTextures(profile);
            return;
        }

        fetchProfile(profile.name()).thenAccept(optional ->
        {
            if (invokeId == requestId)
                FeatherMorphClientBootstrap.getInstance().schedule(() -> optional.ifPresent(this::updateSkinTextures));
        });
    }

    @Nullable
    private volatile PlayerInfo playerInfo;

    private void updateSkinTextures(GameProfile profile)
    {
        //logger.info("Apply skin! " + profile.name() + " :: " + profile.id());
        RenderSystem.assertOnRenderThread();
        this.playerInfo = new PlayerInfo(profile, false);
    }

    @Nullable
    private BlockPos overrideSleepPos;

    @Override
    public @NotNull Optional<BlockPos> getSleepingPos()
    {
        if (overrideSleepPos != null)
            return Optional.of(overrideSleepPos);

        return super.getSleepingPos();
    }

    public void overrideSleepPos(BlockPos pos)
    {
        this.overrideSleepPos = pos;
    }

    @Override
    public boolean isSpectator()
    {
        return bindingPlayer != null && bindingPlayer.isSpectator();
    }

    private HumanoidArm overrideMainArm;

    public void setOverrideMainArm(@Nullable HumanoidArm arm)
    {
        this.overrideMainArm = arm;
    }

    @Override
    public @NotNull HumanoidArm getMainArm()
    {
        if (overrideMainArm != null) return overrideMainArm;

        return bindingPlayer == null ? super.getMainArm() : bindingPlayer.getMainArm();
    }

    @Override
    public boolean isFallFlying()
    {
        return bindingPlayer == null ? super.isFallFlying() : bindingPlayer.isFallFlying();
    }

    @Override
    public boolean isAutoSpinAttack()
    {
        return bindingPlayer == null ? super.isAutoSpinAttack() : bindingPlayer.isAutoSpinAttack();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
    }

    @Override
    public boolean isModelPartShown(PlayerModelPart modelPart)
    {
        return bindingPlayer == null || bindingPlayer.isModelPartShown(modelPart);
    }

    @Override
    public @Nullable PlayerInfo getPlayerInfo()
    {
        return playerInfo;
    }

    @Override
    public boolean shouldShowName()
    {
        return hasBindingPlayer() && (Minecraft.getInstance().getCameraEntity() != bindingPlayer || bindingPlayer != Minecraft.getInstance().player);
    }

    @Override
    public double distanceToSqr(Vec3 vector)
    {
        // compat with 3d skin layers
        if (vector.equals(Minecraft.getInstance().gameRenderer.mainCamera().position()))
            return 0d;

        return super.distanceToSqr(vector);
    }
}
