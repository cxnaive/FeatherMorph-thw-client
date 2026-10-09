package xyz.nifeather.morph.client.graphics;

import net.minecraft.world.entity.EntityTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import xyz.nifeather.morph.client.Vec3dUtils;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class ModelWorkarounds
{
    private static ModelWorkarounds instance;

    public static ModelWorkarounds getInstance()
    {
        if (instance == null) instance = new ModelWorkarounds();

        return instance;
    }

    private final Map<Identifier, ModelPartConsumer<ModelPart, ModelPart>> workarounds = new Object2ObjectOpenHashMap<>();

    private void addWorkaround(EntityType<?> modelType, ModelPartConsumer<ModelPart, ModelPart> consumer)
    {
        workarounds.put(EntityType.getKey(modelType), consumer);
    }

    private void addWorkaround(List<EntityType<?>> types, ModelPartConsumer<ModelPart, ModelPart> consumer)
    {
        types.forEach(t -> addWorkaround(t, consumer));
    }

    public ModelWorkarounds()
    {
        initWorkarounds();
    }

    private interface ModelPartConsumer<L, R>
    {
        @NotNull
        WorkaroundMeta accept(L l, R r);
    }

    public void initWorkarounds()
    {
        LoggerFactory.getLogger("morph").info("Initializing arm render workarounds");
        workarounds.clear();

        //No-op
        addWorkaround(List.of(EntityTypes.WARDEN, EntityTypes.VILLAGER, EntityTypes.SNOW_GOLEM), (l, r) ->
                WorkaroundMeta.of(Vec3.ZERO, Vec3dUtils.ONE()));

        addWorkaround(List.of(EntityTypes.HOGLIN, EntityTypes.ZOGLIN), (l, r) ->
                WorkaroundMeta.of(new Vec3(0, -0.57f, 0.8f), Vec3dUtils.ONE()));

        addWorkaround(List.of(EntityTypes.ZOMBIE_HORSE, EntityTypes.SKELETON_HORSE, EntityTypes.HORSE), (l, r) ->
                WorkaroundMeta.of(new Vec3(0, -0.45f, 1f), Vec3dUtils.ONE()));

        addWorkaround(EntityTypes.POLAR_BEAR, (l, r) ->
                WorkaroundMeta.of(new Vec3(0, -0.57f, 0.65f), Vec3dUtils.ONE()));

        addWorkaround(EntityTypes.CREEPER, (l, r) ->
                WorkaroundMeta.of(new Vec3(0, -0.57f, 0.5f), Vec3dUtils.ONE()));

        addWorkaround(EntityTypes.IRON_GOLEM, (l, r) ->
                WorkaroundMeta.of(Vec3dUtils.of(0, -0.2, 0), Vec3dUtils.of(.75)));

        addWorkaround(List.of(EntityTypes.ALLAY, EntityTypes.VEX), (l, r) ->
        {
            l.zRot = r.zRot = 0;
            return new WorkaroundMeta(Vec3dUtils.of(0, .25, .1), Vec3dUtils.of(1.5));
        });

        addWorkaround(EntityTypes.BLAZE, (l, r) ->
                WorkaroundMeta.of(Vec3dUtils.of(0, -0.1, 0.2), Vec3dUtils.ONE()));

        addWorkaround(List.of(EntityTypes.CAMEL, EntityTypes.SNIFFER), (l, r) ->
                WorkaroundMeta.of(new Vec3(0, -0.6, 0.7), Vec3dUtils.ONE()));

        addWorkaround(EntityTypes.VILLAGER, (l, r) ->
                WorkaroundMeta.of(new Vec3(0, -0.5, 0.3), Vec3dUtils.ONE()));

        addWorkaround(EntityTypes.DONKEY, (l, r) ->
                WorkaroundMeta.of(Vec3dUtils.of(0, -0.5f, 0.6f), Vec3dUtils.ONE()));

        addWorkaround(EntityTypes.ENDER_DRAGON, (l, r) ->
        {
            //0.55f
            l.yRot = -0.6f;
            r.yRot = -l.yRot;

            return new WorkaroundMeta(Vec3dUtils.of(0, -3.2, 0.6f), Vec3dUtils.of(.6));
        });

    }

    private WorkaroundMeta defaultMeta()
    {
        return new WorkaroundMeta(new Vec3(0, -0.6f, 0.45f), Vec3dUtils.ONE());
    }

    /**
     * 通过传入的类型获取对应的{@link WorkaroundMeta}
     * @param entityType 实体类型
     * @param left 左手模型
     * @param right 右手模型
     * @return {@link WorkaroundMeta}
     */
    public WorkaroundMeta apply(EntityType<?> entityType, ModelPart left, ModelPart right)
    {
        var workaround = workarounds.get(EntityType.getKey(entityType));

        return workaround == null ? defaultMeta() : workaround.accept(left, right);
    }

    public record WorkaroundMeta(Vec3 offset, Vec3 scale)
    {
        public WorkaroundMeta(@NotNull Vec3 offset, @NotNull Vec3 scale)
        {
            this.offset = offset;
            this.scale = scale;
        }

        public static WorkaroundMeta of(Vec3 offset, Vec3 scale)
        {
            return new WorkaroundMeta(offset, scale);
        }
    }
}
