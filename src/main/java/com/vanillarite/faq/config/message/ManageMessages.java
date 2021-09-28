package com.vanillarite.faq.config.message;

import com.vanillarite.faq.storage.supabase.Field;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.Map;

@ConfigSerializable
public record ManageMessages(
    String initialPlaceholder,
    ManageEdit edit,
    ManageList list,
    ManageAlias alias
) {
  @ConfigSerializable
  public record ManageEdit(
      String header,
      Map<ButtonKind, String> button
  ) {}

  @ConfigSerializable
  public record ManageList(
      String header,
      String incomplete,
      String deleted,
      String editLabel,
      String editLabelHover,
      String aliases,
      ListPreview preview,
      ListTopic topic,
      String line,
      String hint
  ) {}

  @ConfigSerializable
  public record ListPreview(
      ListPreviewSection content,
      ListPreviewSection preface
  ) {
    public ListPreviewSection get(Field field) {
      return switch (field) {
        case CONTENT -> content;
        case PREFACE -> preface;
        default -> throw new IllegalArgumentException();
      };
    }
  }

  @ConfigSerializable
  public record ListPreviewSection(
      String label,
      String empty
  ) {}

  @ConfigSerializable
  public record ListTopic(
      String defaultGroup,
      String customGroup,
      String hover
  ) {}

  @ConfigSerializable
  public record ManageAlias(
      String list,
      String add,
      String remove
  ) {}
}
