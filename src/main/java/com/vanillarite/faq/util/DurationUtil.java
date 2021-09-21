package com.vanillarite.faq.util;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DurationUtil {
  private DurationUtil() {}

  public static String formatDuration(@NotNull Duration duration, int specificity) {
    if (duration.isNegative()) return "THIS IS AN ERROR PLEASE REPORT IT";
    if (duration.isZero()) return "one moment";

    List<String> s = new ArrayList<>();
    String delimiter = " ";

    long iDays = duration.toDaysPart();
    if (iDays > 0) s.add(String.format("%dd", iDays));

    int iHours = duration.toHoursPart();
    if (iHours > 0) s.add(String.format("%dh", iHours));

    int iMinutes = duration.toMinutesPart();
    if (iMinutes > 0) s.add(String.format("%dm", iMinutes));

    int iSeconds = duration.toSecondsPart();
    if (iSeconds > 0) s.add(String.format("%ds", iSeconds));

    return s.stream().limit(specificity).collect(Collectors.joining(delimiter));
  }

  public static String formatDuration(@NotNull Duration duration) {
    return formatDuration(duration, 4);
  }

  public static String formatInstantToNow(Instant instant) {
    return formatDuration(Duration.between(instant, Instant.now()));
  }

}
