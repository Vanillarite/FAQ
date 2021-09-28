package com.vanillarite.faq.config;

import com.vanillarite.faq.config.message.MessageConfig;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.Map;

@ConfigSerializable
public record Config(
    boolean debug,
    Map<PrefixKind, String> prefix,
    MessageConfig messages,
    SupabaseConfig supabase,
    MMEditorConfig mmEditor
) {

}
