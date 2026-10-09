package xyz.nifeather.morph.client.integrations.entityculling;

import dev.tr7zw.entityculling.EntityCullingModBase;
import dev.tr7zw.entityculling.access.Cullable;
import xyz.nifeather.morph.client.FeatherMorphClientBootstrap;
import xyz.nifeather.morph.client.entities.IMorphClientEntity;

public class EntityCullingCompatibilityHandler
{
    public static void tryAddDynamicEntityWhitelist()
    {
        var cullingMod = EntityCullingModBase.instance;
        if (cullingMod == null)
        {
            FeatherMorphClientBootstrap.getInstance().schedule(EntityCullingCompatibilityHandler::tryAddDynamicEntityWhitelist, 20);
            return;
        }

        cullingMod.addDynamicEntityWhitelist(e ->
        {
            if (!(e instanceof Cullable cullable))
                return false;

            boolean shouldSkipThisCull = e instanceof IMorphClientEntity iMorphClientEntity
                    && iMorphClientEntity.featherMorph$isDisguiseEntity();

            if (shouldSkipThisCull)
                cullable.setOutOfCamera(false);

            return shouldSkipThisCull;
        });

        FeatherMorphClientBootstrap.LOGGER.info("OK Added dynamic entity whitelist rule.");
    }
}
