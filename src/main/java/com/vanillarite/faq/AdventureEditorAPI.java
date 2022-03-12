package com.vanillarite.faq;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdventureEditorAPI {
  private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"(.*)\"");

  private final URI root;
  private final HttpClient client;

  public AdventureEditorAPI(final @NotNull URI root) {
    this.root = root;
    this.client = HttpClient.newHttpClient();
  }

  public @NotNull CompletableFuture<String> startSession(
      final @NotNull String input,
      final @NotNull String command,
      final @NotNull String application) {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    constructBody(input, command, application), StandardCharsets.UTF_8))
            .header("content-type", "application/json; charset=UTF-8")
            .uri(root.resolve(URI.create("/api/editor/input")))
            .build();
    final CompletableFuture<String> result = new CompletableFuture<>();

    this.client
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            stringHttpResponse -> {
              if (stringHttpResponse.statusCode() != 200) {
                result.completeExceptionally(
                    new IOException(
                        "The server could not handle the request. (%s)"
                            .formatted(stringHttpResponse.statusCode())));
              } else {
                final String body = stringHttpResponse.body();
                final Matcher matcher = TOKEN_PATTERN.matcher(body);

                if (matcher.find()) {
                  final String group = matcher.group(1);
                  result.complete(group);
                }

                result.completeExceptionally(
                    new IOException("The result did not contain a token. (%s)".formatted(body)));
              }
              return null;
            });

    return result;
  }

  public @NotNull CompletableFuture<String> retrieveSession(final @NotNull String token) {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .GET()
            .uri(root.resolve(URI.create("/api/editor/output?token=" + token)))
            .build();
    final CompletableFuture<String> result = new CompletableFuture<>();

    this.client
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            stringHttpResponse -> {
              final int statusCode = stringHttpResponse.statusCode();
              if (statusCode == 404) {
                result.complete(null);
              } else if (statusCode != 200) {
                result.completeExceptionally(
                    new IOException(
                        "The server could not handle the request. (%s)"
                            .formatted(stringHttpResponse.statusCode())));
              } else {
                result.complete(stringHttpResponse.body());
              }
              return null;
            });

    return result;
  }

  private @NotNull String constructBody(
      final @NotNull String input,
      final @NotNull String command,
      final @NotNull String application) {
    var body = new JsonObject();
    body.addProperty("input", input);
    body.addProperty("command", command);
    body.addProperty("application", application);
    return body.toString();
  }
}
