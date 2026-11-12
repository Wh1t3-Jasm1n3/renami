plugins {
    application
    java
}

group = "app.renami"

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.groovy:groovy:4.0.22")
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.formdev:flatlaf-extras:3.7.2")
    implementation("com.googlecode.lanterna:lanterna:3.1.2")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "app.renami.Main"
    applicationName = "renami"
}

tasks.test {
    useJUnitPlatform()
    // pass through opt-in flags for integration-style tests (e.g. FPCALC_TEST=on)
    System.getenv().forEach { (k, v) ->
        if (k.startsWith("FPCALC_") || k.startsWith("RENAMI_")) {
            environment(k, v)
        }
    }
}

tasks.jar {
    // stable jar name for jpackage + tests
    archiveVersion.set("")
    manifest {
        attributes("Implementation-Title" to "Renami", "Implementation-Version" to project.version)
    }
}

// ---------------------------------------------------------------------------
// Application icon: generated from code (tools/GenerateIcon.java), never a
// checked-in binary. packaging/AppIcon.* are build artifacts (see .gitignore).
// ---------------------------------------------------------------------------
val toolchains = project.extensions.getByType<JavaToolchainService>()
val javaExt = project.extensions.getByType<JavaPluginExtension>()
val jdkLauncher = toolchains.launcherFor(javaExt.toolchain)
fun jdkBin(tool: String) = File(jdkLauncher.get().metadata.installationPath.asFile, "bin/$tool").absolutePath

val iconsetDir = file("$rootDir/packaging/AppIcon.iconset")
val icnsFile = file("$rootDir/packaging/AppIcon.icns")

val generateIconPngs = tasks.register<Exec>("generateIconPngs") {
    group = "distribution"
    description = "Render the AppIcon.iconset PNGs with Java2D (no external assets)."
    inputs.file(file("$rootDir/tools/GenerateIcon.java"))
    outputs.dir(iconsetDir)
    workingDir = rootDir
    commandLine(jdkBin("java"), "-Djava.awt.headless=true",
        file("$rootDir/tools/GenerateIcon.java").absolutePath)
}

val appIcon = tasks.register<Exec>("appIcon") {
    group = "distribution"
    description = "Assemble packaging/AppIcon.icns from the generated iconset (macOS only)."
    dependsOn(generateIconPngs)
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    inputs.dir(iconsetDir)
    outputs.file(icnsFile)
    commandLine("iconutil", "-c", "icns", iconsetDir.absolutePath, "-o", icnsFile.absolutePath)
}

// the same PNG is shipped inside the jar so the Swing UI / Dock icon works in dev builds
val copyIconResource = tasks.register<Copy>("copyIconResource") {
    dependsOn(generateIconPngs)
    from(iconsetDir) {
        include("icon_256x256.png")
        rename { "app-icon.png" }
    }
    into(layout.buildDirectory.dir("generated/resources/app/renami"))
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/resources"))
}

tasks.processResources {
    dependsOn(copyIconResource)
}

// macOS .app bundle (mirrors how commercial Java media tools ship a native-looking app)
// Sign with a Developer ID when one is in the keychain (override with -PsignKey="..." or -PskipSign).
val signingKey = (project.findProperty("signKey") as String?)
    ?: "Developer ID Application: Henry Ling (H24BG2G8YU)"
val skipSign = project.hasProperty("skipSign")

val jpackageApp = tasks.register<Exec>("jpackageApp") {
    dependsOn("installDist", appIcon)
    group = "distribution"
    description = "Build Renami.app via jpackage (app-image, Developer ID signed when available)."

    val libDir = layout.buildDirectory.dir("install/renami/lib").get().asFile
    val outDir = layout.buildDirectory.dir("jpackage").get().asFile

    doFirst { outDir.deleteRecursively() }
    // macOS bundles reject versions starting with 0; strip the leading "0." for the bundle only
    val bundleVersion = project.version.toString().removePrefix("0.")
    val args = mutableListOf(
        jdkBin("jpackage"),
        "--type", "app-image",
        "--name", "Renami",
        "--app-version", bundleVersion,
        "--input", libDir.absolutePath,
        "--main-jar", "renami.jar",
        "--main-class", "app.renami.Main",
        "--dest", outDir.absolutePath,
        "--java-options", "-Xdock:name=Renami",
        "--mac-package-name", "Renami",
        "--mac-package-identifier", "app.renami.Renami",
        "--icon", icnsFile.absolutePath,
        "--file-associations", file("$rootDir/packaging/file-associations.properties").absolutePath
    )
    if (!skipSign) {
        args += listOf("--mac-sign", "--mac-signing-key-user-name", signingKey)
    }
    commandLine(args)
}

// ---------------------------------------------------------------------------
// Release artifacts: .app zip, dmg installer, CLI zip
// ---------------------------------------------------------------------------
val version = project.version.toString()

val appZip = tasks.register<Exec>("appZip") {
    group = "distribution"
    description = "Renami-$version-macos.zip — the signed .app bundle (symlinks + exec bits kept)."
    dependsOn(jpackageApp)
    val outDir = layout.buildDirectory.dir("artifacts").get().asFile
    val src = layout.buildDirectory.dir("jpackage").get().asFile
    doFirst { outDir.mkdirs() }
    workingDir = src
    commandLine("zip", "-ryq", File(outDir, "Renami-$version-macos.zip").absolutePath, "Renami.app")
}

val dmg = tasks.register<Exec>("dmg") {
    group = "distribution"
    description = "Renami-$version.dmg — drag-to-Applications installer image."
    dependsOn(jpackageApp)
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    val outDir = layout.buildDirectory.dir("artifacts").get().asFile
    val appImage = layout.buildDirectory.dir("jpackage/Renami.app").get().asFile
    commandLine(
        jdkBin("jpackage"),
        "--type", "dmg",
        "--name", "Renami",
        "--app-version", project.version.toString().removePrefix("0."),
        "--app-image", appImage.absolutePath,
        "--dest", outDir.absolutePath
    )
}

val cliZip = tasks.register<Zip>("cliZip") {
    group = "distribution"
    description = "renami-$version-cli.zip — installDist output (needs JDK 21+)."
    dependsOn("installDist")
    archiveFileName.set("renami-$version-cli.zip")
    destinationDirectory.set(layout.buildDirectory.dir("artifacts"))
    from(layout.buildDirectory.dir("install/renami"))
}

val releaseArtifacts = tasks.register("releaseArtifacts") {
    group = "distribution"
    description = "Build every GitHub-release asset: app zip, dmg, CLI zip."
    dependsOn(appZip, dmg, cliZip)
}
