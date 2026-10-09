package xyz.nifeather.morph.server.disguise.providers;

import xyz.nifeather.morph.shared.EntityTypeLookup;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.server.MorphServerLoader;
import xyz.nifeather.morph.server.morphs.DisguiseSession;
import xyz.nifeather.morph.server.disguise.animations.provider.VanillaAnimationProvider;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class VanillaDisguiseProvider extends AbstractDisguiseProvider
{
    @Override
    public String namespace()
    {
        return "minecraft";
    }

    private final List<String> availableIdentifiers;

    @Override
    public boolean isValid(String identifier)
    {
        var asIdentifier = Identifier.tryParse(identifier);
        if (asIdentifier == null)
            return false;

        return availableIdentifiers.contains(asIdentifier.getPath());
    }

    public VanillaDisguiseProvider()
    {
        var server = MorphServerLoader.mcserver;
        assert server != null;

        var entityTypes = server.registryAccess().lookup(BuiltInRegistries.ENTITY_TYPE.key());

        logger.info("Loading vanilla disguise identifiers...");

        var spawnPos = new BlockPos(0, 2048, 0);

        var list = new ObjectArrayList<String>();
        entityTypes.orElseThrow().forEach(eT ->
        {
            var entity = eT.spawn(server.overworld(), spawnPos, EntitySpawnReason.COMMAND);

            //logger.info("Filter " + eT + " --> " + entity);

            if (!(entity instanceof LivingEntity))
            {
                if (entity != null)
                    entity.discard();

                return;
            }

            entity.discard();

            list.add(EntityType.getKey(eT).getPath());
        });

        availableIdentifiers = new ObjectImmutableList<>(list);

        logger.info("Loaded %s vanilla disguise identifiers".formatted(list.size()));
    }

    @Override
    public List<String> availableDisguises()
    {
        return availableIdentifiers;
    }

    @Override
    public boolean disguise(Player player, String disguiseIdentifier)
    {
        return false;
    }

    @Override
    public void onPostConstructDisguise(DisguiseSession state, @Nullable Entity targetEntity)
    {
    }

    @Override
    public boolean unDisguise(Player player)
    {
        return true;
    }

    @Override
    public boolean updateDisguise(Player player, DisguiseSession disguiseSession)
    {
        return true;
    }

    @Override
    public void onDisguiseApplied(DisguiseSession disguiseSession)
    {
    }

    @Override
    public Component getDisplayName(String disguiseIdentifier)
    {
        var entityType = EntityTypeLookup.byIdentifier(disguiseIdentifier).orElse(null);
        if (entityType == null)
            return Component.nullToEmpty("???(%s)".formatted(disguiseIdentifier));

        return Component.translatable(entityType.getDescriptionId());
    }

    private final VanillaAnimationProvider animationProvider = new VanillaAnimationProvider();

    @Override
    public VanillaAnimationProvider getAnimationProvider()
    {
        return animationProvider;
    }
}
