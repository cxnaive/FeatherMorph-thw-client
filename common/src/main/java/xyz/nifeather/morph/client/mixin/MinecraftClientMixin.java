package xyz.nifeather.morph.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nifeather.morph.client.graphics.transforms.Transformer;

import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin
{
    @Inject(method = "runTick", at = @At("RETURN"))
    private void featherMorph$onClientRender(boolean tick, CallbackInfo ci)
    {
        Transformer.onClientRenderEnd(Minecraft.getInstance());
    }

}
