import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// YDeck for Windows: the same projects as the phone (same file format, moved with a backup file), shown as glass
// widgets on the desktop. Each link opens in the Chrome profile of the project's account.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

/** The Windows app's own version (the Android app has its own). */
val desktopVersion = "1.16.2"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.json:json:20240303")
    // Windows APIs: keeping the widgets on the desktop, out of the taskbar and Alt+Tab; the Run key.
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    // The pairing QR code shown to the phone.
    implementation("io.nayuki:qrcodegen:1.8.0")

    testImplementation("junit:junit:4.13.2")
}

// One source of truth with the Android app, copied in at build time: the service catalog (names, brand colours,
// URL patterns, from ServiceCatalog.kt) with each brand mark's vector path (the simple-icons drawables), the
// app's fonts, and its icon.
val shared = layout.buildDirectory.dir("generated/shared-resources")
val copyShared by tasks.registering {
    val app = rootProject.file("app/src/main")
    val catalogFile = app.resolve("java/com/yonidev/ydeck/data/ServiceCatalog.kt")
    val modelFile = app.resolve("java/com/yonidev/ydeck/data/Model.kt")
    val drawables = app.resolve("res/drawable")
    val fonts = app.resolve("res/font")
    val icon = file("icons/ydeck.png")
    val version = desktopVersion
    val out = shared.get().asFile
    inputs.files(catalogFile, modelFile, icon)
    inputs.dir(drawables)
    inputs.dir(fonts)
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        out.deleteRecursively()
        out.resolve("fonts").mkdirs()
        fonts.listFiles()!!.forEach { it.copyTo(out.resolve("fonts/${it.name}")) }
        icon.copyTo(out.resolve("icons/app.png"))

        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val categories = Regex("""(\w+)\("([^"]+)"\)[,\s]""").findAll(modelFile.readText()).associate { it.groupValues[1] to it.groupValues[2] }
        val row = Regex(
            """Service\("([^"]+)", "([^"]+)", "([^"]+)", R\.drawable\.(\w+), (true|false), (?:0x([0-9A-Fa-f]{8})\.toInt\(\)|null), Route\.(\w+), "([^"]*)", listOf\(([^)]*)\), Category\.(\w+)\)""",
        )
        val items = row.findAll(catalogFile.readText()).map { m ->
            val (id, label, short, drawable, brand, color, route) = m.destructured
            val url = m.groupValues[8]
            val match = Regex("\"([^\"]+)\"").findAll(m.groupValues[9]).map { q(it.groupValues[1]) }.joinToString(",")
            val path = if (drawable.startsWith("si_")) {
                Regex("""android:pathData="([^"]+)"""").findAll(drawables.resolve("$drawable.xml").readText()).joinToString(" ") { it.groupValues[1] }
            } else null
            listOf(
                "\"id\":${q(id)}", "\"label\":${q(label)}", "\"short\":${q(short)}", "\"brand\":$brand",
                "\"color\":" + (if (color.isEmpty()) "null" else q("#" + color.substring(2))),
                "\"route\":${q(route)}", "\"url\":${q(url)}", "\"match\":[$match]",
                "\"category\":${q(categories[m.groupValues[10]] ?: "")}", "\"path\":" + (path?.let(::q) ?: "null"),
            ).joinToString(",", "{", "}")
        }.toList()
        check(items.size > 150) { "ServiceCatalog.kt parse failed: ${items.size} services" }
        out.resolve("catalog.json").writeText("[\n" + items.joinToString(",\n") + "\n]\n")
        out.resolve("build.properties").writeText("version=$version\n")
    }
}
sourceSets.main { resources.srcDir(files(shared).builtBy(copyShared)) }
// The phone link (pairing, sealing, the relay), the same source as the Android app's.
sourceSets.main { kotlin.srcDir(rootProject.file("shared/kotlin")) }

// A full JDK with jpackage for the installer (Android Studio's runtime doesn't include it).
val packagingJdk = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }

compose.desktop {
    application {
        mainClass = "com.yonidev.ydeck.desktop.MainKt"
        javaHome = packagingJdk.get().metadata.installationPath.asFile.absolutePath
        // Trust exactly the certificates Windows trusts (also company / content-filter certificates), for favicons.
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx256m", "-Djavax.net.ssl.trustStoreType=Windows-ROOT", "-Dsun.java2d.uiScale.enabled=true")

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "YDeck"
            packageVersion = desktopVersion
            description = "YDeck - nothing left open"
            vendor = "YoniDev"
            copyright = "© YoniDev"
            modules("java.desktop", "java.logging", "java.naming", "java.net.http", "java.prefs", "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.mscapi", "jdk.httpserver")
            windows {
                iconFile.set(project.file("icons/ydeck.ico"))
                menuGroup = "YDeck"
                shortcut = true
                perUserInstall = true
                dirChooser = false
                // Never change: lets a new installer replace the old version.
                upgradeUuid = "8c3d2f6e-4b71-4f0a-9a52-7d1e6b3c9f24"
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

// Every installer lands in dist/ under its own version, next to the earlier ones (like the APKs).
// Built with jpackage from the release app image, with YDeck's own installer screens (desktop/installer: the images
// from store/brand/installer/make.cjs, the words, and "Open YDeck now", which opens it with its sting). Same name,
// upgrade code and per-user install as Compose's packageReleaseMsi, so it replaces earlier versions.
tasks.register("distribute") {
    dependsOn("createReleaseDistributable", ":unzipWix")
    doLast {
        val out = rootProject.file("dist/YDeck-Windows-$desktopVersion.msi")
        if (out.exists()) throw GradleException("dist/${out.name} already exists - bump desktopVersion first")
        val appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/YDeck").get().asFile
        val wix = rootProject.layout.buildDirectory.dir("wix311").get().asFile
        val work = layout.buildDirectory.dir("styled-msi").get().asFile.apply { deleteRecursively(); mkdirs() }
        val res = File(work, "resources").apply { mkdirs() }
        val src = project.file("installer")
        fun wixPath(name: String): String = File(src, name).absolutePath.replace('\\', '/')
        File(res, "ui.wxf").writeText(File(src, "ui.wxf").readText().replace("@DIALOG@", wixPath("dialog.bmp")).replace("@BANNER@", wixPath("banner.bmp")))
        File(src, "MsiInstallerStrings_en.wxl").copyTo(File(res, "MsiInstallerStrings_en.wxl"))
        val jpackage = File(packagingJdk.get().metadata.installationPath.asFile, "bin/jpackage.exe")
        val pb = ProcessBuilder(
            jpackage.absolutePath, "--type", "msi", "--app-image", appImage.absolutePath,
            "--name", "YDeck", "--app-version", desktopVersion, "--vendor", "YoniDev", "--copyright", "© YoniDev",
            "--description", "YDeck - nothing left open", "--icon", project.file("icons/ydeck.ico").absolutePath,
            "--win-menu", "--win-menu-group", "YDeck", "--win-shortcut", "--win-per-user-install",
            "--win-upgrade-uuid", "8c3d2f6e-4b71-4f0a-9a52-7d1e6b3c9f24",
            // Only so jpackage links WiX's UI library; ui.wxf replaces its screens (there is no folder picker).
            "--win-dir-chooser",
            "--resource-dir", res.absolutePath, "--temp", File(work, "tmp").absolutePath, "--dest", File(work, "out").absolutePath,
        ).inheritIO()
        pb.environment()["PATH"] = wix.absolutePath + File.pathSeparator + System.getenv("PATH")
        if (pb.start().waitFor() != 0) throw GradleException("jpackage failed")
        val msi = File(work, "out").listFiles { f -> f.extension == "msi" }?.firstOrNull() ?: throw GradleException("no MSI was built")
        out.parentFile.mkdirs()
        msi.copyTo(out)
        println("-> dist/${out.name}")
    }
}

// `-Prender`: RenderWidgets draws every widget size into build/widget-renders/ (a visual check, no windows).
// The live relay test uses the JDK's own CA list: the build JDK (Android Studio's) has no Windows-ROOT store.
tasks.test { if (!project.hasProperty("relay") && !project.hasProperty("phone")) jvmArgs("-Djavax.net.ssl.trustStoreType=Windows-ROOT"); if (project.hasProperty("render")) systemProperty("render", "1"); if (project.hasProperty("relay")) systemProperty("relay", "1"); if (project.hasProperty("phone")) systemProperty("phone", project.property("phone").toString()); outputs.upToDateWhen { !project.hasProperty("render") } }
