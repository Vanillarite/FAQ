package com.vanillarite.faq.storage.supabase;

import java.net.URI;
import java.net.http.HttpRequest;

public record SupabaseConnection(URI uri, String anonKey, String authKey) {
  public HttpRequest.Builder request(String table) {
    return HttpRequest.newBuilder()
        .uri(uri.resolve("/rest/v1/" + table))
        .header("apikey", anonKey)
        .header("authorization", "Bearer " + authKey)
        .header("content-type", "application/json");
  }

  public HttpRequest.Builder single(String table) {
    return request(table)
        .header("prefer", "return=representation")
        .header("accept", "application/vnd.pgrst.object+json");
  }
}

