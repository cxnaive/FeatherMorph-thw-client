import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

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
