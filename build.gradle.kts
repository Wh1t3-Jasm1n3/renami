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

fun runCmd(vararg args: String) {
    val rc = ProcessBuilder(args.toList()).inheritIO().start().waitFor()
    if (rc != 0) throw GradleException("Command failed (exit $rc): ${args.joinToString(" ")}")
}

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
val isMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX

// FlatLaf ships unsigned native libs inside its jar and Apple's notary service
// scans inside jars — extract the macOS dylibs, Developer ID-sign them
// (hardened runtime + secure timestamp) and put them back before jpackage.
val signFlatlafNatives = tasks.register("signFlatlafNatives") {
    group = "distribution"
    description = "Developer ID-sign FlatLaf's bundled macOS natives inside flatlaf-3.7.2.jar (notarization prerequisite)."
    dependsOn("installDist")
    onlyIf { isMac && !skipSign }
    val flatlafJar = layout.buildDirectory.file("install/renami/lib/flatlaf-3.7.2.jar").get().asFile
    doLast {
        val work = layout.buildDirectory.dir("flatlaf-natives").get().asFile
        work.deleteRecursively()
        work.mkdirs()
        runCmd("unzip", "-qo", flatlafJar.absolutePath, "com/formdev/flatlaf/natives/*", "-d", work.absolutePath)
        val natives = work.resolve("com/formdev/flatlaf/natives")
        natives.listFiles { f -> f.name.endsWith(".dylib") }!!.forEach { dylib ->
            runCmd("codesign", "--force", "--options", "runtime", "--timestamp", "--sign", signingKey, dylib.absolutePath)
            runCmd("codesign", "--verify", "--strict", "--verbose", dylib.absolutePath)
        }
        // jar's -C applies to only the next entry, so repeat it per file
        runCmd(jdkBin("jar"), "uf", flatlafJar.absolutePath,
            "-C", work.absolutePath, "com/formdev/flatlaf/natives/libflatlaf-macos-arm64.dylib",
            "-C", work.absolutePath, "com/formdev/flatlaf/natives/libflatlaf-macos-x86_64.dylib")
        work.deleteRecursively()
    }
}

val jpackageApp = tasks.register<Exec>("jpackageApp") {
    dependsOn(signFlatlafNatives, appIcon)
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
// Notarization: Developer ID signing alone fails Gatekeeper (and Homebrew
// casks) — submit the signed .app to Apple's notary service and staple the
// ticket. Credentials come from `xcrun notarytool store-credentials`
// (override profile with -PnotaryProfile="...", skip with -PskipNotarize).
// ---------------------------------------------------------------------------
val notaryProfile = (project.findProperty("notaryProfile") as String?) ?: "henry-dev"
val skipNotarize = project.hasProperty("skipNotarize")

val notarizeApp = tasks.register("notarizeApp") {
    group = "distribution"
    description = "Submit Renami.app to Apple's notary service and staple the ticket."
    dependsOn(jpackageApp)
    onlyIf { isMac && !skipNotarize }

    val appImage = layout.buildDirectory.dir("jpackage/Renami.app").get().asFile
    val notaryZip = layout.buildDirectory.file("jpackage/Renami-notary.zip").get().asFile

    doLast {
        runCmd("ditto", "-c", "-k", "--keepParent", appImage.absolutePath, notaryZip.absolutePath)
        runCmd("xcrun", "notarytool", "submit", notaryZip.absolutePath, "--keychain-profile", notaryProfile, "--wait")
        runCmd("xcrun", "stapler", "staple", appImage.absolutePath)
        runCmd("xcrun", "stapler", "validate", appImage.absolutePath)
        runCmd("spctl", "--assess", "--type", "execute", "--verbose", appImage.absolutePath)
        notaryZip.delete()
    }
}

// ---------------------------------------------------------------------------
// Release artifacts: .app zip, dmg installer, CLI zip
// ---------------------------------------------------------------------------
val version = project.version.toString()

val appZip = tasks.register<Exec>("appZip") {
    group = "distribution"
    description = "Renami-$version-macos.zip — the signed + notarized .app bundle (symlinks + exec bits kept)."
    dependsOn(notarizeApp)
    val outDir = layout.buildDirectory.dir("artifacts").get().asFile
    val src = layout.buildDirectory.dir("jpackage").get().asFile
    doFirst { outDir.mkdirs() }
    workingDir = src
    commandLine("zip", "-ryq", File(outDir, "Renami-$version-macos.zip").absolutePath, "Renami.app")
}

val dmg = tasks.register<Exec>("dmg") {
    group = "distribution"
    description = "Renami-$version.dmg — drag-to-Applications installer image (from the notarized app)."
    dependsOn(notarizeApp)
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

val notarizeDmg = tasks.register("notarizeDmg") {
    group = "distribution"
    description = "Submit Renami-$version.dmg to the notary service and staple it too."
    dependsOn(dmg)
    onlyIf { isMac && !skipNotarize }
    val dmgFile = layout.buildDirectory.dir("artifacts/Renami-$version.dmg").get().asFile
    doLast {
        runCmd("xcrun", "notarytool", "submit", dmgFile.absolutePath, "--keychain-profile", notaryProfile, "--wait")
        runCmd("xcrun", "stapler", "staple", dmgFile.absolutePath)
        runCmd("xcrun", "stapler", "validate", dmgFile.absolutePath)
    }
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
    description = "Build every GitHub-release asset: app zip, dmg, CLI zip (macOS assets notarized)."
    dependsOn(appZip, notarizeDmg, cliZip)
}
