plugins {
    application
    java
}

group = "app.renami"
version = "0.9.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.groovy:groovy:4.0.22")

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

// macOS .app bundle (mirrors how commercial Java media tools ship a native-looking app)
val jpackageApp by tasks.registering(Exec::class) {
    dependsOn("installDist")
    group = "distribution"
    description = "Build Renami.app via jpackage (app-image, unsigned)."

    val toolchains = project.extensions.getByType<JavaToolchainService>()
    val javaExt = project.extensions.getByType<JavaPluginExtension>()
    val jdkHome = toolchains.launcherFor(javaExt.toolchain).get().metadata.installationPath
    val libDir = layout.buildDirectory.dir("install/renami/lib").get().asFile
    val outDir = layout.buildDirectory.dir("jpackage").get().asFile

    doFirst { outDir.deleteRecursively() }
    // macOS bundles reject versions starting with 0; strip the leading "0." for the bundle only
    val bundleVersion = project.version.toString().removePrefix("0.")
    commandLine(
        "$jdkHome/bin/jpackage",
        "--type", "app-image",
        "--name", "Renami",
        "--app-version", bundleVersion,
        "--input", libDir.absolutePath,
        "--main-jar", "renami.jar",
        "--main-class", "app.renami.Main",
        "--dest", outDir.absolutePath,
        "--java-options", "-Xdock:name=Renami",
        "--mac-package-name", "Renami"
    )
}
