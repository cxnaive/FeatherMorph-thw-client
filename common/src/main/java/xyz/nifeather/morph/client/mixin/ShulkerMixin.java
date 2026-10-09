package xyz.nifeather.morph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nifeather.morph.client.entities.IMorphClientEntity;

@Mixin(Shulker.class)
public class ShulkerMixin
{
    @WrapOperation(method = "setPos", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V"))
    private <T> void morphclient$preserveDisguisePeek(SynchedEntityData data, EntityDataAccessor<T> accessor,
                                                    T value, Operation<Void> original)
    {
        // Rendering moves the proxy between its hidden and visible positions each frame.
        if (!((IMorphClientEntity) this).featherMorph$isDisguiseEntity())
            original.call(data, accessor, value);
    }

    @Inject(method = "getRenderPosition", at = @At("HEAD"), cancellable = true)
    private void morphclient$ignoreProxyTeleport(float partialTick, CallbackInfoReturnable<Vec3> cir)
    {
        if (((IMorphClientEntity) this).featherMorph$isDisguiseEntity())
            cir.setReturnValue(null);
    }
}
