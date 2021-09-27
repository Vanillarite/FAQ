package com.vanillarite.faq.storage;

import com.google.common.collect.Streams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vanillarite.faq.storage.supabase.Field;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record Topic(
    int id,
    String topic,
    String content,
    @Nullable String preface,
    List<String> alias,
    String group,
    Pos pos,
    UUID author,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
  public static Topic fromJson(JsonObject json) {
    return new Topic(
        json.get("id").getAsInt(),
        json.get("topic").getAsString(),
        json.get("content").getAsString(),
        json.get("preface").isJsonNull() ? null : json.get("preface").getAsString(),
        Streams.stream(json.get("alias").getAsJsonArray().iterator()).map(JsonElement::getAsString).toList(),
        json.get("group").getAsString(),
        new Pos(
            json.get("pos").getAsJsonObject().get("line").getAsInt(),
            json.get("pos").getAsJsonObject().get("col").getAsInt()
        ),
        UUID.fromString(json.get("author").getAsString()),
        json.get("active").getAsBoolean(),
        OffsetDateTime.parse(json.get("created_at").getAsString()).toInstant(),
        OffsetDateTime.parse(json.get("updated_at").getAsString()).toInstant()
    );
  }

  public String findField(Field field) {
    return switch (field) {
      case CONTENT -> content;
      case PREFACE -> preface;
      case TOPIC -> topic;
      case ALIAS -> alias.toString();
      case GROUP -> group;
      case POS -> pos.tuple();
      // default -> throw new IllegalArgumentException("%s isn't implemented".formatted(field));
    };
  }

  public ArrayList<String> findArrayField(Field field) {
    return new ArrayList<>(switch (field) {
      case ALIAS -> alias;
      default -> throw new IllegalArgumentException("%s isn't an array field".formatted(field));
    });
  }

  public SmartPreface smartPreface(int maxLines) {
    if (preface != null && !preface.isBlank()) {
      return new SmartPreface(true, preface.split("\n"));
    } else {
      var fullBody = content.split("\n");
      if (fullBody.length <= maxLines) {
        return new SmartPreface(false, fullBody);
      } else {
        var trim = new ArrayList<String>();
        var i = 0;
        for (String s : content.split("\n")) {
          if (i++ < maxLines) {
            trim.add(s);
          }
        }
        return new SmartPreface(true, true, trim);
      }
    }
  }

  public record SmartPreface(boolean isPreview, boolean isContinuable, List<String> lines) {
    public SmartPreface(boolean isContinuable, String[] lines) {
      this(false, isContinuable, Arrays.asList(lines));
    }
  }

  public record Pos(int line, int col) {
    public JsonObject toJson() {
      var jsonObject = new JsonObject();
      jsonObject.addProperty("line", line);
      jsonObject.addProperty("col", col);
      return jsonObject;
    }

    public String tuple() {
      return "(%s,%s)".formatted(line, col);
    }

    public static Pos fromTuple(String tuple) {
      var split = tuple.substring(1, tuple.length() - 1).split(",");
      return new Pos(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }
  }
}

