package com.vanillarite.faq.storage;

import com.google.gson.JsonObject;
import com.vanillarite.faq.storage.supabase.Field;
import com.vanillarite.faq.storage.supabase.Method;
import com.vanillarite.faq.util.DurationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static com.vanillarite.faq.FaqPlugin.m;
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
    if (field == Field.POS) {
      var pos = Topic.Pos.fromTuple(supplier.get());
      if (pos.line() == 0)
        return text("automatic", GRAY, ITALIC);
      else
        return text("(%s, %s)".formatted(pos.line(), pos.col()));
    } else if (field == Field.TOPIC || field == Field.ALIAS || field == Field.GROUP) {
      return text(supplier.get(), style(ITALIC));
    } else {
      return text((concise ? "%s" : "%s chars").formatted(supplier.get().length()), style(ITALIC)).hoverEvent(showText(m.parse(supplier.get())));
    }
  }

  public Component asComponent() {
    var component = TextComponent.ofChildren(
        text("#", GRAY),
        text(String.format("%04d", id)),
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
    if ((field == Field.PREFACE || field == Field.CONTENT) && method == Method.PATCH && !beforeOrBlank().isBlank() && !after.isBlank()) {
      component = component.append(
          text(" [inspect]", RED)
              .hoverEvent(showText(text("Click to inspect differences")))
              .clickEvent(runCommand("/faqeditor admin inspect %s".formatted(id)))
      );
    }
    return component;
  }

  public Component componentAuthor() {
    return Manager.componentAuthor(author);
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
      case POST -> symbolic("☑", "Create new", GREEN);
      case PATCH -> symbolic("☐", "Edit", GOLD);
      case DELETE -> symbolic("☒", "Delete", RED);
    };
  }

  private Component toComponent(Field field) {
    return switch (field) {
      case CONTENT -> symbolic("Ⓒ", "Content", AQUA);
      case PREFACE -> symbolic("Ⓟ", "Preface", DARK_AQUA);
      case TOPIC -> symbolic("Ⓣ", "Topic", YELLOW);
      case ALIAS -> symbolic("Ⓐ", "Alias", WHITE);
      case GROUP -> symbolic("Ⓖ", "Group", LIGHT_PURPLE);
      case POS -> symbolic("Ⓟ", "Position", GOLD);
    };
  }
}
