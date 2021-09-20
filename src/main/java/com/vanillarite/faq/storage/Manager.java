package com.vanillarite.faq.storage;

import cloud.commandframework.types.tuples.Pair;
import com.vanillarite.faq.AdventureEditorAPI;
import com.vanillarite.faq.FaqPlugin;
import com.vanillarite.faq.storage.supabase.Field;
import com.vanillarite.faq.storage.supabase.SupabaseConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.Template;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.vanillarite.faq.FaqPlugin.m;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.TextComponent.ofChildren;
import static net.kyori.adventure.text.event.ClickEvent.openUrl;
import static net.kyori.adventure.text.event.ClickEvent.suggestCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

public class Manager {
  private final FaqPlugin plugin;
  private final FaqCache faqCache;
  private static final UUID NULL_UUID = new UUID(0, 0);

  public Manager(FaqPlugin plugin) {
    this.plugin = plugin;
    this.faqCache = new FaqCache(this::getAllFaqTopics, 60, TimeUnit.MINUTES, plugin);
  }

  public FaqCache cache() {
    return faqCache;
  }

  private SupabaseConnection supabase() {
    var section = plugin.section("faq", "supabase");

    return new SupabaseConnection(
        URI.create(Objects.requireNonNull(section.getString("url"))),
        section.getString("anon_key"),
        section.getString("auth_key")
    );
  }

  public Optional<Topic> updateFaqTopic(int id, Field key, String value, CommandSender author) {
    return faqCache.supabasePatch(supabase(), id, key, value, getAuthor(author));
  }

  public Optional<Topic> createFaqTopic(String topic, CommandSender author) {
    return faqCache.supabasePost(supabase(), topic, getAuthor(author));
  }

  public boolean deleteFaqTopic(int id, CommandSender author) {
    return faqCache.supabaseDelete(supabase(), id, getAuthor(author));
  }

  public ArrayList<Topic> getAllFaqTopics() {
    var updated = faqCache.supabaseGet(supabase());
    if (updated.isPresent()) {
      plugin.debug("FAQ List cache has been updated, %s entries in memory".formatted(updated.get().size()));
      var list = updated.get();
      list.sort(Comparator.comparingInt(Topic::id));
      return list;
    } else {
      plugin.getLogger().severe("FAQ List cache was invalidated but couldn't update. A stack trace is above");
      return null;
    }
  }

  public Function<Component, Component> makeEditorLink(boolean noHover, Topic existing, Field field) throws ExecutionException, InterruptedException {
    return makeEditorLink(noHover, existing, field, null);
  }

  public Function<Component, Component> makeEditorLink(boolean noHover, Topic existing, Field field, String placeholder) throws ExecutionException, InterruptedException {
    var editor = plugin.section("faq", "mm_editor");
    var editorLink = Objects.requireNonNull(editor.getString("url"));

    var token = new AdventureEditorAPI(URI.create(editorLink)).startSession(
        requireNonBlankElse(existing.findField(field), placeholder),
        "/vu faqeditor submit %s %s {token}".formatted(String.valueOf(existing.id()), field.name().toLowerCase()),
        "VanillariteUtil FAQ"
    ).get();

    var tokenLink = "%s?mode=chat_open&token=%s".formatted(editorLink, token);

    if (noHover) {
      return (c) -> c.append(text("Editor link for %s: <%s>".formatted(field, tokenLink)));
    } else {
      return (c) -> c.clickEvent(openUrl(tokenLink));
    }
  }

  private @NotNull String requireNonBlankElse(@Nullable String s1, String s2) {
    if (s1 == null) return s2;
    if (s1.isBlank()) return s2;
    return s1;
  }

  private Component makeButton(String kind, Function<Component, Component> transform) {
    var section = plugin.section("messages", "manage", "edit", "button");
    return transform.apply(m.parse(Objects.requireNonNull(section.getString(kind))));
  }

  public Component makeButtons(boolean noHover, Topic faq, Predicate<String> permissionChecker) throws ExecutionException, InterruptedException {
    var section = plugin.section("messages", "manage");
    var buttons = new ArrayList<Component>();
    Function<String, Function<Component, Component>> cmd = (name) -> (c) -> c.clickEvent(suggestCommand(name.formatted(faq.id())));
    List.of(
        Pair.of("content", makeEditorLink(noHover, faq, Field.CONTENT, section.getString("initial_placeholder"))),
        Pair.of("preface", makeEditorLink(noHover, faq, Field.PREFACE, section.getString("initial_placeholder"))),
        Pair.of("rename", cmd.apply("/vu faqeditor set %s topic ")),
        Pair.of("delete", cmd.apply("/vu faqeditor delete %s"))
    ).forEach(i -> {
      if (permissionChecker.test("vfaq.manage." + (i.getFirst().equals("delete") ? i.getFirst() : "edit." + i.getFirst()))) {
        buttons.add(makeButton(i.getFirst(), i.getSecond()));
      }
    });

    return ofChildren(buttons.toArray(new Component[0]));
  }

  public void showFaqList(CommandSender sender, String command) {
    var section = plugin.section("messages", "list", command);
    var prefix = plugin.prefixFor(sender, command);
    prefix.logged(section.getString("header"));

    var topics = new ArrayList<Component>();
    Runnable emitLine = () -> {
      if (topics.size() > 0) {
        prefix.response(ofChildren(
            m.parse(Objects.requireNonNull(section.getString("prefix"))),
            ofChildren(topics.toArray(new Component[0]))
        ));
        topics.clear();
      }
    };

    var maxPerLine = section.getInt("max_per_line");

    faqCache.getIgnoreEmpty().forEach((faq) -> {
      if (topics.size() + 1 > maxPerLine) emitLine.run();

      topics.add(
          m.parse(Objects.requireNonNull(section.getString("each_topic")),
              Template.of("topic", faq.topic())
          ).clickEvent(
              ClickEvent.runCommand("/" + command + " " + faq.topic())
          ).hoverEvent(
              showText(
                  m.parse(Objects.requireNonNull(section.getString("hover")), Template.of("topic", faq.topic()))
              )
          )
      );
    });
    emitLine.run();
    prefix.response(empty());
  }

  public void applyEditor(CommandSender sender, int id, String token, Field field) {
    var editor = plugin.section("faq", "mm_editor");
    var editorLink = Objects.requireNonNull(editor.getString("url"));
    var prefix = plugin.prefixFor(sender, "vu");

    prefix.response(text("Processing change...", GRAY, ITALIC));

    try {
      var output = new AdventureEditorAPI(URI.create(editorLink)).retrieveSession(token).get();
      var modified = updateFaqTopic(id, field, output.replace("\\n", "\n"), sender);
      // TODO: uncomment when https://github.com/KyoriPowered/adventure-webui/pull/24 is merged
      // var modified = updateFaqTopic(id, field, output, author);
      modified.ifPresentOrElse(
          faqTopic -> prefix.logged(text("Success! %s of %s (#%s) was modified".formatted(field.name(), faqTopic.topic(), id))),
          () ->       prefix.logged(text("Saving new %s failed?".formatted(field.name()), RED))
      );
    } catch (InterruptedException | ExecutionException e) {
      prefix.logged(text("Reading new %s failed?".formatted(field.name()), RED));
      e.printStackTrace();
    }
  }

  public Component makePreview(String type, @Nullable String body) {
    var section = plugin.section("messages", "manage", "list", "preview", type);

    if (body == null || body.isBlank()) {
      return m.parse(Objects.requireNonNull(section.getString("empty")));
    }

    return m.parse(Objects.requireNonNull(section.getString("label")))
        .hoverEvent(showText(m.parse(body)));
  }


  public String resolveAuthor(UUID author) {
    if (author.equals(NULL_UUID)) return "SYSTEM";
    var player = Bukkit.getOfflinePlayer(author);
    return Objects.requireNonNullElse(player.getName(), "Unknown user " + author);
  }

  public UUID getAuthor(CommandSender sender) {
    return (sender instanceof Player p) ? p.getUniqueId() : NULL_UUID;
  }
}

