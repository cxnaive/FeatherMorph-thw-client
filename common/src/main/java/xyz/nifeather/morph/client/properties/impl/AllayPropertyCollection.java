package xyz.nifeather.morph.client.properties.impl;

import net.minecraft.world.entity.animal.allay.Allay;
import xyz.nifeather.morph.client.entities.IAllay;
import xyz.nifeather.morph.client.properties.ClientProperty;
import xyz.nifeather.morph.client.properties.CommonInputHandles;
import xyz.nifeather.morph.client.properties.CommonOutputHandles;
import xyz.nifeather.morph.client.properties.PropertyNames;

public class AllayPropertyCollection extends LivingEntityPropertyCollection<Allay>
{
    public final ClientProperty<Boolean, Allay> DANCING =
            ClientProperty.builder(PropertyNames.ALLAY_DANCING, false, Allay.class)
                    .inputHandle(CommonInputHandles::readBoolean)
                    .outputHandle(CommonOutputHandles::writeBoolean)
                    .entityHandle((allay, dancing) -> ((IAllay) allay).morphclient$forceSetDancing(dancing))
                    .build();

    public AllayPropertyCollection()
    {
        register(DANCING);
    }
}
