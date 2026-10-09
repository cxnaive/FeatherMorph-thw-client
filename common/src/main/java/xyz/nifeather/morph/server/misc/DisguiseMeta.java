package xyz.nifeather.morph.server.misc;

import net.minecraft.world.entity.EntityTypes;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.server.disguise.providers.AbstractDisguiseProvider;
import xyz.nifeather.morph.shared.EntityTypeLookup;

public class DisguiseMeta
{
    @SerializedName("Type")
    @Expose(serialize = false)
    @Deprecated
    //仅更新配置时使用
    public EntityType<?> type;

    private final EntityType<?> entityType;

    /**
     *
     * @return UNKNOWN if the identifier doesn't match any vanilla type
     */
    @ApiStatus.Internal
    public EntityType<?> getEntityType()
    {
        return entityType;
    }

    @NotNull
    public final String rawIdentifier;

    public String getIdentifier()
    {
        return rawIdentifier;
    }

    @NotNull
    private final DisguiseTypes disguiseType;

    @Nullable
    private final AbstractDisguiseProvider provider;

    @Nullable
    public AbstractDisguiseProvider getProvider()
    {
        return provider;
    }

    /**
     * 获取伪装类型（玩家、生物、LD或未知）
     *
     * @return 伪装类型
     */
    @NotNull
    public DisguiseTypes getDisguiseType()
    {
        return disguiseType;
    }

    public final boolean isPlayerDisguise()
    {
        return disguiseType == DisguiseTypes.PLAYER;
    }

    /**
     * 不带"player:"的玩家伪装名称
     */
    @Expose
    public String playerDisguiseTargetName;

    public DisguiseMeta(@NotNull String rawIdentifier, DisguiseTypes disguiseType, @Nullable AbstractDisguiseProvider matchingProvider)
    {
        this.rawIdentifier = rawIdentifier;
        this.disguiseType = disguiseType;

        this.provider = matchingProvider;

        switch (disguiseType)
        {
            case PLAYER ->
            {
                this.entityType = EntityTypes.PLAYER;
                this.playerDisguiseTargetName = disguiseType.toStrippedId(rawIdentifier);
            }

            case VANILLA -> this.entityType = EntityTypeLookup.byIdentifier(rawIdentifier).orElseThrow();
            default -> this.entityType = null;
        }
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof DisguiseMeta di)) return false;

        return this.equals(di.rawIdentifier);
    }

    public boolean equals(EntityType<?> type)
    {
        if (!this.isValid()) return false;

        return this.entityType.equals(type);
    }

    public boolean equals(String rawString)
    {
        return this.rawIdentifier.equals(rawString);
    }

    /**
     * SAN值检查
     * @return 是否通过
     */
    public boolean isValid()
    {
        return disguiseType != DisguiseTypes.UNKNOWN && entityType != null;
    }

    /**
     * 获取可用于存储的键名
     * @return 键名
     */
    public String getKey()
    {
        if (!this.isValid())
            return rawIdentifier;

        return rawIdentifier;
    }

    public Component asComponent()
    {
        return isValid()
                    ? provider == null
                        ? Component.literal(rawIdentifier)
                        : provider.getDisplayName(rawIdentifier)
                    : Component.literal(rawIdentifier);
    }

    /**
     * In case someone will need, but do they really need this?
     */
    public int objectHashCode()
    {
        return super.hashCode();
    }

    @Override
    public int hashCode()
    {
        return rawIdentifier.hashCode();
    }

    @Override
    public String toString()
    {
        return "DisguiseMeta[Type=" + this.entityType + ", DisguiseType=" + this.getDisguiseType() + ", targetPlayerName=" + this.playerDisguiseTargetName + "]";
    }
}
