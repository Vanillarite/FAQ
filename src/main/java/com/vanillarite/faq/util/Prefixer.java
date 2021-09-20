package com.vanillarite.faq.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.Template;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.vanillarite.faq.FaqPlugin.m;

public class Prefixer {
  private final @NotNull String miniMessagePrefix;
  private final CommandSender sender;
  private @Nullable Component prefix = null;

  public Prefixer(CommandSender sender, @NotNull String miniMessagePrefix) {
    this.sender = sender;
    this.miniMessagePrefix = miniMessagePrefix;
  }

  private Component prefixMemo() {
    if (prefix == null) {
      prefix = m.parse(miniMessagePrefix);
    }
    return prefix;
  }

  private void loggedSend(Component message) {
    sender.sendMessage(message);
    if (sender instanceof Player) {
      Bukkit.getConsoleSender().sendMessage(
          Component.text("[VanillariteFAQ] For " + sender.getName() + ": ").append(message)
      );
    }
  }

  public void logged(Component message) {
    var component = TextComponent.ofChildren(prefixMemo(), message);
    loggedSend(component);
  }

  public void response(Component message) {
    var component = TextComponent.ofChildren(prefixMemo(), message);
    sender.sendMessage(component);
  }

  public void logged(String miniMessage) {
    var component = m.parse(miniMessagePrefix + miniMessage);
    loggedSend(component);
  }

  public Component component(String miniMessage, Template... templates) {
    return m.parse(miniMessagePrefix + miniMessage, templates);
  }

  public void logged(String miniMessage, Template... templates) {
    loggedSend(component(miniMessage, templates));
  }

  public void response(String miniMessage, Template... templates) {
    sender.sendMessage(component(miniMessage, templates));
  }

}

