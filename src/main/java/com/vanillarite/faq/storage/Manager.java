package com.vanillarite.faq.storage;

import com.google.gson.JsonElement;
import com.vanillarite.faq.AdventureEditorAPI;
import com.vanillarite.faq.FaqPlugin;
import com.vanillarite.faq.storage.supabase.Field;
import com.vanillarite.faq.storage.supabase.Method;
import com.vanillarite.faq.storage.supabase.SupabaseConnection;
import com.vanillarite.faq.text.list.FaqLister;
import net.kyori.adventure.text.Component;
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
import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.TextComponent.ofChildren;
import static net.kyori.adventure.text.event.ClickEvent.*;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

public class Manager {
  private final FaqPlugin plugin;
  private final FaqCache faqCache;
  public static final UUID NULL_UUID = new UUID(0, 0);

  public Manager(FaqPlugin plugin) {
    this.plugin = plugin;
    this.faqCache = new FaqCache(this::getAllFaqTopics, 60, TimeUnit.MINUTES, plugin);
  }

  public FaqCache cache() {
    return faqCache;
  }

  public SupabaseConnection supabase() {
    var section = plugin.section("supabase");

    return new SupabaseConnection(
        URI.create(Objects.requireNonNull(section.getString("url"))),
        section.getString("anon_key"),
        section.getString("auth_key")
    );
  }

  public boolean assertNoExisting(String candidate) {
    return faqCache.invalidateAndGet().stream().anyMatch(i -> i.topic().equalsIgnoreCase(candidate) || i.alias().stream().anyMatch(a -> a.equalsIgnoreCase(candidate)));
  }

  public Optional<Topic> updateFaqArrayField(int id, Field key, Method method, String entry, CommandSender author) {
    return faqCache.supabasePatchArray(supabase(), id, key, entry, method, getAuthor(author));
  }

  public Optional<Topic> updateFaqComplex(int id, Field key, String value, JsonElement element, CommandSender author) {
    return faqCache.supabasePatchComplex(supabase(), id, key, value, element, getAuthor(author));
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

  public Function<Component, Component> makeEditorLink(boolean noHover, Topic existing, Field field, String placeholder) throws ExecutionException, InterruptedException {
    var editor = plugin.section("mm_editor");
    var editorLink = Objects.requireNonNull(editor.getString("url"));

    var token = new AdventureEditorAPI(URI.create(editorLink)).startSession(
        requireNonBlankElse(existing.findField(field), placeholder),
        "/faqeditor submit %s %s {token}".formatted(String.valueOf(existing.id()), field.name().toLowerCase()),
        "VanillariteFAQ"
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

  public List<Component> makeButtons(Topic faq, Predicate<String> permissionChecker) throws ExecutionException, InterruptedException {
    var section = plugin.section("messages", "manage");
    var rows = new ArrayList<Component>();
    var buttons = new ArrayList<Component>();
    Function<String, Function<Component, Component>> suggest = (name) -> (c) -> c.clickEvent(suggestCommand(name.formatted(faq.id())));
    Function<String, Function<Component, Component>> cmd = (name) -> (c) -> c.clickEvent(runCommand(name.formatted(faq.id())));

    List.of(
        new ButtonType("group", suggest.apply("/faqeditor set %s group ")),
        new ButtonType("rename", suggest.apply("/faqeditor set %s topic ")),
        new ButtonType("move", cmd.apply("/faqeditor set %s positionmenu")),
        new ButtonType("delete", "manage.delete", suggest.apply("/faqeditor delete %s")),
        new ButtonType("history", "admin.history", cmd.apply("/faqeditor admin history %s"))
    ).forEach(i -> {
      if (permissionChecker.test("vfaq." + i.permission())) {
        buttons.add(makeButton(i.name(), i.factory()));
      }
    });
    rows.add(ofChildren(buttons.toArray(new Component[0])));
    buttons.clear();

    if (permissionChecker.test("vfaq.manage.edit.alias")) {
      buttons.add(m.parse(Objects.requireNonNull(section.getString("alias.list")), Template.of("count", String.valueOf(faq.alias().size()))));
      buttons.add(
          m.parse(Objects.requireNonNull(section.getString("alias.add")))
              .clickEvent(suggestCommand("/faqeditor set %s alias add ".formatted(faq.id())))
      );
      faq.alias().forEach(a -> {
            buttons.add(space());
            buttons.add(
                m.parse(Objects.requireNonNull(section.getString("alias.remove")), Template.of("name", a))
                    .clickEvent(suggestCommand("/faqeditor set %s alias remove %s".formatted(faq.id(), a)))
            );
          });
    }
    rows.add(ofChildren(buttons.toArray(new Component[0])));

    return rows;
  }

  public void applyEditor(CommandSender sender, int id, String token, Field field) {
    var editor = plugin.section("mm_editor");
    var editorLink = Objects.requireNonNull(editor.getString("url"));
    var prefix = plugin.prefixFor(sender, "editor");

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

  public Component makePreview(String type, @NotNull Topic topic, @Nullable String body) {
    var section = plugin.section("messages", "manage", "list", "preview", type);

    if (body == null || body.isBlank()) {
      return m.parse(Objects.requireNonNull(section.getString("empty")));
    }

    return m.parse(Objects.requireNonNull(section.getString("label")))
        .hoverEvent(showText(m.parse(body)))
        .clickEvent(runCommand("/faqeditor set %s editor %s".formatted(topic.id(), type)));
  }


  public static Component componentAuthor(UUID author) {
    if (author.equals(NULL_UUID)) return text("SYSTEM", DARK_PURPLE);
    var player = Bukkit.getOfflinePlayer(author).getName();
    if (player == null) {
      return text("Unknown", DARK_RED);
    } else {
      return text(player, GOLD);
    }
  }

  public UUID getAuthor(CommandSender sender) {
    return (sender instanceof Player p) ? p.getUniqueId() : NULL_UUID;
  }

  private record ButtonType(String name, String permission, Function<Component, Component> factory) {
    private ButtonType(String name, Function<Component, Component> factory) {
      this(name, "manage.edit." + name, factory);
    }
  }
}

