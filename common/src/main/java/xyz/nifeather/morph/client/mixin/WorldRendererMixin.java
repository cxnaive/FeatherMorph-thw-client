package xyz.nifeather.morph.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nifeather.morph.client.graphics.PlayerRenderHelper;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin
{
    @Inject(
            method = "submitEntities",
            at = @At("TAIL")
    )
    private void onRenderEntities(PoseStack poseStack, LevelRenderState levelRenderState,
                                  SubmitNodeCollector submitNodeCollector, CallbackInfo ci)
    {
        PlayerRenderHelper.instance().submitCrystalBeamIfPossible(Minecraft.getInstance().getDeltaTracker(), poseStack,
                submitNodeCollector, LightCoordsUtil.FULL_BRIGHT);
    }
}
