package xyz.nifeather.morph.client.properties;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import xyz.nifeather.morph.client.properties.impl.*;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ClientDisguiseProperties
{
    public static final ClientDisguiseProperties INSTANCE = new ClientDisguiseProperties();

    private final Map<EntityType<?>, AbstractPropertyCollection> handlerMap = new ConcurrentHashMap<>();

    public void reset()
    {
        handlerMap.clear();
    }
    public void init()
    {
        if (Minecraft.getInstance().level == null)
            throw new RuntimeException("Client level is not available, cannot initialize disguise properties!");

        reset();

        register(EntityTypes.PLAYER, new PlayerPropertyCollection());
        register(EntityTypes.ARMOR_STAND, new ArmorStandPropertyCollection());
        register(EntityTypes.AXOLOTL, new AxolotlPropertyCollection());
        register(EntityTypes.CAT, new CatPropertyCollection());
        register(EntityTypes.CHICKEN, new ChickenPropertyCollection());
        register(EntityTypes.COW, new CowPropertyCollection());
        register(EntityTypes.CREEPER, new CreeperPropertyCollection());
        register(EntityTypes.ENDER_DRAGON, new EnderDragonPropertyCollection());
        register(EntityTypes.FOX, new FoxPropertyCollection());
        register(EntityTypes.FROG, new FrogPropertyCollection());
        register(EntityTypes.GOAT, new GoatPropertyCollection());
        register(EntityTypes.HAPPY_GHAST, new HappyGhastPropertyCollection());

        register(EntityTypes.HOGLIN, new HoglinPropertyCollection());
        register(EntityTypes.ZOGLIN, new ZoglinPropertyCollection());

        register(EntityTypes.HORSE, new HorsePropertyCollection());

        register(EntityTypes.LLAMA, new LlamaPropertyCollection());
        register(EntityTypes.MOOSHROOM, new MooshroomPropertyCollection());

        register(EntityTypes.PANDA, new PandaPropertyCollection());
        register(EntityTypes.PARROT, new ParrotPropertyCollection());
        register(EntityTypes.PHANTOM, new PhantomPropertyCollection());

        register(EntityTypes.PIG, new PigPropertyCollection());
        register(EntityTypes.RABBIT, new RabbitPropertyCollection());

        register(EntityTypes.SHEEP, new SheepPropertyCollection());
        register(EntityTypes.SHULKER, new ShulkerPropertyCollection());

        register(EntityTypes.SLIME, new SlimePropertyCollection());
        register(EntityTypes.MAGMA_CUBE, new MagmaPropertyCollection());
        register(EntityTypes.SULFUR_CUBE, new SulfurCubePropertyCollection());

        register(EntityTypes.SNOW_GOLEM, new SnowGolemPropertyCollection());

        register(EntityTypes.TRADER_LLAMA, new TraderLlamaPropertyCollection());
        register(EntityTypes.TROPICAL_FISH, new TropicalFishPropertyCollection());

        register(EntityTypes.VILLAGER, new VillagerPropertyCollection());
        register(EntityTypes.WOLF, new WolfPropertyCollection());

        register(EntityTypes.ZOMBIE, new ZombiePropertyhandler());
        register(EntityTypes.ZOMBIE_VILLAGER, new ZombieVillagerPropertyhandler());

        register(EntityTypes.GUARDIAN, new GuardianPropertyCollection());

        register(EntityTypes.MANNEQUIN, new MannequinPropertyCollection());
        register(EntityTypes.COPPER_GOLEM, new CopperGolemPropertyCollection());

        register(EntityTypes.ITEM_DISPLAY, new ItemDisplayPropertyCollection());

        register(EntityTypes.ALLAY, new AllayPropertyCollection());
        register(EntityTypes.ARMADILLO, new ArmadilloPropertyCollection());
        register(EntityTypes.CREAKING, new CreakingPropertyCollection());
        register(EntityTypes.PIGLIN, new PiglinPropertyCollection());
        register(EntityTypes.PUFFERFISH, new PufferfishPropertyCollection());
        register(EntityTypes.SNIFFER, new SnifferPropertyCollection());
    }

    private final FallbackPropertyCollection fallbackPropertyHandler = new FallbackPropertyCollection();

    public FallbackPropertyCollection fallbackHandler()
    {
        return fallbackPropertyHandler;
    }

    public <E extends Entity> Optional<AbstractPropertyCollection> getHandler(E entity)
    {
        var match = handlerMap.getOrDefault(entity.getType(), null);

        return Optional.of((AbstractPropertyCollection) Objects.requireNonNullElse(match, fallbackPropertyHandler));
    }

    public <E extends Entity> void register(EntityType<E> entityType, AbstractPropertyCollection handler)
    {
        handlerMap.put(entityType, handler);
    }
}
