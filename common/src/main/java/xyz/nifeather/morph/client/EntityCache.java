package xyz.nifeather.morph.client;

import net.minecraft.world.entity.EntityTypes;
import com.mojang.authlib.GameProfile;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import xiamomc.pluginbase.Bindables.Bindable;
import xyz.nifeather.morph.client.entities.IMorphClientEntity;
import xyz.nifeather.morph.client.entities.MorphLocalAvatar;
import xyz.nifeather.morph.client.entities.MorphLocalPlayer;
import xyz.nifeather.morph.client.utilties.EntityCacheUtils;
import xyz.nifeather.morph.shared.EntityTypeLookup;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EntityCache
{
    public static EntityCache getGlobalCache()
    {
        return globalInstance;
    }

    private static final EntityCache globalInstance = new EntityCache();

    public EntityCache()
    {
        EntityCacheUtils.addOnEntityAddHook(this, e ->
        {
            if (e.entityTags().contains(tag)) return;

            var targets = cacheMap.entrySet().stream().filter(entry -> entry.getValue().getUUID().equals(e.getUUID()))
                    .toList();

            targets.forEach(ee ->
            {
                var id = ee.getKey();

                discardEntity(id);
            });
        });
    }

    private final Map<String, Entity> cacheMap = new ConcurrentHashMap<>();

    public void clearCache()
    {
        cacheMap.clear();
    }

    public boolean containsId(int id)
    {
        try
        {
            //照理说values里不该出现null值，但这确实发生了
            return cacheMap.values().stream().filter(l -> l.getId() == id).findFirst().orElse(null) != null;
        }
        catch (Exception e)
        {
            LoggerFactory.getLogger("MorphClient").error("Error checking cache: " + e.getMessage());
            e.printStackTrace();

            cacheMap.remove(null);

            return false;
        }
    }

    public final Bindable<Boolean> droppingCaches = new Bindable<>();

    public void discardEntity(String identifier)
    {
        var entity = cacheMap.getOrDefault(identifier, null);

        if (entity != null)
        {
            FeatherMorphClientBootstrap.getInstance().schedule(() ->
            {
                entity.discard();
                entity.onClientRemoval();
            });

            cacheMap.remove(identifier);
        }
    }

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private final long lockWait = 10;

    public static final String tag = "FMC_ClientView";

    protected static final RandomSource random = RandomSource.create();
    // Allocate local IDs far from the server's normal entity range.
    private static final AtomicInteger nextLocalEntityId = new AtomicInteger(Integer.MIN_VALUE);

    private static void assignLocalId(Entity entity)
    {
        int localId;
        do
        {
            localId = nextLocalEntityId.getAndIncrement();
        }
        while (entity.level().getEntity(localId) != null);
        entity.setId(localId);
    }

    public void dropAll()
    {
        droppingCaches.set(true);

        var morphClient = FeatherMorphClientBootstrap.getInstance();
        cacheMap.forEach((id, entity) ->
        {
            morphClient.schedule(entity::discard);
            cacheMap.remove(id);
        });

        cacheMap.clear();
        droppingCaches.set(false);
    }

    @Nullable
    public Entity getEntity(String identifier, Player bindingPlayer)
    {
        if (identifier == null) return null;

        if (disposed.get())
            throw new RuntimeException("Cannot access getEntity() for a disposed EntityCache.");

        Entity cache;

        boolean locked;
        try
        {
            locked = readLock.tryLock(lockWait, TimeUnit.MILLISECONDS);
        }
        catch (Throwable t)
        {
            FeatherMorphClientBootstrap.LOGGER.warn("Unable to lock entity cache for read: " + t.getMessage());
            locked = false;
        }

        if (!locked)
        {
            FeatherMorphClientBootstrap.LOGGER.warn("Unable to lock entity cache for read: Timed out.");
            return null;
        }

        try
        {
            cache = cacheMap.getOrDefault(identifier, null);
        }
        finally
        {
            readLock.unlock();
        }

        if (cache != null && !cache.isRemoved()) return cache;

        net.minecraft.world.entity.Entity spawnedEntity = null;

        if (identifier.startsWith("minecraft:"))
        {
            var typeOptional = EntityTypeLookup.byIdentifier(identifier);

            if (typeOptional.isEmpty()) return null;

            var type = typeOptional.get();

            try
            {
                var world = Minecraft.getInstance().level;
                if (world == null) return null;

                var instance = type == EntityTypes.MANNEQUIN ? new MorphLocalAvatar(world) : type.create(world, EntitySpawnReason.COMMAND);

                var uuid = ensureUUIDUnique(Mth.createInsecureUUID(random));
                instance.setUUID(uuid);

                spawnedEntity = instance;
            }
            catch (Throwable t)
            {
                FeatherMorphClientBootstrap.LOGGER.error("Error occurred while creating entity: %s".formatted(t.getMessage()));
                t.printStackTrace();

                return null;
            }
        }
        else if (identifier.startsWith("player:"))
        {
            var splitedId = identifier.split(":", 2);

            if (splitedId.length != 2) return null;

            var uuid = ensureUUIDUnique(Mth.createInsecureUUID(random));
            var profile = new GameProfile(uuid, splitedId[1]);

            try
            {
                var world = Minecraft.getInstance().level;
                if (world == null) return null;
                var localPlayer = new MorphLocalPlayer(world, profile, bindingPlayer);
                localPlayer.updateSkin(new GameProfile(Util.NIL_UUID, splitedId[1]));
                spawnedEntity = localPlayer;
            }
            catch (Throwable t)
            {
                FeatherMorphClientBootstrap.LOGGER.error("Error occurred while creating entity: %s".formatted(t.getMessage()));
                t.printStackTrace();
                return null;
            }
        }

        if (spawnedEntity == null) return null;

        // 26.2 leaves new entities without an ID. Keep local disguises separate from server IDs.
        assignLocalId(spawnedEntity);
        if (spawnedEntity instanceof EnderDragon dragon)
        {
            for (var part : dragon.getSubEntities())
                assignLocalId(part);
        }

        try
        {
            locked = writeLock.tryLock(lockWait, TimeUnit.MILLISECONDS);
        }
        catch (Throwable t)
        {
            FeatherMorphClientBootstrap.LOGGER.warn("Unable to lock entity cache for write: " + t.getMessage());
            t.printStackTrace();

            return null;
        }

        if (!locked)
        {
            FeatherMorphClientBootstrap.LOGGER.warn("Unable to lock entity cache for write: Timed out");
            return null;
        }

        try
        {
            spawnedEntity.addTag(tag);

            cacheMap.put(identifier, spawnedEntity);
        }
        finally
        {
            writeLock.unlock();
        }

        //living.setPos(0, -4096, 0);
        if (identifier.startsWith("player:"))
            LoggerFactory.getLogger("morph").info("Pushing " + identifier + " into EntityCache.");

        return spawnedEntity;
    }

    /**
     * 确保传入的UUID在客户端世界里是唯一的
     * @param uuid 目标UUID
     * @return
     */
    private UUID ensureUUIDUnique(UUID uuid)
    {
        var world = Minecraft.getInstance().level;
        if (world == null) return uuid;

        var haveMatch = true;
        while (haveMatch)
        {
            haveMatch = false;

            for (var entity : world.entitiesForRendering())
            {
                if (entity.getUUID().equals(uuid))
                {
                    uuid = Mth.createInsecureUUID(random);
                    haveMatch = true;
                    break;
                }
            }
        }

        return uuid;
    }

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public boolean disposed()
    {
        return disposed.get();
    }

    public void dispose()
    {
        this.dropAll();
        EntityCacheUtils.removeOnEntityAddHook(this);

        disposed.set(true);
    }
}
