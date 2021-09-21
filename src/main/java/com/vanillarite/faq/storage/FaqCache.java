package com.vanillarite.faq.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vanillarite.faq.FaqPlugin;
import com.vanillarite.faq.storage.supabase.Field;
import com.vanillarite.faq.storage.supabase.Method;
import com.vanillarite.faq.storage.supabase.SupabaseConnection;
import com.vanillarite.faq.util.SingleCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class FaqCache extends SingleCache<ArrayList<Topic>> {
  private final FaqPlugin plugin;
  public FaqCache(Callable<ArrayList<Topic>> supplier, long duration, TimeUnit unit, FaqPlugin plugin) {
    super(supplier, duration, unit);
    this.plugin = plugin;
  }

  public Topic findNow(int id) {
    return invalidateAndGet().stream().filter(i -> i.id() == id).findFirst().orElseThrow();
  }

  public Optional<Topic> findIgnoreEmpty(String topic) {
    return get().stream().filter(i -> i.topic().equalsIgnoreCase(topic)).filter(i -> !i.content().isBlank()).findFirst();
  }

  public Stream<Topic> getIgnoreEmpty() {
    return get().stream().filter(i -> !i.content().isBlank());
  }

  @Override
  public void invalidate() {
    plugin.debug("FAQ List cache was manually invalidated");
    super.invalidate();
  }

  public Optional<ArrayList<History>> supabaseGetHistoryForFaq(SupabaseConnection sb, int forFaq) {
    try {
      HttpRequest faqListRequest = sb.request("history?faq=eq." + forFaq).GET().build();
      HttpResponse<InputStream> faqList = HttpClient.newHttpClient()
          .send(faqListRequest, HttpResponse.BodyHandlers.ofInputStream());

      var jp = new JsonParser();
      JsonElement root = jp.parse(new InputStreamReader(faqList.body(), StandardCharsets.UTF_8));
      JsonArray faqListObject = root.getAsJsonArray();

      var history = new ArrayList<History>();
      faqListObject.forEach((json) -> history.add(History.fromJson(json.getAsJsonObject())));
      return Optional.of(history);
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  public Optional<History> supabaseGetHistorySingle(SupabaseConnection sb, int id) {
    try {
      HttpRequest faqListRequest = sb.single("history?id=eq." + id).GET().build();
      HttpResponse<InputStream> faqList = HttpClient.newHttpClient()
          .send(faqListRequest, HttpResponse.BodyHandlers.ofInputStream());

      var jp = new JsonParser();
      JsonElement root = jp.parse(new InputStreamReader(faqList.body(), StandardCharsets.UTF_8));
      JsonObject newFaqObject = root.getAsJsonObject();

      return Optional.of(History.fromJson(newFaqObject));
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  public Optional<ArrayList<Topic>> supabaseGet(SupabaseConnection sb) {
    try {
      HttpRequest faqListRequest = sb.request("faqs?active=is.true").GET().build();
      HttpResponse<InputStream> faqList = HttpClient.newHttpClient()
          .send(faqListRequest, HttpResponse.BodyHandlers.ofInputStream());

      var jp = new JsonParser();
      JsonElement root = jp.parse(new InputStreamReader(faqList.body(), StandardCharsets.UTF_8));
      JsonArray faqListObject = root.getAsJsonArray();

      var topics = new ArrayList<Topic>();
      faqListObject.forEach((json) -> topics.add(Topic.fromJson(json.getAsJsonObject())));
      return Optional.of(topics);
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  public void logHistory(SupabaseConnection sb, int id, Method method, Field field, String before, String after, UUID author) throws IOException, InterruptedException {
    var body = new JsonObject();
    body.addProperty("faq", id);
    body.addProperty("author", author.toString());
    body.addProperty("method", method.name());
    body.addProperty("field", field.name());
    body.addProperty("before", before);
    body.addProperty("after", after);

    HttpRequest faqListRequest = sb.request("history")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();
    HttpResponse<String> faqList = HttpClient.newHttpClient()
        .send(faqListRequest, HttpResponse.BodyHandlers.ofString());

    if (faqList.statusCode() != 201) throw new IllegalStateException("Couldn't log operation, got %s - %s".formatted(faqList.statusCode(), faqList.body()));

    plugin.debug("FAQ modification has been logged: #%s %s %s by %s; %s chars -> %s chars".formatted(id, method, field, author, before.length(), after.length()));
  }

  public Optional<Topic> supabasePatch(SupabaseConnection sb, int id, Field key, String newValue, UUID author) {
    try {
      var existing = findNow(id);
      logHistory(sb, id, Method.PATCH, key, existing.findField(key), newValue, author);

      var body = new JsonObject();
      body.addProperty(key.name().toLowerCase(), newValue);

      HttpRequest faqListRequest = sb.single("faqs?active=is.true&id=eq." + id)
          .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
          .build();
      HttpResponse<InputStream> faqList = HttpClient.newHttpClient()
          .send(faqListRequest, HttpResponse.BodyHandlers.ofInputStream());

      if (faqList.statusCode() >= 300) {
        throw new IllegalStateException("Failed to patch? Got %s - %s".formatted(faqList.statusCode(), new String(faqList.body().readAllBytes())));
      }

      var jp = new JsonParser();
      JsonElement root = jp.parse(new InputStreamReader(faqList.body(), StandardCharsets.UTF_8));
      JsonObject newFaqObject = root.getAsJsonObject();

      invalidate();
      return Optional.of(Topic.fromJson(newFaqObject));
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }


  public Optional<Topic> supabasePost(SupabaseConnection sb, String topic, UUID author) {
    try {
      var body = new JsonObject();
      body.addProperty("topic", topic);
      body.addProperty("content", "");
      body.addProperty("author", author.toString());

      HttpRequest faqListRequest = sb.single("faqs")
          .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
          .build();
      HttpResponse<InputStream> faqList = HttpClient.newHttpClient()
          .send(faqListRequest, HttpResponse.BodyHandlers.ofInputStream());

      if (faqList.statusCode() >= 300) {
        throw new IllegalStateException("Failed to post? Got %s - %s".formatted(faqList.statusCode(), new String(faqList.body().readAllBytes())));
      }

      var jp = new JsonParser();
      JsonElement root = jp.parse(new InputStreamReader(faqList.body(), StandardCharsets.UTF_8));
      JsonObject newFaqObject = root.getAsJsonObject();

      var newTopic = Topic.fromJson(newFaqObject);
      // logging must be done after because we don't know the ID yet
      logHistory(sb, newTopic.id(), Method.POST, Field.TOPIC, "", topic, author);

      invalidate();
      return Optional.of(newTopic);
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  public boolean supabaseDelete(SupabaseConnection sb, int id, UUID author) {
    try {
      var existing = findNow(id);
      logHistory(sb, id, Method.DELETE, Field.TOPIC, existing.topic(), "", author);
      logHistory(sb, id, Method.DELETE, Field.CONTENT, existing.content(), "", author);
      logHistory(sb, id, Method.DELETE, Field.PREFACE, existing.preface(), "", author);

      var body = new JsonObject();
      body.addProperty("active", false);

      HttpRequest faqListRequest = sb.single("faqs?active=is.true&id=eq." + id)
          .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
          .build();
      HttpResponse<String> faqList = HttpClient.newHttpClient()
          .send(faqListRequest, HttpResponse.BodyHandlers.ofString());

      if (faqList.statusCode() >= 300) {
        throw new IllegalStateException("Failed to delete? Got %s - %s".formatted(faqList.statusCode(), faqList.body()));
      }

      invalidate();
      return true;
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return false;
    }
  }
}
