package xyz.nifeather.morph.server.events;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.server.ServerPluginObject;
import xyz.nifeather.morph.server.misc.DisguiseTypes;
import xyz.nifeather.morph.server.morphs.MorphManager;
import xyz.nifeather.morph.shared.platform.Services;

public class CommonEventProcessor extends ServerPluginObject
{
    public void initListener()
    {
        Services.PLATFORM.registerAfterKilledOtherEntityEvent(this::onEntityKilledEntityEvent);
    }

    @Resolved(shouldSolveImmediately = true)
    private MorphManager morphManager;

    private void onEntityKilledEntityEvent(ServerLevel world, Entity damager, LivingEntity killed, DamageSource damageSource)
    {
        if (!(damager instanceof ServerPlayer player))
            return;

        if (damager.equals(killed))
            return;

        if (killed instanceof ServerPlayer targetedPlayer)
        {
            morphManager.grantDisguiseToPlayer(player, DisguiseTypes.PLAYER.toId(targetedPlayer.getScoreboardName()));
        }
        else
        {
            var type = killed.getType();

            if (type != EntityTypes.CREAKING)
                morphManager.grantDisguiseToPlayer(player, EntityType.getKey(type).toString());
        }
    }
}
