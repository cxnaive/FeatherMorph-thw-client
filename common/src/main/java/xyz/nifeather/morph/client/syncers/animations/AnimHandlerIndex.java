package xyz.nifeather.morph.client.syncers.animations;

import net.minecraft.world.entity.EntityTypes;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.client.syncers.animations.impl.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.EntityType;

public class AnimHandlerIndex
{
    private final Map<String, AnimationHandler> handlerMap = new ConcurrentHashMap<>();

    public AnimHandlerIndex()
    {
        register(EntityTypes.WARDEN, new WardenAnimationHandler());
        register(EntityTypes.SNIFFER, new SnifferAnimationHandler());
        register(EntityTypes.ALLAY, new AllayAnimationHandler());
        register(EntityTypes.ARMADILLO, new ArmadilloAnimationHandler());
        register(EntityTypes.SHULKER, new ShulkerAnimationHandler());
        register(EntityTypes.CAT, new CatAnimationHandler());
        register(EntityTypes.PARROT, new ParrotAnimationHandler());
        register(EntityTypes.PIGLIN, new PiglinAnimationHandler());
        register(EntityTypes.PUFFERFISH, new PufferfishAnimationHandler());
        register(EntityTypes.FOX, new FoxAnimationHandler());
        register(EntityTypes.FROG, new FrogAnimationHandler());
        register(EntityTypes.PANDA, new PandaAnimationHandler());
        register(EntityTypes.WOLF, new WolfAnimationHandler());
        register(EntityTypes.PLAYER, new PlayerAnimationHandler());
        register(EntityTypes.CREAKING, new CreakingAnimationHandler());
        register(EntityTypes.MANNEQUIN, new MannequinAnimationHandler());
    }

    public void register(EntityType<?> type, AnimationHandler handler)
    {
        this.register(EntityType.getKey(type).toString(), handler);
    }

    public void register(String disguiseIdentifier, AnimationHandler handler)
    {
        handlerMap.put(disguiseIdentifier, handler);
    }

    @Nullable
    public AnimationHandler get(String disguiseIdentifier)
    {
        if (disguiseIdentifier.startsWith("player:"))
            disguiseIdentifier = "minecraft:player";

        return handlerMap.getOrDefault(disguiseIdentifier, null);
    }
}
