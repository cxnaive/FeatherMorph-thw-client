package xyz.nifeather.morph.client.entities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MorphLocalAvatar extends ClientMannequin
{
    public MorphLocalAvatar(Level level)
    {
        super(level, Minecraft.getInstance().playerSkinRenderCache());
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
