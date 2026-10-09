import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.Map;

/** Console-only probe, installed exclusively in the temporary test server. */
public final class BackendProbe extends JavaPlugin
{
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof ConsoleCommandSender)) return true;
        try
        {
            var plugin = Bukkit.getPluginManager().getPlugin("FeatherMorph");
            var loader = plugin.getClass().getClassLoader();
            var api = Class.forName("xyz.nifeather.morph.api.FeatherMorphAPI", true, loader)
                    .getMethod("instance").invoke(null);
            var direct = api.getClass().getMethod("directAccess").invoke(api);
            var manager = direct.getClass().getMethod("morphManager").invoke(direct);
            if (command.getName().equals("fmprotocolprobe")) {
                if (args.length != 2) throw new IllegalArgumentException("player and fixture required");
                var player = Bukkit.getPlayerExact(args[0]);
                player.getScheduler().run(this, task -> {
                    try {
                        var state = manager.getClass().getMethod("getDisguiseStateFor", Player.class).invoke(manager, player);
                        if (args[1].equals("equipment")) {
                            var equipmentClass = Class.forName("xyz.nifeather.morph.misc.DisguiseEquipment", true, loader);
                            var equipment = equipmentClass.getConstructor(Map.class).newInstance(Map.of(
                                    EquipmentSlot.BODY, new ItemStack(Material.IRON_HORSE_ARMOR),
                                    EquipmentSlot.SADDLE, new ItemStack(Material.SADDLE)));
                            state.getClass().getMethod("setEquipment", equipmentClass).invoke(state, equipment);
                            state.getClass().getMethod("setShowingDisguisedEquipment", boolean.class).invoke(state, true);
                        } else if (args[1].equals("refresh")) {
                            manager.getClass().getMethod("refreshClientState", state.getClass()).invoke(manager, state);
                        } else {
                            var registryClass = Class.forName("xyz.nifeather.morph.misc.disguiseProperty.DisguiseProperties", true, loader);
                            var registry = registryClass.getField("INSTANCE").get(null);
                            var property = registryClass.getMethod("lookup", String.class).invoke(registry, "entity/custom_name_visible");
                            var properties = state.getClass().getMethod("disguisePropertyHandler").invoke(state);
                            var propertyClass = Class.forName("xyz.nifeather.morph.misc.disguiseProperty.SingleProperty", true, loader);
                            if (args[1].equals("discard"))
                                properties.getClass().getMethod("discardProperty", propertyClass).invoke(properties, property);
                            else if (args[1].equals("property"))
                                properties.getClass().getMethod("set", propertyClass, Object.class).invoke(properties, property, true);
                            else throw new AssertionError("Unknown fixture " + args[1]);
                        }
                        getLogger().info("FM_CLIENT_PROTOCOL_PASS " + args[1]);
                    } catch (Throwable t) {
                        getLogger().severe("FM_CLIENT_BACKEND_FAIL " + t);
                        t.printStackTrace();
                    }
                }, null);
                return true;
            }
            var backend = manager.getClass().getMethod("getBackend", String.class).invoke(manager, "client");
            var backendClass = Class.forName("xyz.nifeather.morph.backends.DisguiseBackend", true, loader);
            var result = manager.getClass().getMethod("switchBackend", backendClass).invoke(manager, backend);
            if (!Boolean.TRUE.equals(result)) throw new AssertionError("Client backend switch failed");
            getLogger().info("FM_CLIENT_BACKEND_PASS");
        }
        catch (Throwable t)
        {
            getLogger().severe("FM_CLIENT_BACKEND_FAIL " + t);
            t.printStackTrace();
        }
        return true;
    }
}
