package xyz.nifeather.morph.client.graphics.toasts;

import xyz.nifeather.morph.client.MorphClientObject;
import net.minecraft.client.Minecraft;
import xiamomc.pluginbase.Annotations.Initializer;

public class MorphToastHelper extends MorphClientObject
{
    @Initializer
    private void load()
    {
        var toastMgr = Minecraft.getInstance().gui.toastManager();
    }
}
