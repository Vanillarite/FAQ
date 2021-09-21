plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
    java
}

group = "com.vanillarite"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.incendo.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("io.papermc.paper", "paper-api", "1.17.1-R0.1-SNAPSHOT")
    implementation("cloud.commandframework", "cloud-paper", "1.5.0")
    implementation("cloud.commandframework", "cloud-annotations", "1.5.0")
    implementation("net.kyori", "adventure-text-minimessage", "4.2.0-SNAPSHOT") {
        exclude("net.kyori", "adventure-api")
    }
    implementation("net.kyori", "adventure-text-feature-pagination", "4.0.0-SNAPSHOT")
    implementation("io.github.java-diff-utils", "java-diff-utils", "4.5")
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

tasks {
    shadowJar {
        dependencies {
            exclude(dependency("com.google.guava:"))
            exclude(dependency("com.google.errorprone:"))
            exclude(dependency("org.checkerframework:"))
            exclude(dependency("org.jetbrains:"))
            exclude(dependency("org.intellij:"))
        }

        relocate("cloud.commandframework", "${rootProject.group}.faq.shade.cloud")
        relocate("io.leangen.geantyref", "${rootProject.group}.faq.shade.typetoken")
        relocate("net.kyori.adventure.text.minimessage", "${rootProject.group}.faq.shade.minimessage")
        relocate("com.github.difflib", "${rootProject.group}.faq.shade.difflib")

        archiveClassifier.set(null as String?)
        archiveFileName.set(project.name + ".jar")
        destinationDirectory.set(rootProject.tasks.shadowJar.get().destinationDirectory.get())
    }
    build {
        dependsOn(shadowJar)
    }

    withType<ProcessResources> {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(project.properties)
        }
    }
}
