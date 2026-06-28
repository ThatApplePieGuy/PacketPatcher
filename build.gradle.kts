plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.3.1"
}

group = "com.github.thatapplepieguy"
version = "1.1"

repositories {
    mavenCentral()
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.pgm.fyi/snapshots")
}

val sportPaperServer by configurations.creating

dependencies {
    compileOnly("app.ashcon:sportpaper:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")

    sportPaperServer("app.ashcon:sportpaper:1.8.8-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        minecraftVersion("1.8.8")

        environment("PACKETPATCHER_DEBUG", "true")

        serverJar(layout.file(sportPaperServer.elements.map { it.single().asFile }))

        downloadPlugins {
            modrinth("packetevents", "2.13.0+spigot")
            modrinth("viaversion", "5.10.0")
        }
    }
}

bukkitPluginYaml {
    main = "com.github.thatapplepieguy.packetpatcher.PacketPatcher"
    version = project.version.toString()
    depend = listOf("packetevents")
}
