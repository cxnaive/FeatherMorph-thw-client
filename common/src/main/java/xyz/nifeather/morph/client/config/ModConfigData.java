package xyz.nifeather.morph.client.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import org.jetbrains.annotations.ApiStatus;
import xyz.nifeather.morph.client.FeatherMorphClientBootstrap;
import xyz.nifeather.morph.client.graphics.transforms.easings.Easing;

@Config(name = "morphclient")
public class ModConfigData implements ConfigData
{
    public boolean alwaysShowPreviewInInventory = false;

    public boolean allowClientView = true;

    public boolean verbosePackets = false;

    public boolean displayDisguiseOnHud = true;

    public boolean changeCameraHeight = false;

    public float scrollSpeed = 1f;

    public boolean scaleNameTag = false;

    public boolean displayGrantRevokeToast = true;
    public boolean displayQuerySetToast = false;
    public boolean displayToastProgress = false;

    public boolean singlePlayerDebugging = false;

    public boolean disguiseListMouseDragging = false;

    @ApiStatus.Experimental
    public boolean protocolCompatibilityMode = false;

    public boolean clientViewVisible()
    {
        return FeatherMorphClientBootstrap.getInstance().morphManager.selfVisibleEnabled.get() && allowClientView;
    }
}
