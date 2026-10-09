package xyz.nifeather.morph.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.nifeather.morph.client.FeatherMorphClientBootstrap;
import xyz.nifeather.morph.client.graphics.ICustomItemInHandRenderer;
import xyz.nifeather.morph.client.graphics.PlayerRenderHelper;
import xyz.nifeather.morph.client.syncers.ClientDisguiseSyncer;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin implements ICustomItemInHandRenderer
{
    @Unique
    private static final PlayerRenderHelper morphclient$rendererHelper = PlayerRenderHelper.instance();

    @Nullable
    private ItemStack morphclient$mainHandItem;

    @Nullable
    private ItemStack morphclient$offHandItem;

    private boolean morphclient$shouldOverrideDisplayingItem;

    @Override
    public void morphclient$setShouldDisplayOverridingItem(boolean value)
    {
        this.morphclient$shouldOverrideDisplayingItem = value;
    }

    @Override
    public void morphclient$overrideMainHandItem(@Nullable ItemStack itemStack)
    {
        morphclient$mainHandItem = itemStack;
    }

    @Override
    public void morphclient$overrideOffHandItem(@Nullable ItemStack itemStack)
    {
        morphclient$offHandItem = itemStack;
    }

    @ModifyVariable(
            method = "tick",
            at = @At("STORE"),
            ordinal = 0
    )
    private ItemStack morphclient$modifyMainhandItem(ItemStack value)
    {
        return (morphclient$shouldOverrideDisplayingItem && morphclient$mainHandItem != null)
               ? morphclient$mainHandItem
               : value;
    }

    @ModifyExpressionValue(
            method = "submitHandsWithItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F")
    )
    private float morphclient$overrideSwingTime(float value, @Local(argsOnly = true) float f)
    {
        if (!FeatherMorphClientBootstrap.getInstance().getModConfigData().clientViewVisible())
            return value;

        var syncer = ClientDisguiseSyncer.getCurrentInstance();
        if (syncer == null || syncer.disposed() || !(syncer.getDisguiseInstance() instanceof LivingEntity living)) return value;

        return living.getAttackAnim(f);
    }

    @ModifyVariable(
            method = "tick",
            at = @At("STORE"),
            ordinal = 1
    )
    private ItemStack morphclient$modifyOffhandItem(ItemStack value)
    {
        return (morphclient$shouldOverrideDisplayingItem && morphclient$offHandItem != null)
               ? morphclient$offHandItem
               : value;
    }

    @Redirect(
            method = "renderPlayerArm",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V")
    )
    private void morphclient$renderArmHoldingItem_left(AvatarRenderer<?> instance, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, Identifier resourceLocation, boolean bl)
    {
        if (morphclient$canRender())
            morphclient$renderLeftArm(poseStack, submitNodeCollector, light);
        else
            instance.renderLeftHand(poseStack, submitNodeCollector, light, resourceLocation, bl);
    }

    @Redirect(
            method = "renderPlayerArm",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V")
    )
    private void morphclient$renderArmHoldingItem_right(AvatarRenderer instance, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, Identifier resourceLocation, boolean bl)
    {
        if (morphclient$canRender())
            morphclient$renderRightArm(poseStack, submitNodeCollector, light);
        else
            instance.renderRightHand(poseStack, submitNodeCollector, light, resourceLocation, bl);
    }

    @Redirect(
            method = "renderMapHand",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V")
    )
    private void morphclient$renderArmHoldingItem_right_alt(AvatarRenderer instance, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, Identifier resourceLocation, boolean bl)
    {
        if (morphclient$canRender())
            morphclient$renderRightArm(poseStack, submitNodeCollector, light);
        else
            instance.renderRightHand(poseStack, submitNodeCollector, light, resourceLocation, bl);
    }

    @Redirect(
            method = "renderMapHand",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V")
    )
    private void morphclient$renderArmHoldingItem_left_alt(AvatarRenderer instance, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, Identifier resourceLocation, boolean bl)
    {
        if (morphclient$canRender())
            morphclient$renderLeftArm(poseStack, submitNodeCollector, light);
        else
            instance.renderLeftHand(poseStack, submitNodeCollector, light, resourceLocation, bl);
    }

    @Unique
    private void morphclient$renderLeftArm(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light)
    {
        morphclient$rendererHelper.renderingLeftPart = true;

        this.morphclient$onArmRender(matrices, submitNodeCollector, light);
    }

    @Unique
    private void morphclient$renderRightArm(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light)
    {
        morphclient$rendererHelper.renderingLeftPart = false;

        this.morphclient$onArmRender(matrices, submitNodeCollector, light);
    }

    @Unique
    private boolean morphclient$canRender()
    {
        var clientSyncer = ClientDisguiseSyncer.getCurrentInstance();
        return clientSyncer != null && !clientSyncer.disposed() && FeatherMorphClientBootstrap.getInstance().getModConfigData().clientViewVisible();
    }

    @Unique
    private void morphclient$onArmRender(PoseStack matrices, SubmitNodeCollector submitNodeCollector,
                                         int light)
    {
        morphclient$rendererHelper.onArmDrawCall(
                matrices, submitNodeCollector,
                light
        );
    }
}
