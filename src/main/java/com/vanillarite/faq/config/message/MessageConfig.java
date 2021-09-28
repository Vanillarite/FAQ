package com.vanillarite.faq.config.message;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record MessageConfig(
    String header,
    String unknownTopic,
    String keepReading,
    String keepReadingHover,
    int maxPreviewLines,
    ListMessages list,
    ManageMessages manage
) {

}
