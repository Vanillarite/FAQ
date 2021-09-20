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
import com.vanillarite.faq.storage.Topic;
import com.vanillarite.faq.storage.supabase.Field;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Template;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static com.vanillarite.faq.FaqPlugin.m;
import static com.vanillarite.faq.util.DurationUtil.formatInstantToNow;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
import static net.kyori.adventure.text.format.TextDecoration.UNDERLINED;

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
    var section = plugin.section("messages");
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

  @CommandDescription("Create a new FAQ")
  @CommandMethod("faqeditor new <topic>")
  @CommandPermission("vfaq.manage.new")
  private void commandFaqNew(
      final @NotNull CommandSender sender,
      final @NotNull @Argument("topic") @Greedy String topicName
  ) {
    var prefix = plugin.prefixFor(sender, "editor");
    var section = plugin.section("messages", "manage");

    if (manager.cache().invalidateAndGet().stream().anyMatch(i -> i.topic().equalsIgnoreCase(topicName))) {
      prefix.logged(text("This name cannot be used because it would have the same name as an already existing topic", RED));
      return;
    }

    prefix.response(text("Processing new topic...", GRAY, ITALIC));

    boolean isConsole = sender instanceof ConsoleCommandSender;
    var created = manager.createFaqTopic(topicName, sender);
    created.ifPresentOrElse(
        newTopic -> {
          prefix.logged(text("Success! Created new FAQ (assigned #%s)".formatted(newTopic.id())));
          prefix.logged(text("This FAQ still has an empty body, creating link to set the content...", GRAY, ITALIC));
          try {
            prefix.response(manager.makeEditorLink(isConsole, newTopic, Field.CONTENT, section.getString("initial_placeholder"))
                .apply(text("Please click to set the content now!", BLUE, UNDERLINED).hoverEvent(showText(text("Click!")))));
          } catch (ExecutionException | InterruptedException e) {
            prefix.logged(text("Making editor link failed?", RED));
          }
        },
        () -> prefix.logged(text("Creating new FAQ failed?", RED))
    );
  }

  @CommandDescription("List all FAQ for management")
  @CommandMethod("faqeditor list")
  @CommandPermission("vfaq.manage.list")
  private void commandFaqList(
      final @NotNull CommandSender sender
  ) {
    var prefix = plugin.prefixFor(sender, "editor");
    var section = plugin.section("messages", "manage", "list");

    prefix.logged(section.getString("header"), Template.of("count", String.valueOf(manager.cache().invalidateAndGet().size())));
    manager.cache().get().stream().sorted(Comparator.comparingInt(Topic::id)).forEach(topic -> prefix.response(
        section.getString("line") + (topic.content().isBlank() ? section.getString("incomplete") : ""),
        Template.of("id", String.valueOf(topic.id())),
        Template.of("topic", String.valueOf(topic.topic())),
        Template.of("author", manager.resolveAuthor(topic.author())),
        Template.of("created_ago", formatInstantToNow(topic.createdAt())),
        Template.of("updated_ago", formatInstantToNow(topic.updatedAt())),
        Template.of("edit_button", m.parse(Objects.requireNonNull(section.getString("edit_label")))
            .clickEvent(runCommand("/vu faqeditor actions %s".formatted(topic.id())))),
        Template.of("preview_content", manager.makePreview("content", topic.content())),
        Template.of("preview_preface", manager.makePreview("preface", topic.preface()))
    ));
    prefix.response(Component.empty());
  }

  @CommandDescription("Menu for editing FAQ")
  @CommandMethod("faqeditor actions <id>")
  @CommandPermission("vfaq.manage.list")
  private void commandFaqActions(
      final @NotNull CommandSender sender,
      final @Argument("id") int id
  ) {
    var prefix = plugin.prefixFor(sender, "editor");
    var section = plugin.section("messages", "manage", "edit");
    var existing = manager.cache().findNow(id);

    prefix.response(text("Generating links...", GRAY, ITALIC));

    try {
      prefix.response(section.getString("header"), Template.of("topic", existing.topic()));
      prefix.response(manager.makeButtons((sender instanceof ConsoleCommandSender), existing, sender::hasPermission));
      prefix.response(Component.empty());
    } catch (ExecutionException | InterruptedException e) {
      prefix.logged(text("Failed?", RED));
    }
  }



  @CommandDescription("Apply editor modification to the content of a FAQ")
  @CommandMethod("faqeditor submit <id> content <token>")
  @CommandPermission("vfaq.manage.edit.content")
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
