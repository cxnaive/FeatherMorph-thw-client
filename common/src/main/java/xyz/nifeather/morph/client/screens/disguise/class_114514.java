package xyz.nifeather.morph.client.screens.disguise;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class class_114514
{
    private final Screen parentScreen;

    @Nullable
    private String last;

    public class_114514(Screen parent)
    {
        this.parentScreen = parent;
    }

    public void apply(String str)
    {
        if (!str.equals(last))
            last = str;
        else
            return;

        str = str.replaceFirst("!", "");

        var client = Minecraft.getInstance();
        var currentLocale = client
                .getLanguageManager()
                .getSelected()
                .toLowerCase();

        boolean isChinese = currentLocale.startsWith("zh") || currentLocale.equals("lzh");

        switch (str)
        {
            case "owc" -> this.open(isChinese,
                    "https://www.bilibili.com/video/BV19vzfYhEmc",
                    "https://www.youtube.com/watch?v=r459I7A-Rds",
                    Component.translatable("url.morphclient.owc2024"));

            case "osu", "circles" -> this.open(isChinese,
                    "https://www.bilibili.com/video/BV1z9zDYjEc7",
                    "https://www.youtube.com/watch?v=fu0KoihoeA8",
                    Component.translatable("url.morphclient.circles", client.player.getName()));

            // 需要更好的文本描述
            /*
            case "starrail", "nameless" -> this.open(isChinese,
                    "https://www.bilibili.com/video/BV1Nm67YKEZv",
                    "https://www.youtube.com/watch?v=3ZFrYKdQqUc",
                    Text.translatable("url.morphclient.starrail"),
                    Text.translatable("url.morphclient.starrail2"));

            case "xbalanque", "xibalake" -> this.open(isChinese,
                    "https://www.bilibili.com/video/BV1mX6dYjEqW",
                    "https://www.bilibili.com/video/BV1mX6dYjEqW",
                    Text.translatable("url.morphclient.xbalanque"));
            */
        }
    }

    private void open(boolean isChinese, String urlCN, String urlOS, Component text)
    {
        this.open(isChinese, urlCN, urlOS, text, Component.literal(""));
    }

    private void open(boolean isChinese, String urlCN, String urlOS, Component text, @Nullable Component text2)
    {
        String url = isChinese ? urlCN : urlOS;
        var client = Minecraft.getInstance();

        var screen = new ConfirmScreen(confirmed ->
        {
            if (confirmed)
                Util.getPlatform().openUri(url);

            client.gui.setScreen(this.parentScreen);
        }, text, text2);

        client.gui.setScreen(screen);
    }
}
