/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
    version = libs.versions.mod.version.get()
    group = providers.gradleProperty("maven_group").get()
}

repositories {
    mavenCentral()
    maven {
        name = "modrinth"
        url = uri("https://api.modrinth.com/maven")
        content { includeGroup("maven.modrinth") }
    }
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    // Meteor
    implementation(libs.meteor.client)

    // Baritone
    compileOnly(libs.baritone)
    runtimeOnly(libs.baritone)

    // Inspect optional mixin targets without loading these mods into the dev client.
    testRuntimeOnly(libs.distant.horizons)
    testRuntimeOnly(libs.sodium)
    testRuntimeOnly(libs.xaeros.minimap)
    testRuntimeOnly(libs.xaeros.world.map)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val startupClient = sourceSets.create("startupClient") {
    java.srcDir("tools/tests/startup/java")
    resources.srcDir("tools/tests/startup/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

loom {
    mods {
        register("thm-addon") { sourceSet(sourceSets.main.get()) }
        register("thm-startup-test") { sourceSet(startupClient) }
    }
    runs {
        register("startupTest") {
            client()
            sourceSet.set(startupClient.name)
            runDirectory.set(layout.buildDirectory.dir("client-startup"))
            generateRunConfig.set(false)
        }
    }
}

val startupLaunch = layout.buildDirectory.file("client-startup/command.txt")
val prepareStartupTest = tasks.register("prepareStartupTest") {
    val run = tasks.named<JavaExec>("runStartupTest")
    dependsOn(provider { run.get().taskDependencies.getDependencies(run.get()) })
    doLast {
        val launch = run.get()
        val command = listOf(launch.javaLauncher.get().executablePath.asFile.absolutePath) +
            launch.allJvmArgs.filterNot { it.startsWith("@") } +
            listOf("-cp", launch.classpath.asPath, launch.mainClass.get()) + launch.args.orEmpty() +
            launch.argumentProviders.flatMap { it.asArguments() }
        startupLaunch.get().asFile.apply {
            parentFile.mkdirs()
            writeText(command.joinToString("\n"))
        }
    }
}

tasks.test {
    dependsOn(prepareStartupTest)
    useJUnitPlatform()
    systemProperty("thm.startup.command", startupLaunch.get().asFile.absolutePath)
    systemProperty("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer\$OrderAnnotation")
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "16")
    outputs.upToDateWhen { false }
    systemProperty("thm.requireShaderCompiler", providers.environmentVariable("CI").orElse("false").get().equals("true", ignoreCase = true))
}

tasks.register<JavaExec>("checkBuildRules") {
    group = "verification"
    description = "Check build rules against vanilla block states without starting a client."
    dependsOn(tasks.classes)
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = file("tools/tests/BuildSchematicCheck.java").absolutePath
    workingDir = layout.buildDirectory.dir("build-rules-check").get().asFile
    doFirst { workingDir.mkdirs() }
    systemProperty("java.awt.headless", "true")
}

tasks.register<JavaExec>("checkInventoryRules") {
    group = "verification"
    description = "Check inventory components and loadout compatibility without starting a client."
    dependsOn(tasks.classes)
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = file("tools/tests/InventoryUtilitiesCheck.java").absolutePath
    workingDir = layout.buildDirectory.dir("inventory-rules-check").get().asFile
    doFirst { workingDir.mkdirs() }
    systemProperty("java.awt.headless", "true")
}

tasks.register<Test>("checkShaders") {
    group = "verification"
    description = "Compile and link all THM shader programs for OpenGL and Vulkan."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    systemProperty("thm.requireShaderCompiler", true)
    filter { includeTestsMatching("xyz.thm.addon.shaders.ShaderCompatibilityTest") }
}

tasks {
    processResources {
        val branchPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
        val updateBranch = System.getenv("THM_UPDATE_BRANCH")?.trim().orEmpty()
        if (updateBranch.isNotEmpty() && !branchPattern.matches(updateBranch)) {
            error("Updater branch must be a version branch containing only letters, digits, dots, underscores, or hyphens")
        }
        val branch = updateBranch.ifEmpty {
            (System.getenv("GITHUB_REF_NAME")?.takeIf { System.getenv("GITHUB_REF_TYPE") == "branch" }
                ?: ProcessBuilder("git", "branch", "--show-current")
                    .directory(rootDir)
                    .start()
                    .inputStream.bufferedReader().use { it.readLine() })
                ?.trim()?.takeIf(branchPattern::matches).orEmpty()
        }
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get(),
            "fabric_loader_version" to libs.versions.fabric.loader.get(),
            "fabric_api_version" to libs.versions.fabric.api.get(),
            "gh_hash" to (System.getenv("GITHUB_SHA") ?: run {
                val process = ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(rootDir)
                    .start()
                process.inputStream.bufferedReader().readLine()?.trim() ?: ""
            }),
            "gh_branch" to branch,
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }

        manifest {
            attributes("Main-Class" to "xyz.thm.addon.Main")
        }
    }

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "3g"
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
