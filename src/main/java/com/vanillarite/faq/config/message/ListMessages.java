package com.vanillarite.faq.config.message;

import com.vanillarite.faq.config.PrefixKind;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record ListMessages(
    String defaultGroup,
    String groupSeparator,
    ListSection faq,
    ListSection faq4u
) {
  public ListSection get(PrefixKind prefixKind) {
    return switch (prefixKind) {
      case FAQ -> faq;
      case FAQ4U -> faq4u;
      case EDITOR -> throw new IllegalArgumentException();
    };
  }

  @ConfigSerializable
  public record ListSection(
      String header,
      String prefix,
      String eachTopic,
      String hover,
      int maxPerLine
  ) {}
}
