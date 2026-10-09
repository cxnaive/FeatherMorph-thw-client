package xyz.nifeather.morph.client.mixin.emf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import xyz.nifeather.morph.client.entities.IMorphClientEntity;

@Pseudo
@Mixin(targets = "traben.entity_model_features.utils.EMFLODHandler")
public abstract class EMFAnimationEntityContextMixin
{
    @Inject(method = "getLODFactorOfEntity", remap = false, at = @At("HEAD"),
            cancellable = true
    )
    private static void morphclient$modifyLODFactor(EMFEntityRenderState state, CallbackInfoReturnable<Integer> cir)
    {
        if (state.emfEntity() instanceof IMorphClientEntity morphClientEntity)
        {
            if (morphClientEntity.featherMorph$isDisguiseEntity())
                cir.setReturnValue(0);
        }
    }
}
