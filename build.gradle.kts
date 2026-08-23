import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "ai.rever.boss.plugin.dynamic"
version = "1.2.5"

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
 * Where a locally built api jar would be. Resolved to a plain [File] at configuration time
 * so the provider below captures *it* rather than the script object.
 *
 * A top-level `fun` here would compile to a method on the script, so the lambda would close
 * over `Project` - which the configuration cache cannot serialise. That is an error, not
 * merely an undeclared input, and it would have been introduced by the very commit that
 * cited the configuration cache as its motivation. Moot today (this repo does not enable
 * CC) and cheap to keep correct.
 */
val bossPluginApiLibsDir: File = layout.projectDirectory.dir("$bossPluginApiPath/build/libs").asFile

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
 * at the cost of a much wider blast radius. Both the lookup and the error are deferred, so
 * this resolves when a task actually needs the jar rather than when Gradle configures.
 *
 * NOTE: plugin.json declares apiVersion 1.0.20, and the reason is now the retirement rather
 * than a symbol. The logging and scrollbar packages this comment used to cite went with the
 * list; what is left uses `DynamicPlugin`, `PanelInfo` and `PanelComponentWithUI`, all of which
 * long predate the floor. The floor stays low because this release has to reach *every* host
 * that still shows the old panel - raising it makes the updater skip exactly those installs.
 * Pinned by `RetirementManifestTest`.
 */
val bossPluginApiJar: FileCollection =
    if (useLocalDependencies) {
        files(
            providers.provider {
                // Newest-by-mtime, not by version string: 1.0.9 sorts above 1.0.71
                // lexicographically and the jar you just built is the one you meant. (The
                // tradeoff is deliberate - checking out an older api tag and building it in
                // the sibling silently makes that the compile target.)
                //
                // The classifier exclusions mirror CI's `--pattern 'boss-plugin-api-*[0-9].jar'`,
                // which also filters -javadoc and -all. Without that, publishing either would
                // let this pick a javadoc jar and produce a baffling "Unresolved reference".
                bossPluginApiLibsDir
                    .listFiles { f: File -> f.name.startsWith("boss-plugin-api-") && f.name.endsWith(".jar") }
                    ?.filterNot {
                        it.name.contains("-sources") ||
                            it.name.contains("-thin") ||
                            it.name.contains("-javadoc") ||
                            it.name.contains("-all")
                    }
                    ?.maxByOrNull { it.lastModified() }
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

    // Tests. This plugin had none, which is how `my_secret_get` shipped without the
    // AI-provider refusal its sibling tool carries. What is left asserts the manifest
    // facts the retirement rests on; nothing here needs a host or a live credential,
    // and nothing suspends any more (the coroutines dependency went with the ViewModel).
    testImplementation(kotlin("test"))
    // The api is compileOnly (the host supplies it at runtime), so it is absent from the
    // test runtime by default. Tests need it on the classpath explicitly - PanelId and the
    // Panel slot helpers are read directly.
    testImplementation(bossPluginApiJar)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // An *independent* source of truth for the version assertion. Comparing the reported
    // version against the bundled plugin.json is circular - both read the same file, so any
    // value in it passes, including a stale one. This comes from Gradle instead, so it also
    // catches a processResources that did not stamp. Same reasoning as secret-manager's.
    systemProperty("boss.plugin.expectedVersion", version.toString())
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
