package xyz.nifeather.morph.server.disguise.animations.provider;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import xyz.nifeather.morph.server.disguise.animations.AnimationSet;
import xyz.nifeather.morph.server.disguise.animations.bundled.*;

public class VanillaAnimationProvider extends DefaultAnimationProvider
{
    public VanillaAnimationProvider()
    {
        this.registerAnimSet(EntityTypes.WARDEN, new WardenAnimationSet());
        this.registerAnimSet(EntityTypes.SNIFFER, new SnifferAnimationSet());
        this.registerAnimSet(EntityTypes.ALLAY, new AllayAnimationSet());
        this.registerAnimSet(EntityTypes.ARMADILLO, new ArmadilloAnimationSet());
        this.registerAnimSet(EntityTypes.SHULKER, new ShulkerAnimationSet());
        this.registerAnimSet(EntityTypes.CAT, new CatAnimationSet());

        // Disabled because parrot dancing is not controlled directly by the metadata or event
        // this.registerAnimSet(EntityTypes.PARROT.getKey().asString(), new ParrotAnimationSet());

        this.registerAnimSet(EntityTypes.PIGLIN, new PiglinAnimationSet());
        this.registerAnimSet(EntityTypes.PUFFERFISH, new PufferfishAnimationSet());
        this.registerAnimSet(EntityTypes.FOX, new FoxAnimationSet());
        this.registerAnimSet(EntityTypes.FROG, new FrogAnimationSet());
        this.registerAnimSet(EntityTypes.WOLF, new WolfAnimationSet());
        this.registerAnimSet(EntityTypes.PANDA, new PandaAnimationSet());
        this.registerAnimSet(EntityTypes.CREAKING, new CreakingAnimationSet());
    }

    @Override
    public @NotNull AnimationSet getAnimationSetFor(String disguiseID)
    {
        return animSets.getOrDefault(disguiseID, fallbackAnimationSet);
    }
}
