package xyz.nifeather.morph.client.properties.impl;

import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import xyz.nifeather.morph.client.properties.ClientProperty;
import xyz.nifeather.morph.client.properties.CommonInputHandles;
import xyz.nifeather.morph.client.properties.PropertyNames;

public class SlimePropertyCollection extends LivingEntityPropertyCollection<AbstractCubeMob>
{
    public final ClientProperty<Integer, AbstractCubeMob> SIZE =
            ClientProperty.builder(PropertyNames.SLIME_MAGMA_SIZE, 0, AbstractCubeMob.class)
                    .inputHandle(CommonInputHandles::intOrEmpty)
                    .entityHandle((slime, size) -> slime.setSize(size, false))
                    .build();

    public SlimePropertyCollection()
    {
        register(SIZE);
    }
}
