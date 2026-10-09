package xyz.nifeather.morph.client.graphics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nifeather.morph.client.DisguiseInstanceTracker;
import xyz.nifeather.morph.client.FeatherMorphClientBootstrap;
import xyz.nifeather.morph.client.entities.IDisguiseRenderState;
import xyz.nifeather.morph.client.entities.IMorphClientEntity;
import xyz.nifeather.morph.client.graphics.color.ColorUtils;
import xyz.nifeather.morph.client.graphics.color.MaterialColors;

import java.util.Map;

public class EntityRendererHelper
{
    private static final Logger log = LoggerFactory.getLogger(EntityRendererHelper.class);

    public EntityRendererHelper()
    {
        instance = this;
    }

    public static EntityRendererHelper instance;

    public static boolean doRenderRealName = false;

    private final int textColor = MaterialColors.Orange500.getColor();
    public final int textColorTransparent = ColorUtils.forOpacity(MaterialColors.Orange500, 0).getColor();

    @Nullable
    public final Map.Entry<Integer, String> getRevealNameEntry(Integer id)
    {
        return DisguiseInstanceTracker.getInstance().playerMap.entrySet().stream()
                .filter(set -> id.equals(set.getKey()))
                .findFirst().orElse(null);
    }

    public final void setupEntityState(Entity renderingEntity, IDisguiseRenderState renderState)
    {
        // Reset render state
        renderState.morphclient$setRevealName(null);
        renderState.morphclient$setMasterPosition(null);
        renderState.morphclient$setDisguiseSyncer(null);

        // then do setup

        int id = renderingEntity.getId();

        // client renderer
        if (renderingEntity instanceof IMorphClientEntity iMorphEntity)
        {
            // 如果被我们的Syncer标记为了伪装实体，那么从syncer读取主实体位置
            if (iMorphEntity.featherMorph$isDisguiseEntity())
            {
                var syncer = DisguiseInstanceTracker.getInstance().getSyncerFor(iMorphEntity.featherMorph$getMasterEntityId());

                if (syncer != null)
                {
                    var masterEntity = syncer.getBindingPlayer();
                    id = syncer.getBindingPlayer().getId();

                    renderState.morphclient$setDisguiseSyncer(syncer);
                    renderState.morphclient$setMasterPosition(masterEntity.position());
                }
            }
            else
            {
                // 否则，该实体的位置就是主实体的位置
                renderState.morphclient$setMasterPosition(renderingEntity.position());
            }
        }

        var entrySet = getRevealNameEntry(id);
        if (entrySet == null)
            return;

        String revealName = entrySet.getValue();
        var disguiseEntityName = renderingEntity.getName().getString();

        String text = "%s(%s)".formatted(disguiseEntityName, revealName);

        renderState.morphclient$setRevealName(text);
    }

    private final PoseStack renderPoseStack = new PoseStack();

    public final void submitRevealName(PoseStack ignored, EntityRenderState renderState,
                                       SubmitNodeCollector collector, CameraRenderState cameraRenderState)
    {
        if (!doRenderRealName) return;
        if (!(renderState instanceof IDisguiseRenderState disguiseRenderState))
            throw new RuntimeException("Given render state is not an instance of IDisguiseRenderState");

        // Don't render if reveal name is null
        var revealName = disguiseRenderState.morphclient$getRevealName();
        if (revealName == null)
            return;

        // apply rotation
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().camera;
        if (camera == null)
            throw new RuntimeException("Camera is NULL!");

        float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        if (!renderPoseStack.isEmpty())
            throw new RuntimeException("Non-empty PoseStack for submitRevealName");

        renderPoseStack.pushPose();

        Vec3 anchorPosition = ((IDisguiseRenderState) renderState).morphclient$masterPosition();
        var syncer = disguiseRenderState.morphclient$getDisguiseSyncer();
        if (syncer != null)
        {
            var bindingPlayer = syncer.getBindingPlayer();
            anchorPosition = Mth.lerp(tickDelta, bindingPlayer.oldPosition(), bindingPlayer.position());
        }

        // apply position relative to the camera
        // Nametag offset
        var labelOffset = getLabelOffset(renderState);

        var positionDiff = anchorPosition.subtract(camera.position()).add(labelOffset);
        renderPoseStack.translate(positionDiff);

        // If tag should scale on distance
        if (FeatherMorphClientBootstrap.getInstance().getModConfigData().scaleNameTag)
        {
            var distance = camera.position().distanceTo(anchorPosition);
            var scale = Math.max(1, (float)distance / 7.5f);
            renderPoseStack.scale(scale, scale, scale);
        }

        //System.out.println("Submit! For name " + revealName);
        collector.submitNameTag(renderPoseStack, Vec3.ZERO, 0, Component.literal(revealName).withColor(textColor), true, LightCoordsUtil.FULL_BRIGHT, cameraRenderState);
        renderPoseStack.popPose();
    }

    private static @NotNull Vec3 getLabelOffset(EntityRenderState renderState)
    {
        var labelOffset = renderState.nameTagAttachment != null
                          ? renderState.nameTagAttachment
                          : new Vec3(0, renderState.boundingBoxHeight, 0);

        if (renderState.nameTag != null)
            labelOffset = labelOffset.add(0, 0.25, 0);

        if (renderState instanceof AvatarRenderState avatarRenderState
                && avatarRenderState.scoreText != null
                && renderState.nameTagAttachment != null)
        {
            labelOffset = labelOffset.add(0, 9.0F * 1.15F * 0.025F, 0);
        }

        return labelOffset;
    }
}
