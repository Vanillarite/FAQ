package com.vanillarite.faq;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.vanillarite.faq.config.Config;
import com.vanillarite.faq.config.PrefixKind;
import com.vanillarite.faq.util.Prefixer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.CommandTree;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.parser.ParserParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.util.NamingSchemes;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public final class FaqPlugin extends JavaPlugin {
  public static final MiniMessage m = MiniMessage.miniMessage();
  private final ObjectMapper.Factory objectFactory =
      ObjectMapper.factoryBuilder().defaultNamingScheme(NamingSchemes.SNAKE_CASE).build();
  private final YamlConfigurationLoader.Builder configBuilder =
      YamlConfigurationLoader.builder()
          .defaultOptions(
              opts -> opts.serializers(builder -> builder.registerAnnotatedObjects(objectFactory)));
  private final File configFile = new File(this.getDataFolder(), "config.yml");
  private boolean isBungee = false;
  private Config config;

  public Config config() {
    return config;
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();

    final boolean bungeeEnabled = getServer().spigot().getSpigotConfig().getBoolean("settings.bungeecord");
    final boolean velocityEnabled = getServer().spigot().getPaperConfig().getBoolean("proxies.velocity.enabled");
    isBungee = bungeeEnabled || velocityEnabled;
    if (isBungee) {
      getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    try {
      loadConfig();
    } catch (ConfigurateException e) {
      e.printStackTrace();
    }

    final LegacyPaperCommandManager<CommandSender> manager =
            LegacyPaperCommandManager.createNative(this, ExecutionCoordinator.simpleCoordinator());
    if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
      manager.registerBrigadier();
    }

//    manager.setCommandSuggestionProcessor(
//        (context, strings) -> {
//          final String input;
//          if (context.getInputQueue().isEmpty()) {
//            input = "";
//          } else {
//            input = context.getInputQueue().peek();
//          }
//          final List<String> suggestions = new LinkedList<>();
//          for (final String suggestion : strings) {
//            if (suggestion.toLowerCase().startsWith(input.toLowerCase())) {
//              suggestions.add(suggestion);
//            }
//          }
//          return suggestions;
//        });

    AnnotationParser<CommandSender> annotationParser = new AnnotationParser<>(manager, CommandSender.class);

    var commandHolder = new Commands(this);
    annotationParser.parse(commandHolder);

    getServer().getScheduler().runTaskLater(this, () -> debug(config.toString()), 50);
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
                sender.sendMessage(
                    Component.text(
                        "Failed to broadcast because nobody is online", NamedTextColor.RED));
              } else {
                getLogger().severe("Failed to broadcast because nobody is online");
              }
            });
  }

  public void debug(String msg) {
    if (config.debug()) {
      getLogger().info("[DEBUG] %s".formatted(msg));
    }
  }

  public Prefixer prefixFor(CommandSender sender, PrefixKind kind) {
    return new Prefixer(sender, config.prefix().get(kind));
  }

  public void loadConfig() throws ConfigurateException {
    final var configLoader = configBuilder.file(configFile).build();
    final var defaultLoader = configBuilder.url(this.getClass().getResource("/config.yml")).build();
    config =
        objectFactory.get(Config.class).load(configLoader.load().mergeFrom(defaultLoader.load()));
  }
}
