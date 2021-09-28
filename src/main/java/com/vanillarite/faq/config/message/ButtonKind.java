package com.vanillarite.faq.config.message;

import net.kyori.adventure.text.Component;

import java.util.function.Function;

public enum ButtonKind {
  RENAME, MOVE, GROUP, HISTORY, DELETE;

  public ButtonType type(String permission, Function<Component, Component> factory) {
    return new ButtonType(this, permission, factory);
  }

  public ButtonType type(Function<Component, Component> factory) {
    return new ButtonType(this, factory);
  }

  public record ButtonType(ButtonKind name, String permission, Function<Component, Component> factory) {
    public ButtonType(ButtonKind name, Function<Component, Component> factory) {
      this(name, "manage.edit." + name, factory);
    }
  }
}
