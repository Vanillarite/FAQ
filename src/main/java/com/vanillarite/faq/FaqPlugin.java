package com.vanillarite.faq;

import cloud.commandframework.CommandTree;
import cloud.commandframework.annotations.AnnotationParser;
import cloud.commandframework.arguments.parser.ParserParameters;
import cloud.commandframework.arguments.parser.StandardParameters;
import cloud.commandframework.bukkit.CloudBukkitCapabilities;
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.paper.PaperCommandManager;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.vanillarite.faq.util.Prefixer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

public final class FaqPlugin extends JavaPlugin {
  public static final MiniMessage m = MiniMessage.get();
  private boolean isBungee = false;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    isBungee = getServer().spigot().getSpigotConfig().getBoolean("settings.bungeecord");
    if (isBungee) {
      getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    final Function<CommandTree<CommandSender>, CommandExecutionCoordinator<CommandSender>>
        executionCoordinatorFunction =
        AsynchronousCommandExecutionCoordinator.<CommandSender>newBuilder().build();
    final Function<CommandSender, CommandSender> mapperFunction = Function.identity();
    PaperCommandManager<CommandSender> manager;
    try {
      manager =
          new PaperCommandManager<>(
              this, executionCoordinatorFunction, mapperFunction, mapperFunction);
    } catch (final Exception e) {
      getLogger().severe("Failed to initialize the command manager");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    if (manager.queryCapability(CloudBukkitCapabilities.BRIGADIER)) {
      manager.registerBrigadier();
    }
    final Function<ParserParameters, CommandMeta> commandMetaFunction =
        p ->
            CommandMeta.simple()
                .with(
                    CommandMeta.DESCRIPTION,
                    p.get(StandardParameters.DESCRIPTION, "No description"))
                .build();
    AnnotationParser<CommandSender> annotationParser =
        new AnnotationParser<>(manager, CommandSender.class, commandMetaFunction);

  }

  public void networkBroadcast(@NotNull Component c, @Nullable CommandSender sender) {
    if (!isBungee) {
      getServer().broadcast(c);
      return;
    }

    //noinspection UnstableApiUsage
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeUTF("MessageRaw");
    out.writeUTF("ALL");
    out.writeUTF(GsonComponentSerializer.gson().serialize(c));
    Bukkit.getConsoleSender().sendMessage(c);

    Bukkit.getOnlinePlayers().stream()
        .findFirst()
        .ifPresentOrElse(
            (e) -> e.sendPluginMessage(this, "BungeeCord", out.toByteArray()),
            () -> {
              if (sender != null) {
                sender.sendMessage(Component.text("Failed to broadcast because nobody is online", NamedTextColor.RED));
              } else {
                getLogger().severe("Failed to broadcast because nobody is online");
              }
            });
  }


  public void debug(String msg) {
    if (getConfig().getBoolean("debug")) {
      getLogger().info("[DEBUG] %s".formatted(msg));
    }
  }

  public ConfigurationSection section(String... p) {
    var sep = Objects.requireNonNull(getConfig().getRoot()).options().pathSeparator();
    var path = String.join(Character.toString(sep), p);
    return Objects.requireNonNull(getConfig().getConfigurationSection(path));
  }

  public Prefixer prefixFor(CommandSender sender, String kind) {
    return new Prefixer(sender, Objects.requireNonNull(getConfig().getString("prefix." + kind)));
  }
}
