package com.vanillarite.faq;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.text.DiffRowGenerator;
import com.vanillarite.faq.config.PrefixKind;
import com.vanillarite.faq.storage.Manager;
import com.vanillarite.faq.storage.Topic;
import com.vanillarite.faq.storage.supabase.Field;
import com.vanillarite.faq.storage.supabase.Method;
import com.vanillarite.faq.text.list.FaqLister;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.Template;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.vanillarite.faq.FaqPlugin.m;
import static com.vanillarite.faq.util.DurationUtil.formatInstantToNow;
import static net.kyori.adventure.text.Component.empty;
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
    var prefix = plugin.prefixFor(sender, PrefixKind.FAQ);
    BiFunction<Topic, Component, Component> topicCallback = null;
    if (sender.hasPermission("vfaq.manage.list")) {
      topicCallback = (t, c) -> c.insertion("/faqeditor actions %s".formatted(t.id()));
    }
    new FaqLister(
        PrefixKind.FAQ,
        "faq",
        plugin, manager.cache(),
        (g) -> sender.hasPermission("vfaq.group." + g),
        prefix::response,
        topicCallback
    ).run();
  }

  @CommandDescription("View a topic from the FAQ")
  @CommandMethod("faq <topic>")
  @CommandPermission("vfaq.faq")
  private void commandFaq(
      final @NotNull CommandSender sender,
      final @NotNull @Argument(value = "topic", suggestions = "faqTopicsAll") @Greedy String topic
  ) {
    var section = plugin.config().messages();
    var prefix = plugin.prefixFor(sender, PrefixKind.FAQ);
    var defaultGroup = section.list().defaultGroup();

    manager.cache().findTopicOrAlias(topic).ifPresentOrElse(
        (t) -> {
          if (t.group().equals(defaultGroup) || sender.hasPermission("vfaq.group." + t.group())) {
            prefix.response(
                section.header() + "<r>\n\n" + t.content() + "\n",
                Template.of("topic", t.topic())
            );
          } else {
            prefix.logged(section.unknownTopic());
          }
        },
        () -> prefix.logged(section.unknownTopic())
    );
  }

  @CommandDescription("List all FAQs to show to someone else")
  @CommandMethod("faq4u")
  @CommandPermission("vfaq.faq4u")
  private void commandFaqForYouNoArgs(
      final @NotNull CommandSender sender
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.FAQ4U);
    new FaqLister(
        PrefixKind.FAQ4U,
        "faq4u",
        plugin, manager.cache(),
        (g) -> false,
        prefix::response
    ).run();
  }


  @CommandDescription("Show everyone else a topic from the FAQ")
  @CommandMethod("faq4u <topic>")
  @CommandPermission("vfaq.faq4u")
  private void commandFaqForYou(
      final @NotNull CommandSender sender,
      final @NotNull @Argument(value = "topic", suggestions = "faqTopicsDefault") @Greedy String topic
  ) {
    var section = plugin.config().messages();
    var prefix = plugin.prefixFor(sender, PrefixKind.FAQ);
    var prefix4u = plugin.prefixFor(sender, PrefixKind.FAQ4U);
    var defaultGroup = section.list().defaultGroup();

    manager.cache().findTopicOrAlias(topic).ifPresentOrElse(
        (t) -> {
          if (!t.group().equals(defaultGroup)) {
            prefix4u.response(text("This topic can't be used because it's locked behind a permission", RED));
            return;
          }
          var preface = t.smartPreface(section.maxPreviewLines());
          var keepReading = showText(
              m.parse(section.keepReadingHover(), Template.of("topic", t.topic()))
          );
          preface.lines().forEach(i -> {
            var component = prefix.component(i);
            if (preface.isContinuable()) {
              component = component
                  .clickEvent(runCommand("/faq " + t.topic()))
                  .hoverEvent(keepReading);
            }
            plugin.networkBroadcast(component, sender);
          });
          if (preface.isPreview()) {
            plugin.networkBroadcast(prefix.component(section.keepReading()).clickEvent(
                runCommand("/faq " + t.topic())
            ).hoverEvent(keepReading), sender);
          }
        },
        () -> prefix4u.logged(section.unknownTopic())
    );
  }

  @CommandDescription("Create a new FAQ")
  @CommandMethod("faqeditor new <topic>")
  @CommandPermission("vfaq.manage.new")
  private void commandFaqNew(
      final @NotNull CommandSender sender,
      final @NotNull @Argument("topic") @Greedy String topicName
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);
    var section = plugin.config().messages().manage();

    if (manager.assertNoExisting(topicName)) {
      prefix.logged(text("This name cannot be used because it would have the same name as an already existing topic or alias", RED));
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
            prefix.response(manager.makeEditorLink(isConsole, newTopic, Field.CONTENT, section.initialPlaceholder())
                .apply(text("Please click to set the content now!", BLUE, UNDERLINED).hoverEvent(showText(text("Click!")))));
          } catch (ExecutionException | InterruptedException e) {
            prefix.logged(text("Making editor link failed?", RED));
          }
        },
        () -> prefix.logged(text("Creating new FAQ failed?", RED))
    );
  }

  @CommandDescription("List all FAQ for management")
  @CommandMethod("faqeditor")
  @CommandPermission("vfaq.manage.list")
  private void commandFaqList(
      final @NotNull CommandSender sender
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);
    var section = plugin.config().messages().manage().list();
    var defaultGroup = plugin.config().messages().list().defaultGroup();

    prefix.logged(section.header(), Template.of("count", String.valueOf(manager.cache().invalidateAndGet().size())));
    manager.cache().get().stream().sorted(Comparator.comparingInt(Topic::id)).forEach(topic -> {
      Component topicComponent;
      Component aliases = empty();
      ArrayList<Template> placeholders = new ArrayList<>();
      placeholders.add(Template.of("id", String.valueOf(topic.id())));
      placeholders.add(Template.of("author", Manager.componentAuthor(topic.author())));
      placeholders.add(Template.of("author_uuid", topic.author().toString()));
      placeholders.add(Template.of("group", topic.group()));
      placeholders.add(Template.of("created_ago", formatInstantToNow(topic.createdAt())));
      placeholders.add(Template.of("updated_ago", formatInstantToNow(topic.updatedAt())));
      if (topic.group().equals(defaultGroup)) {
        topicComponent = m.parse(
            section.topic().defaultGroup(),
            Template.of("topic", topic.topic())
        );
      } else {
        topicComponent = m.parse(
            section.topic().customGroup(),
            Template.of("topic", topic.topic()),
            Template.of("group", topic.group())
        );
      }
      topicComponent = topicComponent.hoverEvent(showText(
          m.parse(section.topic().hover(), placeholders)
      ));
      if (topic.alias().size() > 0) {
        aliases = m.parse(section.aliases(), Template.of("aliases", String.join(", ", topic.alias())));
      }
      placeholders.add(Template.of("topic", topicComponent));
      placeholders.add(Template.of("aliases", aliases));
      placeholders.add(Template.of("edit_button", m.parse(section.editLabel())
          .clickEvent(runCommand("/faqeditor actions %s".formatted(topic.id())))
          .hoverEvent(showText(m.parse(section.editLabelHover(),
              Template.of("topic", topicComponent))
          ))));
      placeholders.add(Template.of("preview_content", manager.makePreview(Field.CONTENT, topic)));
      placeholders.add(Template.of("preview_preface", manager.makePreview(Field.PREFACE, topic)));

      prefix.response(
          section.line() + (topic.content().isBlank() ? section.incomplete() : ""), placeholders
      );
    });
    prefix.response(section.hint(),
        Template.of("preview_content", m.parse(section.preview().content().label())),
        Template.of("preview_preface", m.parse(section.preview().preface().label()))
    );
  }

  @CommandDescription("Menu for editing FAQ")
  @CommandMethod("faqeditor actions <id>")
  @CommandPermission("vfaq.manage.list")
  private void commandFaqActions(
      final @NotNull CommandSender sender,
      final @Argument("id") int id
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);
    var section = plugin.config().messages().manage().edit();
    var existing = manager.cache().find(id);

    try {
      prefix.response(section.header(), Template.of("topic", existing.topic()));
      manager.makeButtons(existing, sender::hasPermission).forEach(prefix::response);
      prefix.response(Component.empty());
    } catch (ExecutionException | InterruptedException e) {
      prefix.logged(text("Failed?", RED));
    }
  }

  @CommandDescription("Create editor modification to the content of a FAQ")
  @CommandMethod("faqeditor set <id> editor content")
  @CommandPermission("vfaq.manage.edit.content")
  private void commandFaqSetEditorContent(
      final @NotNull CommandSender sender,
      final @Argument("id") int id
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);
    var section = plugin.config().messages().manage();
    prefix.response(text("Generating link...", GRAY, ITALIC));

    var existing = manager.cache().findNow(id);
    boolean isConsole = sender instanceof ConsoleCommandSender;

    try {
      prefix.response(manager.makeEditorLink(isConsole, existing, Field.CONTENT, section.initialPlaceholder())
          .apply(text("Please click to edit the content of %s!".formatted(existing.topic()), BLUE, UNDERLINED).hoverEvent(showText(text("Click!")))));
    } catch (ExecutionException | InterruptedException e) {
      prefix.logged(text("Making editor link failed?", RED));
    }
  }

  @CommandDescription("Create editor modification to the content of a FAQ")
  @CommandMethod("faqeditor set <id> editor preface")
  @CommandPermission("vfaq.manage.edit.preface")
  private void commandFaqSetEditorPreface(
      final @NotNull CommandSender sender,
      final @Argument("id") int id
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);
    prefix.response(text("Generating link...", GRAY, ITALIC));

    var existing = manager.cache().findNow(id);
    boolean isConsole = sender instanceof ConsoleCommandSender;

    try {
      prefix.response(manager.makeEditorLink(isConsole, existing, Field.PREFACE, plugin.config().messages().manage().initialPlaceholder())
          .apply(text("Please click to edit the preface of %s!".formatted(existing.topic()), BLUE, UNDERLINED).hoverEvent(showText(text("Click!")))));
    } catch (ExecutionException | InterruptedException e) {
      prefix.logged(text("Making editor link failed?", RED));
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
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);

    if (manager.assertNoExisting(newTopic)) {
      prefix.logged(text("This name cannot be used because it would have the same name as an already existing topic or alias", RED));
      return;
    }

    prefix.response(text("Processing change...", GRAY, ITALIC));

    var modified = manager.updateFaqTopic(id, Field.TOPIC, newTopic, sender);
    modified.ifPresentOrElse(
        faqTopic -> prefix.logged(text("Success! TOPIC of #%s was modified (now %s)".formatted(id, faqTopic.topic()))),
        () -> prefix.logged(text("Saving new TOPIC failed?", RED))
    );
  }

  @CommandDescription("Set group of a FAQ")
  @CommandMethod("faqeditor set <id> group [new_group]")
  @CommandPermission("vfaq.manage.edit.group")
  private void commandFaqEditGroup(
      final @NotNull CommandSender sender,
      final @Argument("id") int id,
      final @Argument("new_group") @Greedy String groupName
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);

    var cleanGroupName = groupName;
    if (groupName == null || groupName.isBlank()) {
      cleanGroupName = plugin.config().messages().list().defaultGroup();
    }

    prefix.response(text("Processing change...", GRAY, ITALIC));

    var modified = manager.updateFaqTopic(id, Field.GROUP, cleanGroupName, sender);
    modified.ifPresentOrElse(
        faqTopic -> prefix.logged(text("Success! GROUP of #%s was modified (now %s)".formatted(id, faqTopic.group()))),
        () -> prefix.logged(text("Saving new GROUP failed?", RED))
    );
  }

  @CommandDescription("yes")
  @CommandMethod("faqeditor debug dump")
  @CommandPermission("vfaq.admin.debug")
  private void commandFaqDebug(
      final @NotNull CommandSender sender
  ) {
    sender.sendMessage(manager.cache().invalidateAndGet().toString());
  }

  @CommandDescription("Add new alias to a FAQ")
  @CommandMethod("faqeditor set <id> alias add <new_alias>")
  @CommandPermission("vfaq.manage.edit.alias")
  private void commandFaqAddAlias(
      final @NotNull CommandSender sender,
      final @Argument("id") int id,
      final @NotNull @Argument("new_alias") @Greedy String newAlias
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);

    if (manager.assertNoExisting(newAlias)) {
      prefix.logged(text("This alias cannot be used because it would have the same name as an already existing topic or alias", RED));
      return;
    }

    prefix.response(text("Processing change...", GRAY, ITALIC));

    var modified = manager.updateFaqArrayField(id, Field.ALIAS, Method.POST, newAlias, sender);
    modified.ifPresentOrElse(
        faqTopic -> prefix.logged(text("Success! ALIAS of #%s was modified (now %s: %s)".formatted(id, faqTopic.alias().size(), faqTopic.alias()))),
        () -> prefix.logged(text("Saving new ALIAS failed?", RED))
    );
  }

  @CommandDescription("Remove alias from a FAQ")
  @CommandMethod("faqeditor set <id> alias remove <alias_name>")
  @CommandPermission("vfaq.manage.edit.alias")
  private void commandFaqRemoveAlias(
      final @NotNull CommandSender sender,
      final @Argument("id") int id,
      final @NotNull @Argument("alias_name") @Greedy String aliasName
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);

    prefix.response(text("Processing change...", GRAY, ITALIC));

    var modified = manager.updateFaqArrayField(id, Field.ALIAS, Method.DELETE, aliasName, sender);
    modified.ifPresentOrElse(
        faqTopic -> prefix.logged(text("Success! ALIAS of #%s was modified (now %s: %s)".formatted(id, faqTopic.alias().size(), faqTopic.alias()))),
        () -> prefix.logged(text("Saving new ALIAS failed?", RED))
    );
  }

  @CommandDescription("Reposition a FAQ")
  @CommandMethod("faqeditor set <id> positionmenu")
  @CommandPermission("vfaq.manage.edit.move")
  private void commandFaqAddAlias(
      final @NotNull CommandSender sender,
      final @Argument("id") int id
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);

    var lastPos = new AtomicReference<>(new Topic.Pos(0, 0));
    var existing = manager.cache().findNow(id);
    var lister = new FaqLister(
        PrefixKind.FAQ,
        "faq",
        plugin, manager.cache(),
        (g) -> sender.hasPermission("vfaq.group." + g),
        (line) -> prefix.response(
            line
                .clickEvent(ClickEvent.runCommand("/faqeditor set %s pos %s %s".formatted(id, lastPos.get().line(), lastPos.get().col() + 1)))
                .hoverEvent(showText(text("Click to add to the end of this row")))
        )
    );
    var relevantGroup = lister.topicGroupOf(existing.group());
    lister.emitTopicGroup(
        relevantGroup.stream().filter(i -> i.pos().line() != 0).toList(),
        (faq) -> {
          lastPos.set(faq.pos());
          return m.parse(
              lister.section.eachTopic(),
              Template.of("topic", faq.topic())
          );
        }
    );
    prefix.response(empty()
        .append(text("[New row]", GREEN)
            .clickEvent(ClickEvent.runCommand("/faqeditor set %s pos %s %s".formatted(
                id, lastPos.get().line() + 1, 1
            ))))
        .append(text("[Remove custom position]", RED)
            .clickEvent(ClickEvent.runCommand("/faqeditor set %s pos 0 0".formatted(id)))
        )
    );
  }

  @CommandDescription("Move a FAQ")
  @CommandMethod("faqeditor set <id> pos <line> <col>")
  @CommandPermission("vfaq.manage.edit.move")
  private void commandFaqRemoveAlias(
      final @NotNull CommandSender sender,
      final @Argument("id") int id,
      final @Argument("line") int line,
      final @Argument("col") int col
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);

    prefix.response(text("Processing change...", GRAY, ITALIC));
    var newPos = new Topic.Pos(line, col);

    var modified = manager.updateFaqComplex(id, Field.POS, newPos.tuple(), newPos.toJson(), sender);
    modified.ifPresentOrElse(
        faqTopic -> prefix.logged(text("Success! POS of #%s was modified (now %s)".formatted(id, faqTopic.pos().tuple()))),
        () -> prefix.logged(text("Saving new POS failed?", RED))
    );
  }

  @CommandDescription("Delete FAQ")
  @CommandMethod("faqeditor delete <id>")
  @CommandPermission("vfaq.manage.delete")
  private void commandFaqDeleteTopic(
      final @NotNull CommandSender sender,
      final @Argument("id") int id
  ) {
    var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);

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
    try {
      plugin.loadConfig();
      var prefix = plugin.prefixFor(sender, PrefixKind.EDITOR);
      prefix.response("Config was reloaded!");
    } catch (ConfigurateException e) {
      e.printStackTrace();
    }
  }

  @CommandMethod("faqeditor admin history <id>")
  @CommandPermission("vfaq.admin.history")
  private void commandAdminHistory(
      final @NotNull CommandSender sender,
      final @Argument("id") int faq
  ) {
    var history = manager.cache().supabaseGetHistoryForFaq(manager.supabase(), faq).orElseThrow();
    history.forEach(i -> sender.sendMessage(i.asComponent()));
  }

  @CommandMethod("faqeditor admin inspect <id>")
  @CommandPermission("vfaq.admin.inspect")
  private void commandAdminInspect(
      final @NotNull CommandSender sender,
      final @Argument("id") int historyId
  ) {
    var history = manager.cache().supabaseGetHistorySingle(manager.supabase(), historyId).orElseThrow();
    var delimiter = text("====== Modification inspection for %s ======".formatted(history.id()));
    try {
      var drg = DiffRowGenerator.create()
          .showInlineDiffs(true)
          .mergeOriginalRevised(true)
          .lineNormalizer(i -> i.replace("<", "\\<"))
          .oldTag(f -> f ? "<red><st>" : "</st></red>")
          .newTag(f -> f ? "<green><u>" : "</u></green>")
          .build();
      var rows = drg.generateDiffRows(List.of(history.beforeOrBlank().split("\n")), List.of(history.after().split("\n")));
      sender.sendMessage(delimiter);
      rows.forEach(r -> {
        var prefix = switch (r.getTag()) {
          case EQUAL -> text(" = ", WHITE);
          case INSERT -> text(" + ", GREEN);
          case DELETE -> text(" - ", RED);
          case CHANGE -> text(" > ", YELLOW);
        };
        sender.sendMessage(text("", GRAY).append(prefix).append(text("| ", DARK_GRAY)).append(m.parse(r.getOldLine())));
      });
      sender.sendMessage(delimiter);
    } catch (DiffException e) {
      e.printStackTrace();
    }
  }

  @Suggestions("faqTopicsAll")
  public @NotNull List<String> completeFaqTopicsAll(CommandContext<CommandSender> sender, String input) {
    var defaultGroup = plugin.config().messages().list().defaultGroup();
    return manager.cache().get().stream()
        .filter(t -> t.group().equals(defaultGroup) || sender.getSender().hasPermission("vfaq.group." + t.group()))
        .mapMulti((Topic i, Consumer<String> r) -> {
          r.accept(i.topic());
          i.alias().forEach(r);
        })
        .filter(t -> t.toLowerCase().startsWith(input.toLowerCase()))
        .limit(20).toList();
  }

  @Suggestions("faqTopicsDefault")
  public @NotNull List<String> completeFaqTopicsDefault(CommandContext<CommandSender> sender, String input) {
    var defaultGroup = plugin.config().messages().list().defaultGroup();
    return manager.cache().get().stream()
        .filter(t -> t.group().equals(defaultGroup))
        .mapMulti((Topic i, Consumer<String> r) -> {
          r.accept(i.topic());
          i.alias().forEach(r);
        })
        .filter(t -> t.toLowerCase().startsWith(input.toLowerCase()))
        .limit(20).toList();
  }
}
