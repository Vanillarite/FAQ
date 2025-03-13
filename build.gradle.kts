plugins {
    id("com.gradleup.shadow") version "8.3.1"
    java
}

val buildNum = System.getenv("CI_PIPELINE_IID") ?: "dirty"
group = "com.vanillarite"
version = "0.4.0-$buildNum"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.incendo.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("io.papermc.paper", "paper-api", "1.21.4-R0.1-SNAPSHOT")
    implementation("org.incendo", "cloud-paper", "2.0.0-beta.10")
    implementation("org.incendo", "cloud-annotations", "2.0.0")
    implementation("io.github.java-diff-utils", "java-diff-utils", "4.5")
    implementation("org.spongepowered", "configurate-yaml", "4.1.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
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
        relocate("com.github.difflib", "${rootProject.group}.faq.shade.difflib")
        relocate("org.spongepowered.configurate", "${rootProject.group}.faq.shade.configurate")

        archiveClassifier.set(null as String?)
        destinationDirectory.set(rootProject.tasks.shadowJar.get().destinationDirectory.get())
    }
    build {
        dependsOn(shadowJar)
    }

    withType<ProcessResources> {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to version)
        }
    }
}
