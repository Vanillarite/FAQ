package com.vanillarite.faq.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.net.URI;
import java.net.URL;

@ConfigSerializable
public record SupabaseConfig(
    URI url,
    String anonKey,
    String authKey
) {
}
