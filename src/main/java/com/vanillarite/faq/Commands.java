package com.vanillarite.faq;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import com.vanillarite.faq.storage.FaqCache;
import com.vanillarite.faq.storage.Manager;
import com.vanillarite.faq.storage.supabase.Field;
import net.kyori.adventure.text.minimessage.Template;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.vanillarite.faq.FaqPlugin.m;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

public class Commands {
  private final FaqPlugin plugin;
  private final Manager manager;

  public Commands(FaqPlugin plugin) {
    this.plugin = plugin;
    this.manager = new Manager(plugin);
  }

  @CommandDescription("List all FAQs")
  @CommandMethod("faq")
  @CommandPermission("vfaq.faq")
  private void commandFaqNoArgs(
      final @NotNull CommandSender sender
  ) {
    manager.showFaqList(sender, "faq");
  }

  @CommandDescription("Show everyone else a topic from the FAQ")
  @CommandMethod("faq4u <topic>")
  @CommandPermission("vfaq.faq")
  private void commandFaqForYou(
      final @NotNull CommandSender sender,
      final @NotNull @Argument(value = "topic", suggestions = "faqTopicsAll") @Greedy String topic
  ) {
    var section = plugin.section("faq", "messages");
    var prefix = plugin.prefixFor(sender, "faq");
    var prefix4u = plugin.prefixFor(sender, "faq4u");

    manager.cache().findIgnoreEmpty(topic).ifPresentOrElse(
        (t) -> {
          var preface = t.smartPreface(section.getInt("max_preview_lines"));
          var keepReading = showText(
              m.parse(Objects.requireNonNull(section.getString("hover")), Template.of("topic", t.topic()))
          );
          preface.lines().forEach(i -> {
            var component = prefix.component(i);
            if (preface.isContinuable()) {
              component = component
                  .clickEvent(runCommand("/faq " + topic))
                  .hoverEvent(keepReading);
            }
            plugin.networkBroadcast(component, sender);
          });
          if (preface.isPreview()) {
            plugin.networkBroadcast(prefix.component(section.getString("keep_reading")).clickEvent(
                runCommand("/faq " + topic)
            ).hoverEvent(keepReading), sender);
          }
        },
        ( ) -> prefix4u.logged(section.getString("unknown_topic"))
    );
  }

  @CommandDescription("Apply editor modification to the content of a FAQ")
  @CommandMethod("faqeditor submit <id> content <token>")
  @CommandPermission("vfaq.manage.edit.preface")
  private void commandFaqSubmitContent(
      final @NotNull CommandSender sender,
      final @Argument("id") int id,
      final @NotNull @Argument("token") @Greedy String token
  ) {
    manager.applyEditor(sender, id, token, Field.CONTENT);
  }


  @CommandDescription("Apply editor modification to the preface of a FAQ")
  @CommandMethod("faqeditor submit <id> preface <token>")
  @CommandPermission("vfaq.manage.edit.preface")
  private void commandFaqSubmitPreface(
      final @NotNull CommandSender sender,
      final @Argument("id") int id,
      final @NotNull @Argument("token") @Greedy String token
  ) {
    manager.applyEditor(sender, id, token, Field.PREFACE);
  }

  @CommandDescription("Set new topic to a FAQ")
  @CommandMethod("faqeditor set <id> topic <new_topic>")
  @CommandPermission("vfaq.manage.edit.rename")
  private void commandFaqEditTopic(
      final @NotNull CommandSender sender,
      final @Argument("id") int id,
      final @NotNull @Argument("new_topic") @Greedy String newTopic
  ) {
    var prefix = plugin.prefixFor(sender, "editor");

    if (manager.cache().invalidateAndGet().stream().anyMatch(i -> i.topic().equalsIgnoreCase(newTopic))) {
      prefix.logged(text("This name cannot be used because it would have the same name as an already existing topic", RED));
      return;
    }

    prefix.response(text("Processing change...", GRAY, ITALIC));

    var modified = manager.updateFaqTopic(id, Field.TOPIC, newTopic, sender);
    modified.ifPresentOrElse(
        faqTopic -> prefix.logged(text("Success! TOPIC of #%s was modified (now %s)".formatted(id, faqTopic.topic()))),
        () ->       prefix.logged(text("Saving new TOPIC failed?", RED))
    );
  }

  @CommandDescription("Set new topic to a FAQ")
  @CommandMethod("faqeditor delete <id>")
  @CommandPermission("vfaq.manage.delete")
  private void commandFaqDeleteTopic(
      final @NotNull CommandSender sender,
      final @Argument("id") int id
  ) {
    var prefix = plugin.prefixFor(sender, "editor");

    prefix.response(text("Processing deletion...", GRAY, ITALIC));

    var success = manager.deleteFaqTopic(id, sender);
    if (success) {
      prefix.logged(text("Success! #%s was deleted".formatted(id)));
    } else {
      prefix.logged(text("Deleting FAQ failed?", RED));
    }
  }

  @CommandDescription("Reload config")
  @CommandMethod("faqeditor reload config")
  @CommandPermission("vfaq.cmd.reload")
  private void commandReload(final @NotNull CommandSender sender) {
    plugin.reloadConfig();

    var prefix = plugin.prefixFor(sender, "editor");
    prefix.response("Config was reloaded!");
  }

  @Suggestions("faqTopicsAll")
  public @NotNull List<String> completeFaqTopicsAll(CommandContext<CommandSender> sender, String input) {
    var mostLike = manager.cache().get().stream().filter(
        t -> t.topic().toLowerCase().startsWith(input.toLowerCase())
    ).limit(20).map(
        t -> input + t.topic().substring(input.length())
    );
    return mostLike.toList();
  }
}
