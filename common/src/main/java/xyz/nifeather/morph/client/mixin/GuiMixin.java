package xyz.nifeather.morph.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nifeather.morph.client.utilties.Screens;

@Mixin(Gui.class)
public abstract class GuiMixin
{
    @Shadow private Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void featherMorph$onSetScreen(Screen screenNext, CallbackInfo ci)
    {
        Screens.getInstance().onChange(this.screen, screenNext);
    }
}
