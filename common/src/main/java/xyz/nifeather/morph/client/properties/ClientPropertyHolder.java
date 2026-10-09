package xyz.nifeather.morph.client.properties;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.client.utilties.actions.BiConsumerActions;
import xyz.nifeather.morph.client.utilties.actions.ConsumerActions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ClientPropertyHolder
{
    private final Map<String, ClientProperty<?, ?>> validProperties = new ConcurrentHashMap<>();

    public ClientProperty<?, ?> getProperty(String propertyName)
    {
        return validProperties.getOrDefault(propertyName, null);
    }

    private final Map<ClientProperty<?, ?>, Object> persistProperties = new ConcurrentHashMap<>();
    private final Map<ClientProperty<?, ?>, Object> tempProperties =  new ConcurrentHashMap<>();

    //region hooks

    protected final BiConsumerActions<ClientProperty<?, ?>, Object> hooksOnTemporaryPropertyWrite = new BiConsumerActions<>();
    public <X> void hookOnTemporaryPropertyWrite(BiConsumer<ClientProperty<X, ?>, X> consumer)
    {
        hooksOnTemporaryPropertyWrite.hook((BiConsumer) consumer);
    }

    protected final ConsumerActions<ClientProperty<?, ?>> hooksOnTemporaryPropertyDiscard = new ConsumerActions<>();
    public <X> void hookOnTemporaryPropertyDiscard(Consumer<ClientProperty<X, ?>> consumer)
    {
        hooksOnTemporaryPropertyDiscard.hook((Consumer) consumer);
    }

    protected final List<IValueChangeListener<ClientProperty<?, ?>, Object>> hooksOnPropertyWrite = new ObjectArrayList<>();
    public <X> void hookOnPropertyWrite(IValueChangeListener<ClientProperty<X, ?>, X> consumer)
    {
        hooksOnPropertyWrite.add((IValueChangeListener) consumer);
    }

    protected final ConsumerActions<ClientProperty<?, ?>> discardHooks = new ConsumerActions<>();
    public <X> void hookOnPropertyDiscard(Consumer<ClientProperty<X, ?>> consumer)
    {
        discardHooks.hook((Consumer) consumer);
    }

    //endregion hooks

    private Map<String, String> generateNetworkPropertiesFrom(Map<ClientProperty<?, ?>, Object> values)
    {
        Map<String, String> map = new ConcurrentHashMap<>();

        try
        {
            for (Map.Entry<ClientProperty<?, ?>, Object> entry : values.entrySet())
            {
                var property = (ClientProperty<Object, Entity>) entry.getKey();

                // Skip properties that's not visible to client
                // if (property.hideFromClient())
                //    continue;

                var value = entry.getValue();

                property.handleOutput(value).ifPresent(s -> map.put(property.identifier(), s));
            }
        }
        catch (Exception ignored)
        {
        }

        return map;
    }

    /**
     * Creates network map for temp properties
     */
    public Map<String, String> serializeTemporaryProperties()
    {
        return generateNetworkPropertiesFrom(tempProperties);
    }

    /**
     * Creates network map for non-temp properties
     */
    public Map<String, String> serializeNonTempProperties()
    {
        return generateNetworkPropertiesFrom(persistProperties);
    }

    public void registerFromPropertyCollection(AbstractPropertyCollection properties)
    {
        validProperties.putAll(properties.getRegisteredProperties());
    }

    /**
     * Add property as a valid property for this handler
     * Kept for external use, so that if anyone wants to add their own property, they can call this method!
     */
    public void addProperty(ClientProperty<?, ?> property)
    {
        validProperties.put(property.identifier(), property);
    }

    public Map<ClientProperty<?, ?>, Object> deserializeProperties(Map<String, String> input)
    {
        var parsedResults = new ConcurrentHashMap<ClientProperty<?, ?>, Object>();
        //var propertiesToRemove = new ObjectArrayList<ClientProperty<?, ?>>();

        for (Map.Entry<String, String> entry : input.entrySet())
        {
            var key = entry.getKey();
            var value = entry.getValue();

            var property = (ClientProperty<Object, Entity>) this.validProperties.getOrDefault(key, null);
            if (property == null)
                continue;

            /*if (value.equals("!"))
            {
                propertiesToRemove.add(property);
                continue;
            }*/

            var val = property.handleInput(value).orElse(null);
            if (val == null) continue;

            //property.validateInput(val, inputSource, validationSkipFlags);
            parsedResults.put(property, val);
            //this.writeGeneric(property, val);
        }

        return parsedResults;

        //propertiesToRemove.forEach(this::discardProperty);

        /*
        for (Map.Entry<ClientProperty<?, ?>, Object> entry : parsedResults.entrySet())
        {
            var property = (ClientProperty<Object, Entity>) entry.getKey();
            var value = entry.getValue();

            property.postProcessHandle().handle(value, this);
        }*/
    }

    public Map<ClientProperty<?, ?>, Object> updateFromPropertiesInput(Map<String, String> input)
    {
        var result = deserializeProperties(input);
        result.forEach(this::writeGeneric);
        return result;
    }

    /**
     * Discard the temporary property.
     */
    public void discardTemporaryProperty(ClientProperty<?, ?> property)
    {
        var existing = tempProperties.remove(property);
        if (existing == null) return;

        hooksOnTemporaryPropertyDiscard.invoke(property);
    }

    /**
     * Discard the property, remove its value from this PropertyHandler
     */
    public void discardProperty(ClientProperty<?, ?> property)
    {
        var existingValue = persistProperties.remove(property);
        var existingTemp = tempProperties.remove(property);

        if (existingValue == null && existingTemp == null) // It doesn't even exist, don't trigger the action.
            return;

        discardHooks.invoke(property);
    }

    public void reset()
    {
        clearProperties();
    }

    public void clearProperties()
    {
        persistProperties.clear();
        tempProperties.clear();
    }

    private void writeGeneric(ClientProperty<?, ?> property, Object value)
    {
        if (!property.type().isInstance(value))
            throw new IllegalArgumentException("Incompatible value for id '%s', excepted for '%s', but got '%s'".formatted(property.identifier(), property.type(), value.getClass()));

        set((ClientProperty<Object, Entity>)property, value);
    }

    /**
     * @throws NullPointerException If the given value is NULL
     */
    public <X> void set(ClientProperty<X, ?> property, @NotNull X value) throws NullPointerException
    {
        /*if (!validProperties.containsKey(property.identifier()))
        {
            FeatherMorphClientBootstrap.getInstance().getSLF4JLogger().warn("The given property '%s' is not registered in propertyHandler".formatted(property.identifier()));
            return;
        }*/

        Objects.requireNonNull(value, "Null values are not accepted");

        var existing = getOptional(property).orElse(null);

        var hadTemporaryValue = tempProperties.remove(property) != null;
        persistProperties.put(property, value);
        if (!value.equals(existing) || hadTemporaryValue)
        {
            this.hooksOnPropertyWrite.forEach(l -> l.invoke(property, existing, value));
            //this.hooksOnPropertyWrite.invoke(BiConsumerActions.pair(property, diffIfPossible));
        }
    }

    public <X> void setTemp(ClientProperty<X, ?> property, @NotNull X value) throws NullPointerException
    {
        /*if (!validProperties.containsKey(property.identifier()))
        {
            FeatherMorphClientBootstrap.getInstance().getSLF4JLogger().warn("The given property '%s' is not registered in propertyHandler".formatted(property.id()));
            return;
        }*/

        Objects.requireNonNull(value, "Null values are not accepted");

        var existing = getOptional(property).orElse(null);

        tempProperties.put(property, value);
        if (!value.equals(existing))
        {
            X diffIfPossible;
            if (existing instanceof ISupportDiffs<?> existingDiff)
                diffIfPossible = ((ISupportDiffs<X>)existingDiff).diff(value);
            else
                diffIfPossible = value;

            // `setTemp` only has this differ from `set`... Maybe consider merge these two methods?
            this.hooksOnTemporaryPropertyWrite.invoke(BiConsumerActions.pair(property, diffIfPossible));
        }
    }

    public boolean contains(ClientProperty<?, ?> property)
    {
        return persistProperties.containsKey(property) || tempProperties.containsKey(property);
    }

    public boolean contains(String propertyName)
    {
        var combinedStream = Stream.concat(persistProperties.keySet().stream(), tempProperties.keySet().stream());
        return combinedStream.anyMatch(sp -> sp.identifier().equals(propertyName));
    }

    @NotNull
    public <X> X get(ClientProperty<X, ?> property)
    {
        return this.getOr(property, property.defaultValue());
    }

    public <X> Optional<X> getOptional(ClientProperty<X, ?> property)
    {
        return Optional.ofNullable(getOr(property, null));
    }

    @Nullable
    @Contract("_, null -> _; _, !null -> !null")
    public <X> X getOr(ClientProperty<X, ?> property, X defaultVal)
    {
        var temp = tempProperties.getOrDefault(property, null);
        if (temp != null)
            return (X) temp;

        return (X) persistProperties.getOrDefault(property, defaultVal);
    }

    @Nullable
    @Contract("_, null -> _; _, !null -> !null")
    public <X> X getOr(String propertyName, @Nullable X defaultVal)
    {
        var property = validProperties.getOrDefault(propertyName, null);
        if (property == null) return defaultVal;

        return (X) getOr((ClientProperty<Object, Entity>) property, defaultVal);
    }

    public Map<ClientProperty<?, ?>, ?> getAllTemporary()
    {
        return new Object2ObjectArrayMap<>(tempProperties);
    }

    public Map<ClientProperty<?, ?>, ?> getAll()
    {
        return new Object2ObjectArrayMap<>(persistProperties);
    }

    /**
     * Execute a simple copy which copies our value to the given PropertyHandler
     */
    public void copyTo(ClientPropertyHolder other)
    {
        other.validProperties.putAll(this.validProperties);
        this.persistProperties.forEach((k, v) -> other.set((ClientProperty<Object, Entity>) k, v));
        this.tempProperties.forEach((k, v) -> other.setTemp((ClientProperty<Object, Entity>) k, v));
    }

    public void dispose()
    {
        hooksOnPropertyWrite.clear();
    }
}
