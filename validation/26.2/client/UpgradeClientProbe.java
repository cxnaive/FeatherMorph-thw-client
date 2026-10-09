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
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import xyz.nifeather.morph.client.DisguiseInstanceTracker;
import xyz.nifeather.morph.client.FeatherMorphClientBootstrap;
import xyz.nifeather.morph.client.entities.IDisguiseRenderState;
import xyz.nifeather.morph.client.entities.IMorphClientEntity;
import xyz.nifeather.morph.client.graphics.PlayerRenderHelper;
import xyz.nifeather.morph.client.screens.disguise.DisguiseScreen;
import xyz.nifeather.morph.client.screens.emote.EmoteScreen;
import xyz.nifeather.morph.client.screens.quickDisguise.QuickDisguiseScreen;
import xyz.nifeather.morph.client.syncers.ClientDisguiseSyncer;
import xyz.nifeather.morph.shared.EntityTypeLookup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

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

    private record Action(String id, String type, String value) {}

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
                require(bootstrap.serverHandler.getReportingProtocol() == 17, "client must report API 17 by default");
                require(bootstrap.serverHandler.getServerVersion() == 17, "server API 17");
                ready = true;
                record("handshake", "PASS", minecraft.player.getName().getString() + " connected at API 17");
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
            if (pending != null && System.currentTimeMillis() - actionStarted > 2500)
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
}
