import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "ai.rever.boss.plugin.dynamic"
version = "1.2.4"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Auto-detect CI environment
val useLocalDependencies = System.getenv("CI") != "true"
val bossPluginApiPath = "../boss-plugin-api"

/**
 * The most recently built api jar in the sibling checkout, whatever its version.
 *
 * Local development only - CI uses the downloaded jar. Deliberately not a hardcoded file
 * name: that goes stale on every api release and surfaces as "Unresolved reference" on a
 * symbol that plainly exists. This file pinned 1.0.51, which no longer exists locally.
 * Newest-by-mtime rather than by version string, because 1.0.9 sorts above 1.0.71
 * lexicographically and the jar you just built is the one you meant. Same block as
 * secret-manager's, which this plugin shadows in every other respect.
 */
val localBossPluginApiJar: File? =
    file("$bossPluginApiPath/build/libs")
        .listFiles { f: File -> f.name.startsWith("boss-plugin-api-") && f.name.endsWith(".jar") }
        ?.filterNot { it.name.contains("-sources") || it.name.contains("-thin") }
        ?.maxByOrNull { it.lastModified() }

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

/**
 * The api jar, resolved lazily.
 *
 * `files(provider { ... })` rather than `files(jar ?: error(...))`: the latter runs at
 * CONFIGURATION time, so a checkout without a built sibling api could no longer run
 * `./gradlew tasks`, `./gradlew clean`, or complete an IDE sync - a better message bought
 * at the cost of a much wider blast radius. Deferred, the error surfaces only when a task
 * actually needs the jar.
 *
 * NOTE: plugin.json declares apiVersion 1.0.20 - the ai.rever.boss.plugin.logging and
 * .scrollbar packages used by this plugin were introduced in exactly that release (api tag
 * v1.0.20), so the declared minimum is accurate.
 */
val bossPluginApiJar: FileCollection =
    if (useLocalDependencies) {
        files(
            provider {
                localBossPluginApiJar
                    ?: error("No boss-plugin-api jar in $bossPluginApiPath/build/libs; build it there first.")
            },
        )
    } else {
        files("build/downloaded-deps/boss-plugin-api.jar")
    }

dependencies {
    compileOnly(bossPluginApiJar)

    // Compose dependencies
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)

    // Compose Icons (FeatherIcons)
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")
    
    // Decompose for ComponentContext
    implementation("com.arkivanov.decompose:decompose:3.3.0")
    implementation("com.arkivanov.essenty:lifecycle:2.5.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Tests. This plugin had none, which is how `my_secret_get` shipped without the
    // AI-provider refusal its sibling tool carries. These run without a host or a
    // live credential.
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // BossLogger binds slf4j at class-init, so a backend is required or every class
    // holding a logger fails with NoClassDefFoundError in tests. The host provides one
    // at runtime; tests have to supply their own.
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    // The api is compileOnly (the host supplies it at runtime), so it is absent from the
    // test runtime by default. Tests need it on the classpath explicitly.
    testImplementation(bossPluginApiJar)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Task to build plugin JAR with compiled classes only
tasks.register<Jar>("buildPluginJar") {
    archiveFileName.set("boss-plugin-user-secret-list-${version}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes(
            "Implementation-Title" to "BOSS My Secrets Plugin",
            "Implementation-Version" to version,
            "Main-Class" to "ai.rever.boss.plugin.dynamic.usersecretlist.UserSecretListDynamicPlugin"
        )
    }
    
    // Include compiled classes
    from(sourceSets.main.get().output)
    
    // Include plugin manifest
    from("src/main/resources")
}

// Sync version from build.gradle.kts into plugin.json (single source of truth)
tasks.processResources {
    inputs.property("pluginVersion", version)
    filesMatching("**/plugin.json") {
        filter { line ->
            line.replace(Regex(""""version"\s*:\s*"[^"]*""""), """"version": "\$version"""")
        }
    }
}

tasks.build {
    dependsOn("buildPluginJar")
}

// Fat JAR for out-of-process plugin execution
tasks.register<Jar>("shadowJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to "ai.rever.boss.plugin.runtime.PluginProcessMainKt"
        )
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    from(sourceSets.main.get().output)
    from("src/main/resources")
}
