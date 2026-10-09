package validation;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import xyz.nifeather.morph.client.DisguiseInstanceTracker;
import xyz.nifeather.morph.client.FeatherMorphClientBootstrap;
import xyz.nifeather.morph.client.entities.IDisguiseRenderState;
import xyz.nifeather.morph.client.entities.IMorphClientEntity;
import xyz.nifeather.morph.client.mixin.accessors.ShulkerAccessor;
import xyz.nifeather.morph.client.graphics.PlayerRenderHelper;
import xyz.nifeather.morph.client.screens.disguise.DisguiseScreen;
import xyz.nifeather.morph.client.screens.emote.EmoteScreen;
import xyz.nifeather.morph.client.screens.quickDisguise.QuickDisguiseScreen;
import xyz.nifeather.morph.client.syncers.ClientDisguiseSyncer;
import xyz.nifeather.morph.shared.EntityTypeLookup;
import xyz.nifeather.morph.client.properties.ClientProperty;
import xyz.nifeather.morph.client.properties.ClientPropertyHolder;
import xyz.nifeather.morph.client.syncers.DisguiseSyncer;
import xyz.nifeather.morph.network.commands.C2S.C2SRequestAnimationCommand;
import xyz.nifeather.morph.network.commands.S2C.S2CUpdatePropertiesCommand;
import xyz.nifeather.morph.network.commands.S2C.S2CDiscardPropertiesCommand;
import xyz.nifeather.morph.network.commands.S2C.S2CUpdateTemporaryPropertiesCommand;
import xyz.nifeather.morph.network.commands.S2C.S2CDiscardTemporaryPropertiesCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

/** A separate, local-only test mod. It is never included in the release JAR. */
public final class UpgradeClientProbe implements ClientModInitializer
{
    private final Gson gson = new Gson();
    private final Path directory = Path.of(System.getProperty("fm.probe.dir"));
    private boolean connecting;
    private boolean ready;
    private String lastId = "";
    private Action pending;
    private long actionStarted;

    private record Action(String id, String type, String value, int delayMs) {}

    @Override
    public void onInitializeClient()
    {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void record(String id, String status, String detail)
    {
        try
        {
            Files.writeString(directory.resolve("events.jsonl"), gson.toJson(Map.of(
                    "id", id, "status", status, "detail", detail)) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }

    private static EntityType<?> expectedType(String disguiseId)
    {
        return disguiseId.startsWith("player:") ? EntityTypes.PLAYER : EntityTypeLookup.byIdentifier(disguiseId).orElseThrow();
    }

    private void tick(Minecraft minecraft)
    {
        try
        {
            if (!minecraft.isGameLoadFinished()) return;
            if (!connecting)
            {
                connecting = true;
                require(EntityTypeLookup.byIdentifier("cow").orElseThrow() == EntityTypes.COW, "default namespace lookup");
                require(EntityTypeLookup.byIdentifier("minecraft:cow").orElseThrow() == EntityTypes.COW, "cow lookup");
                require(EntityTypeLookup.byIdentifier("minecraft:sulfur_cube").orElseThrow() == EntityTypes.SULFUR_CUBE, "sulfur cube lookup");
                require(EntityTypeLookup.byIdentifier("minecraft:missing_entity").isEmpty(), "unknown entity must stay absent");
                require(EntityTypeLookup.byIdentifier("INVALID ID").isEmpty(), "invalid entity must stay absent");
                record("registry", "PASS", "five registry lookups");
                minecraft.options.pauseOnLostFocus = false;
                minecraft.options.renderDistance().set(2);
                minecraft.options.simulationDistance().set(5);
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                String address = System.getProperty("fm.probe.address");
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Upgrade validation", address, ServerData.Type.OTHER), false, null);
                return;
            }

            var bootstrap = FeatherMorphClientBootstrap.getInstance();
            if (!ready && minecraft.player != null && bootstrap.serverHandler.serverReady())
            {
                int expectedApi = Integer.getInteger("fm.probe.api", 19);
                require(bootstrap.serverHandler.getReportingProtocol() == expectedApi, "client reporting API " + expectedApi);
                require(bootstrap.serverHandler.getServerVersion() == expectedApi, "server session API " + expectedApi);
                require(bootstrap.getModConfigData().protocolCompatibilityMode == (expectedApi == 17), "compatibility mode matches requested API");
                verifyPropertyHolder();
                verifyPropertyCache(minecraft);
                var vanillaShulker = EntityTypes.SHULKER.create(minecraft.level, EntitySpawnReason.COMMAND);
                vanillaShulker.tickCount = 1;
                ((ShulkerAccessor) vanillaShulker).callSetRawPeekAmount(100);
                require(rawPeek(vanillaShulker) == 100, "vanilla shulker peek setter");
                vanillaShulker.setPos(3, 3, 3);
                require(rawPeek(vanillaShulker) == 0, "vanilla shulker relocation still closes its shell");
                require(vanillaShulker.getRenderPosition(1) != null, "vanilla shulker teleport interpolation retained");
                ready = true;
                record("handshake", "PASS", minecraft.player.getName().getString() + " connected at API " + expectedApi
                        + "; temporary property precedence/cache and vanilla shulker relocation checked");
            }
            if (!ready) return;

            Path input = directory.resolve("action.json");
            if (pending == null && Files.exists(input))
            {
                Action action = gson.fromJson(Files.readString(input), Action.class);
                if (!action.id().equals(lastId))
                {
                    lastId = action.id();
                    pending = action;
                    actionStarted = System.currentTimeMillis();
                    start(minecraft, action);
                }
            }
            if (pending != null && System.currentTimeMillis() - actionStarted > (pending.delayMs() > 0 ? pending.delayMs() : 2500))
            {
                check(minecraft, pending);
                record(pending.id(), "PASS", pending.type() + " " + pending.value());
                pending = null;
            }
        }
        catch (Throwable t)
        {
            record(pending == null ? "startup" : pending.id(), "FAIL", t.toString());
            t.printStackTrace();
            minecraft.stop();
        }
    }

    private void start(Minecraft minecraft, Action action)
    {
        switch (action.type())
        {
            case "morph" -> minecraft.getConnection().sendCommand("morph " + action.value()
                    + (action.value().equals("minecraft:slime") || action.value().equals("minecraft:magma_cube")
                    ? " [slime_magma/size=3]" : ""));
            case "unmorph" -> minecraft.getConnection().sendCommand("unmorph");
            case "self" -> minecraft.getConnection().sendCommand("fm toggleself");
            case "screen" -> minecraft.gui.setScreen(switch (action.value()) {
                case "disguise" -> new DisguiseScreen();
                case "quick" -> new QuickDisguiseScreen();
                case "emote" -> new EmoteScreen();
                case "inventory" -> new InventoryScreen(minecraft.player);
                case "creative" -> new CreativeModeInventoryScreen(minecraft.player,
                        minecraft.player.connection.enabledFeatures(), false);
                case "config" -> FeatherMorphClientBootstrap.getInstance().getFactory(null).build();
                default -> null;
            });
            case "camera" -> minecraft.options.setCameraType(action.value().equals("first")
                    ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
            case "animation" -> FeatherMorphClientBootstrap.getInstance().serverHandler.sendCommand(
                    new C2SRequestAnimationCommand(gson.fromJson(action.value(), com.google.gson.JsonObject.class).get("name").getAsString()));
            case "legacy_animation" -> FeatherMorphClientBootstrap.getInstance().serverHandler.sendCommand(
                    new C2SRequestAnimationCommand(action.value()));
            case "rebuild" -> {
                var manager = FeatherMorphClientBootstrap.getInstance().morphManager;
                try {
                    var refresh = manager.getClass().getDeclaredMethod("refreshLocalSyncer", String.class);
                    refresh.setAccessible(true);
                    var syncer = ((Optional<DisguiseSyncer>) refresh.invoke(manager, manager.currentIdentifier.get())).orElseThrow();
                    var bind = manager.getClass().getDeclaredMethod("setBindingClientSyncer", DisguiseSyncer.class);
                    bind.setAccessible(true);
                    bind.invoke(manager, syncer);
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            }
            case "temporary", "equipment", "persistent" -> { }
            case "observe", "observe_native", "screenshot" -> { }
            case "stop" -> {
                record(action.id(), "PASS", "normal client shutdown");
                minecraft.stop();
            }
            default -> throw new IllegalArgumentException(action.type());
        }
    }

    private void check(Minecraft minecraft, Action action)
    {
        var bootstrap = FeatherMorphClientBootstrap.getInstance();
        if (action.type().equals("animation")) {
            var expected = gson.fromJson(action.value(), com.google.gson.JsonObject.class).getAsJsonObject("properties");
            verifyTemporary(minecraft, gson.fromJson(expected, Map.class));
        }
        if (action.type().equals("legacy_animation")) {
            var syncer = ClientDisguiseSyncer.getCurrentInstance();
            var entity = syncer.getDisguiseInstance();
            if (entity instanceof Cat cat)
                require(cat.isInSittingPose() == action.value().equals("sit"), "API 17 legacy cat animation applied");
            else if (entity instanceof Shulker shulker) {
                var height = action.value().equals("open") ? 100 : 30;
                require(rawPeek(shulker) == height, "API 17 legacy shulker peek target");
                require(Math.abs(shulker.getClientPeekAmount(1) - height / 100f) < 0.06f, "API 17 legacy shulker shell height");
                require(shulker.getRenderPosition(1) == null, "API 17 legacy shulker proxy render offset");
            } else throw new AssertionError("Untested legacy animation entity " + entity.getType());
            require(syncer.propertyHolder().getAllTemporary().isEmpty(), "legacy animation does not use API 19 temporary properties");
            require(bootstrap.morphManager.getTemporaryPropertiesFor(minecraft.player.getId()).isEmpty(), "legacy animation leaves temporary cache empty");
        }
        if (action.type().equals("temporary") || action.type().equals("rebuild"))
            verifyTemporary(minecraft, gson.fromJson(action.value(), Map.class));
        if (action.type().equals("equipment")) {
            var entity = (LivingEntity) ClientDisguiseSyncer.getCurrentInstance().getDisguiseInstance();
            require(entity.getItemBySlot(EquipmentSlot.BODY).is(Items.IRON_HORSE_ARMOR), "BODY equipment applied to disguise");
            require(entity.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE), "SADDLE equipment applied to disguise");
        }
        if (action.type().equals("persistent")) {
            var syncer = ClientDisguiseSyncer.getCurrentInstance();
            require(syncer.getDisguiseInstance().isCustomNameVisible() == Boolean.parseBoolean(action.value()), "persistent property/default applied");
            require(syncer.propertyHolder().contains("entity/custom_name_visible") == Boolean.parseBoolean(action.value()), "persistent discard removes stored property");
        }
        if (action.type().equals("morph"))
        {
            require(action.value().equals(bootstrap.morphManager.currentIdentifier.get()), "current disguise identifier");
            var syncer = ClientDisguiseSyncer.getCurrentInstance();
            require(syncer != null && !syncer.disposed(), "local syncer");
            require(syncer.getDisguiseInstance().getType() == expectedType(action.value()), "local entity type");
            if (action.value().equals("minecraft:slime") || action.value().equals("minecraft:magma_cube"))
                require(((AbstractCubeMob) syncer.getDisguiseInstance()).getSize() == 3, "cube size 3");
            var state = minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player, 1);
            require(state instanceof IDisguiseRenderState, "render state mixin");
            require(((IDisguiseRenderState) state).morphclient$getDisguiseSyncer() == syncer,
                    "player renderer uses the disguise: state=" + state.getClass().getSimpleName()
                            + ", skip=" + PlayerRenderHelper.instance().skipRender
                            + ", bypass=" + ((IMorphClientEntity) minecraft.player).featherMorph$bypassesDispatcherRedirect()
                            + ", mapped=" + (DisguiseInstanceTracker.getInstance().getSyncerFor(minecraft.player) == syncer)
                            + ", marked=" + ((IMorphClientEntity) syncer.getDisguiseInstance()).featherMorph$isDisguiseEntity());
        }
        if (action.type().equals("unmorph"))
            require(bootstrap.morphManager.currentIdentifier.get() == null
                    || bootstrap.morphManager.currentIdentifier.get().equals(FeatherMorphClientBootstrap.UNMORPH_STIRNG), "unmorph state");
        if (action.type().equals("observe"))
        {
            var player = minecraft.level.players().stream()
                    .filter(p -> p.getName().getString().equals("MorphActor")).findFirst().orElseThrow();
            var syncer = DisguiseInstanceTracker.getInstance().getSyncerFor(player);
            require(syncer != null && !syncer.disposed(), "observer syncer");
            require(syncer.getDisguiseInstance().getType() == expectedType(action.value()), "observer entity type");
            var state = minecraft.getEntityRenderDispatcher().extractEntity(player, 1);
            require(((IDisguiseRenderState) state).morphclient$getDisguiseSyncer() == syncer, "observer renders the disguise");
        }
        if (action.type().equals("observe_native"))
        {
            var expected = expectedType(action.value());
            Entity observed = null;
            for (var entity : minecraft.level.entitiesForRendering())
            {
                if (entity != minecraft.player && !((IMorphClientEntity) entity).featherMorph$isDisguiseEntity() && entity.getType() == expected
                        && entity.position().distanceToSqr(0.5, -60, 0.5) < 4)
                {
                    observed = entity;
                    break;
                }
            }
            require(observed != null, "server-rendered disguise entity");
            require(minecraft.getEntityRenderDispatcher().extractEntity(observed, 1) instanceof IDisguiseRenderState,
                    "server-rendered entity render state");
            if (observed instanceof AbstractCubeMob cube && (expected == EntityTypes.SLIME || expected == EntityTypes.MAGMA_CUBE))
                require(cube.getSize() == 3, "native metadata sets cube size 3");
        }
        if (action.type().equals("self"))
            require(bootstrap.morphManager.selfVisibleEnabled.get() == Boolean.parseBoolean(action.value()), "self visibility");
        if (action.type().equals("screen"))
        {
            var screen = minecraft.gui.screen();
            require(switch (action.value()) {
                case "disguise" -> screen instanceof DisguiseScreen;
                case "quick" -> screen instanceof QuickDisguiseScreen;
                case "emote" -> screen instanceof EmoteScreen;
                case "inventory" -> screen instanceof InventoryScreen;
                case "creative" -> screen instanceof CreativeModeInventoryScreen;
                case "config" -> screen != null && screen.getClass().getName().startsWith("me.shedaniel.clothconfig2.");
                default -> screen == null;
            }, "screen transition: " + screen);
        }
        if (action.type().equals("screenshot") || action.type().equals("screen")) Screenshot.grab(minecraft, false);
    }

    private static void verifyPropertyHolder()
    {
        var holder = new ClientPropertyHolder();
        var property = ClientProperty.builder("validation/flag", false, Entity.class).build();
        holder.addProperty(property);
        holder.set(property, false);
        holder.setTemp(property, false);
        require(holder.getAllTemporary().containsKey(property), "equal temporary value is stored");
        holder.set(property, false);
        require(holder.getAllTemporary().isEmpty(), "equal persistent value clears temporary override");
        holder.setTemp(property, true);
        holder.set(property, true);
        holder.discardTemporaryProperty(property);
        require(holder.get(property), "equal effective value becomes persistent");
        holder.setTemp(property, false);
        holder.discardTemporaryProperty(property);
        require(holder.get(property), "temporary discard restores persistent value");
        holder.discardProperty(property);
        require(!holder.contains(property) && !holder.get(property), "persistent discard restores default");
    }

    private static void verifyPropertyCache(Minecraft minecraft)
    {
        int id = minecraft.player.getId();
        require(DisguiseInstanceTracker.getInstance().getSyncerFor(id) == null, "cache regression needs no disguise syncer");
        var bootstrap = FeatherMorphClientBootstrap.getInstance();
        var manager = bootstrap.morphManager;
        var handler = bootstrap.serverHandler;
        String name = "validation/flag";
        handler.onUpdateTemporaryPropertiesCommand(new S2CUpdateTemporaryPropertiesCommand(Map.of(name, "true")));
        require(manager.getTemporaryPropertiesFor(id).containsKey(name), "temporary update cached without syncer");
        handler.onUpdatePropertiesCommand(new S2CUpdatePropertiesCommand(Map.of(name, "false")));
        require("false".equals(manager.getNetworkPropertiesFor(id).get(name)), "persistent update cached without syncer");
        require(!manager.getTemporaryPropertiesFor(id).containsKey(name), "persistent update clears cached temporary override");
        handler.onUpdateTemporaryPropertiesCommand(new S2CUpdateTemporaryPropertiesCommand(Map.of(name, "true")));
        handler.onDiscardPropertiesCommand(new S2CDiscardPropertiesCommand(List.of(name)));
        require(!manager.getNetworkPropertiesFor(id).containsKey(name), "persistent discard cached without syncer");
        require(!manager.getTemporaryPropertiesFor(id).containsKey(name), "persistent discard clears temporary cache");
        handler.onUpdateTemporaryPropertiesCommand(new S2CUpdateTemporaryPropertiesCommand(Map.of(name, "true")));
        handler.onDiscardTemporaryPropertiesCommand(new S2CDiscardTemporaryPropertiesCommand(List.of(name)));
        require(!manager.getTemporaryPropertiesFor(id).containsKey(name), "temporary discard cached without syncer");
    }

    private static int rawPeek(Shulker shulker)
    {
        try {
            var readPeek = Shulker.class.getDeclaredMethod("getRawPeekAmount");
            readPeek.setAccessible(true);
            return (int) readPeek.invoke(shulker);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private static void verifyTemporary(Minecraft minecraft, Map<String, String> expected)
    {
        var encoded = new HashMap<>(expected);
        if (encoded.containsKey("entity/static_pose"))
            encoded.put("entity/static_pose", Integer.toString(Pose.valueOf(encoded.get("entity/static_pose")).ordinal()));
        var syncer = ClientDisguiseSyncer.getCurrentInstance();
        require(syncer != null, "animation syncer");
        var holder = syncer.propertyHolder();
        var names = holder.getAllTemporary().keySet().stream().map(ClientProperty::identifier).collect(Collectors.toSet());
        require(names.equals(expected.keySet()), "temporary property names: " + names + " expected " + expected.keySet());
        encoded.forEach((name, input) -> {
            var property = (ClientProperty<Object, Entity>) holder.getProperty(name);
            require(property != null, "registered animation property " + name);
            require(property.handleInput(input).orElseThrow().equals(holder.get(property)), "temporary value " + name);
        });
        var entity = syncer.getDisguiseInstance();
        if (expected.containsKey("entity/static_pose"))
            require(entity.getPose().name().equals(expected.get("entity/static_pose")), "actual entity pose");
        else if (!holder.contains("entity/static_pose"))
            require(entity.getPose() == minecraft.player.getPose(), "discarded pose restores the player pose");
        if (expected.containsKey("living_entity/invisible"))
            require(entity.isInvisible() == Boolean.parseBoolean(expected.get("living_entity/invisible")), "actual invisibility override");
        else if (!holder.contains("living_entity/invisible"))
            require(entity.isInvisible() == minecraft.player.isInvisible(), "discarded invisibility restores player visibility");
        if (entity instanceof LivingEntity living) {
            if (expected.containsKey("living_entity/bed_pos"))
                require(living.getSleepingPos().isPresent(), "sleeping animation sets the bed position");
            else if (!holder.contains("living_entity/bed_pos"))
                require(living.getSleepingPos().isEmpty(), "discarded bed position leaves no sleeping state");
        }
        if (entity instanceof Cat cat)
        {
            require(cat.isInSittingPose() == Boolean.parseBoolean(expected.getOrDefault("cat/sitting", "false")), "actual cat sitting pose");
            require(cat.isLying() == Boolean.parseBoolean(expected.getOrDefault("cat/lying", "false")), "actual cat lying pose");
        }
        if (entity instanceof Wolf wolf)
            require(wolf.isInSittingPose() == Boolean.parseBoolean(expected.getOrDefault("wolf/sitting", "false")), "actual wolf sitting pose");
        if (entity instanceof Fox fox) {
            var status = expected.getOrDefault("fox/status", "STANDING");
            require(fox.isSitting() == status.equals("SITTING") && fox.isSleeping() == status.equals("SLEEPING"), "actual fox status");
        }
        if (entity instanceof Panda panda)
            require(panda.isSitting() == Boolean.parseBoolean(expected.getOrDefault("panda/sitting", "false")), "actual panda sitting pose");
        if (entity instanceof Allay allay)
            require(allay.isDancing() == Boolean.parseBoolean(expected.getOrDefault("allay/dancing", "false")), "actual allay dancing");
        if (entity instanceof Piglin piglin)
            require(piglin.isDancing() == Boolean.parseBoolean(expected.getOrDefault("piglin/dancing", "false")), "actual piglin dancing");
        if (entity instanceof Pufferfish pufferfish)
            require(pufferfish.getPuffState() == (expected.containsKey("pufferfish/puff_state") ? Pufferfish.STATE_FULL : Pufferfish.STATE_SMALL), "actual pufferfish puff state");
        if (entity instanceof Creaking creaking)
            require(creaking.isActive() == Boolean.parseBoolean(expected.getOrDefault("creaking/eyes_glowing", "false")), "actual creaking eye glow");
        if (entity instanceof Armadillo armadillo)
            require(armadillo.getState().name().equals(expected.getOrDefault("armadillo/state", "IDLE")), "actual armadillo state");
        if (entity instanceof Sniffer sniffer)
            require(sniffer.sniffingAnimationState.isStarted() == expected.containsKey("sniffer/state"), "actual sniffer animation state");
        if (entity instanceof Shulker shulker) {
            require(shulker.getRenderPosition(1) == null, "disguise shulker has no proxy teleport render offset");
            var height = Integer.parseInt(expected.getOrDefault("shulker/shell_height", "0"));
            require(rawPeek(shulker) == height, "actual shulker peek target " + rawPeek(shulker) + " expected " + height);
            var actual = shulker.getClientPeekAmount(1);
            require(Math.abs(actual - height / 100f) < 0.06f, "actual shulker shell height " + actual + " expected " + height / 100f);
        }
        require(FeatherMorphClientBootstrap.getInstance().morphManager.getTemporaryPropertiesFor(minecraft.player.getId()).equals(encoded), "temporary network cache");
    }
}
