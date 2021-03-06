package com.vanillarite.faq.text.list;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.vanillarite.faq.FaqPlugin;
import com.vanillarite.faq.config.PrefixKind;
import com.vanillarite.faq.config.message.ListMessages;
import com.vanillarite.faq.storage.FaqCache;
import com.vanillarite.faq.storage.Topic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.vanillarite.faq.FaqPlugin.m;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.event.HoverEvent.showText;

public final class FaqLister {
  public final ListMessages.ListSection section;
  private final String command;
  private final FaqCache cache;
  private final Predicate<String> permissionCheck;
  private final Consumer<Component> lineConsumer;
  @Nullable private final BiFunction<Topic, Component, Component> topicCallback;
  private final ListMessages generalSection;
  private final ArrayList<Component> topics = new ArrayList<>();
  private final HashMap<String, Boolean> knownGroups = new HashMap<>();
  private final String defaultGroup;
  private final int maxPerLine;

  public FaqLister(
      PrefixKind style,
      String command,
      FaqPlugin plugin,
      FaqCache cache,
      Predicate<String> permissionCheck,
      Consumer<Component> lineConsumer) {
    this(style, command, plugin, cache, permissionCheck, lineConsumer, null);
  }

  public FaqLister(
      PrefixKind style,
      String command,
      FaqPlugin plugin,
      FaqCache cache,
      Predicate<String> permissionCheck,
      Consumer<Component> lineConsumer,
      @Nullable BiFunction<Topic, Component, Component> topicCallback) {
    this.command = command;
    this.cache = cache;
    this.permissionCheck = permissionCheck;
    this.lineConsumer = lineConsumer;
    this.topicCallback = topicCallback;
    this.section = plugin.config().messages().list().get(style);
    this.generalSection = plugin.config().messages().list();
    this.defaultGroup = generalSection.defaultGroup();
    this.maxPerLine = section.maxPerLine();
  }

  private void emitLine() {
    if (topics.size() > 0) {
      var line = empty().append(m.deserialize(section.prefix()));
      for (var t : topics) line = line.append(t);
      lineConsumer.accept(line);
      topics.clear();
    }
  }

  private boolean checkGroup(String groupName) {
    if (groupName.equals(defaultGroup)) return true;
    if (knownGroups.containsKey(groupName)) return knownGroups.get(groupName);
    var checkResult = permissionCheck.test(groupName);
    knownGroups.put(groupName, checkResult);
    return checkResult;
  }

  private Component addTopic(Topic faq) {
    var c =
        m.deserialize(section.eachTopic(), Placeholder.unparsed("topic", faq.topic()))
            .clickEvent(ClickEvent.runCommand("/" + command + " " + faq.topic()))
            .hoverEvent(
                showText(
                    m.deserialize(section.hover(), Placeholder.unparsed("topic", faq.topic()))));
    if (topicCallback != null) c = topicCallback.apply(faq, c);
    return c;
  }

  public void emitTopicGroup(Collection<Topic> group, Function<Topic, Component> topicFun) {
    var knownRows = new HashMap<Integer, Collection<Topic>>();
    var autoPos = new ArrayList<Topic>();
    Consumer<Topic> topicConsumer = (t) -> topics.add(topicFun.apply(t));
    group.forEach(
        t -> {
          if (t.pos().line() == 0) {
            autoPos.add(t);
          } else {
            knownRows.computeIfAbsent(t.pos().line(), k -> new ArrayList<>()).add(t);
          }
        });
    knownRows.entrySet().stream()
        .sorted(Comparator.comparingInt(Map.Entry::getKey))
        .forEachOrdered(
            i -> {
              i.getValue().stream()
                  .sorted(Comparator.comparingInt(j -> j.pos().col()))
                  .forEachOrdered(topicConsumer);
              emitLine();
            });
    emitLine();
    autoPos.forEach(
        (faq) -> {
          if (topics.size() + 1 > maxPerLine) emitLine();
          topicConsumer.accept(faq);
        });
    emitLine();
  }

  public void run() {
    lineConsumer.accept(m.deserialize(section.header()));

    topicsByGroup().asMap().entrySet().stream()
        .sorted(
            (a, b) ->
                Comparator.<String, Boolean>comparing(defaultGroup::equals)
                    .reversed()
                    .thenComparing(Comparator.naturalOrder())
                    .compare(a.getKey(), b.getKey()))
        .forEachOrdered(
            (topicGroup) -> {
              if (!topicGroup.getKey().equals(defaultGroup)) {
                lineConsumer.accept(
                    m.deserialize(
                        Objects.requireNonNull(generalSection.groupSeparator()),
                        Placeholder.unparsed("group", topicGroup.getKey())));
              }
              emitTopicGroup(topicGroup.getValue(), this::addTopic);
            });

    lineConsumer.accept(empty());
  }

  public Collection<Topic> topicGroupOf(String group) {
    return topicsByGroup().get(group);
  }

  private Multimap<String, Topic> topicsByGroup() {
    Multimap<String, Topic> topicsByGroup = MultimapBuilder.hashKeys().arrayListValues().build();

    cache
        .getIgnoreEmpty()
        .forEach(
            (faq) -> {
              if (checkGroup(faq.group())) topicsByGroup.put(faq.group(), faq);
            });

    return topicsByGroup;
  }
}
