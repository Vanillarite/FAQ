package com.vanillarite.faq.storage;

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
    UUID author,
    Instant createdAt,
    Instant updatedAt
) {
  public static Topic fromJson(JsonObject json) {
    return new Topic(
        json.get("id").getAsInt(),
        json.get("topic").getAsString(),
        json.get("content").getAsString(),
        json.get("preface").isJsonNull() ? null : json.get("preface").getAsString(),
        UUID.fromString(json.get("author").getAsString()),
        OffsetDateTime.parse(json.get("created_at").getAsString()).toInstant(),
        OffsetDateTime.parse(json.get("updated_at").getAsString()).toInstant()
    );
  }

  public String findField(Field field) {
    return switch (field) {
      case CONTENT -> content;
      case PREFACE -> preface;
      case TOPIC -> topic;
    };
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
}
