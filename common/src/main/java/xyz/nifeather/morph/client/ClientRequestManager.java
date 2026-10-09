package xyz.nifeather.morph.client;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import xiamomc.pluginbase.Annotations.Initializer;
import xyz.nifeather.morph.client.graphics.toasts.RequestToast;
import xyz.nifeather.morph.network.commands.S2C.S2CUpdateRequestStatusCommand;

import java.util.List;

public class ClientRequestManager extends MorphClientObject
{
    public void addRequest(S2CUpdateRequestStatusCommand.Type type, String sourceName)
    {
        if (type == S2CUpdateRequestStatusCommand.Type.Unknown) return;

        Minecraft.getInstance().gui.toastManager().addToast(new RequestToast(type, sourceName));
        requests.add(new Request(plugin.getCurrentTick(), type, sourceName));
    }

    public void scheduleRemoveRequest(String sourceName)
    {
        this.addSchedule(() -> requests.removeIf(r -> r.sourceName.equalsIgnoreCase(sourceName)));
    }

    public List<Request> getRequests()
    {
        return new ObjectArrayList<>(requests);
    }

    @Initializer
    private void load()
    {
        addSchedule(this::update);
    }

    private final List<Request> requests = new ObjectArrayList<>();

    private void update()
    {
        var currentTime = plugin.getCurrentTick();
        requests.removeIf(r -> currentTime - r.beginTime > 90 * 20);
    }

    private record Request(long beginTime, S2CUpdateRequestStatusCommand.Type type, String sourceName)
    {
    }
}