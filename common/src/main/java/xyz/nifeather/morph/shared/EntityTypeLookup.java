package xyz.nifeather.morph.shared;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

public final class EntityTypeLookup
{
    private EntityTypeLookup()
    {
    }

    public static Optional<EntityType<?>> byIdentifier(String identifier)
    {
        var parsed = Identifier.tryParse(identifier);
        return parsed == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(parsed);
    }
}
