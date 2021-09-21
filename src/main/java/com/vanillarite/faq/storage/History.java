package com.vanillarite.faq.storage;

import com.google.gson.JsonObject;
import com.vanillarite.faq.storage.supabase.Field;
import com.vanillarite.faq.storage.supabase.Method;
import com.vanillarite.faq.util.DurationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static com.vanillarite.faq.FaqPlugin.m;
import static com.vanillarite.faq.storage.Manager.NULL_UUID;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.Style.style;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

@SuppressWarnings("UnstableApiUsage")
public record History(
    int id,
    int faq,
    UUID author,
    Method method,
    Field field,
    @Nullable String before,
    String after,
    Instant timestamp
) {
  public static History fromJson(JsonObject json) {
    return new History(
        json.get("id").getAsInt(),
        json.get("faq").getAsInt(),
        UUID.fromString(json.get("author").getAsString()),
        Method.valueOf(json.get("method").getAsString()),
        Field.valueOf(json.get("field").getAsString()),
        json.get("before").isJsonNull() ? null : json.get("before").getAsString(),
        json.get("after").getAsString(),
        OffsetDateTime.parse(json.get("timestamp").getAsString()).toInstant()
    );
  }

  private static final char[] smallNumbers = "₀₁₂₃₄₅₆₇₈₉".toCharArray();

  public @NotNull String beforeOrBlank() {
    return Objects.requireNonNullElse(before, "");
  }

  public Component fieldAwareContent(Supplier<String> supplier) {
    return fieldAwareContent(supplier, false);
  }

  public Component fieldAwareContent(Supplier<String> supplier, boolean concise) {
    if (field == Field.TOPIC) {
      return text(supplier.get(), style(ITALIC));
    } else {
      return text((concise ? "%s" : "%s chars").formatted(supplier.get().length()), style(ITALIC)).hoverEvent(showText(m.parse(supplier.get())));
    }
  }

  public Component asComponent() {
    var component = TextComponent.ofChildren(
        text("#", GRAY),
        text(asSmallNumbers(id, 4)),
        text(": ", GRAY),
        toComponent(method),
        toComponent(field),
        space(),
        differenceComponent(),
        text(" by ", GRAY),
        componentAuthor().hoverEvent(showText(text(author.toString()))),
        space(),
        text(DurationUtil.formatInstantToNow(timestamp), TextColor.fromHexString("#BDF9FC")),
        text(" ago", GRAY)
    );
    if (field != Field.TOPIC && method == Method.PATCH && !beforeOrBlank().isBlank() && !after.isBlank()) {
      component = component.append(
          text(" [inspect]", RED)
              .hoverEvent(showText(text("Click to inspect differences")))
              .clickEvent(runCommand("/faqeditor admin inspect %s".formatted(id)))
      );
    }
    return component;
  }

  public Component componentAuthor() {
    if (author.equals(NULL_UUID)) return text("SYSTEM", DARK_PURPLE);
    var player = Bukkit.getOfflinePlayer(author).getName();
    if (player == null) {
      return text("Unknown", DARK_RED);
    } else {
      return text(player, GOLD);
    }
  }

  public String asSmallNumbers(int integer, int pad) {
    var s = new StringBuilder();
    for (char c : String.format("%0" + pad + "d", integer).toCharArray()) {
      s.append(smallNumbers[c - '0']);
    }
    return s.toString();
  }

  private Component differenceComponent() {
    if (after.length() == 0) {
      return TextComponent.ofChildren(
          fieldAwareContent(this::beforeOrBlank),
          text(" erased", WHITE, BOLD, ITALIC)
      );
    } else if (beforeOrBlank().length() == 0)  {
      return TextComponent.ofChildren(
          text("new ", GRAY),
          fieldAwareContent(this::after)
      );
    } else {
      return TextComponent.ofChildren(
          fieldAwareContent(this::beforeOrBlank, true),
          text(" → ", GRAY),
          fieldAwareContent(this::after)
      );
    }
  }

  private Component symbolic(String symbol, String full, TextColor color) {
    return text().content(symbol).hoverEvent(showText(text(full, color))).color(color).build();
  }

  private Component toComponent(Method method) {
    return switch (method) {
      case POST -> symbolic("☑", "Create new", NamedTextColor.GREEN);
      case PATCH -> symbolic("☐", "Edit", NamedTextColor.GOLD);
      case DELETE -> symbolic("☒", "Delete", NamedTextColor.RED);
    };
  }

  private Component toComponent(Field field) {
    return switch (field) {
      case CONTENT -> symbolic("Ⓒ", "Content", NamedTextColor.AQUA);
      case PREFACE -> symbolic("Ⓟ", "Preface", NamedTextColor.DARK_AQUA);
      case TOPIC -> symbolic("Ⓣ", "Topic", NamedTextColor.YELLOW);
    };
  }
}
