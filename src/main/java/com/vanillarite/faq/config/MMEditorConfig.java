package com.vanillarite.faq.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.net.URI;

@ConfigSerializable
public record MMEditorConfig(
    URI url
) {
}
