import com.android.build.api.artifact.SingleArtifact
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jlleitschuh.gradle.ktlint")
}

val hindiFontAssets = layout.buildDirectory.dir("generated/assets/hindiFonts")
val googleFontsCommit = "86aced91de05cd4f96a06838124cca45be337b2b"
val bundledHindiFonts = listOf(
    Triple("notosansdevanagari", "NotoSansDevanagari[wdth,wght].ttf", "NotoSansDevanagari-VF.ttf|641944|163f04bddc5a96fc523e4080466cac5c588f6594"),
    Triple("notoserifdevanagari", "NotoSerifDevanagari[wdth,wght].ttf", "NotoSerifDevanagari-VF.ttf|757692|3d90da279fe70b5017d9b51f58772f0c9ea4146e"),
    Triple("mukta", "Mukta-Regular.ttf", "Mukta-Regular.ttf|432248|ccb22cfb2cbc43cd516d6376492ad6dde3a94c2f"),
    Triple("mukta", "Mukta-Bold.ttf", "Mukta-Bold.ttf|418416|d7256f58bd2f8344c494e0df3be08dcd00d348c8"),
    Triple("hind", "Hind-Regular.ttf", "Hind-Regular.ttf|299532|d354dbbcd295b1b7e09b015f0bd490864ad47aca"),
    Triple("hind", "Hind-Bold.ttf", "Hind-Bold.ttf|286604|4469b8415f1dcbced9dfc3f2bb24e2f2f2618c5d"),
    Triple("tirodevanagarihindi", "TiroDevanagariHindi-Regular.ttf", "TiroDevanagariHindi-Regular.ttf|423224|9a55b8c823496ca170aad4c4fced67891541fff0"),
    Triple("tirodevanagarihindi", "TiroDevanagariHindi-Italic.ttf", "TiroDevanagariHindi-Italic.ttf|425636|b0a5bd9916bf6d912ce75bee30c24d9db42e5c41"),
    Triple("kalam", "Kalam-Regular.ttf", "Kalam-Regular.ttf|427360|0c956a29348e69bff24a1ae8952fd0b42569311c"),
    Triple("sahitya", "Sahitya-Regular.ttf", "Sahitya-Regular.ttf|261652|be1a414eff64e0ba15e8eb5852f35a66da2923c9"),
    Triple("sahitya", "Sahitya-Bold.ttf", "Sahitya-Bold.ttf|267140|c9f249fcc9f4664f439c159ac1ac720a1624c416"),
    Triple("martel", "Martel-Regular.ttf", "Martel-Regular.ttf|170196|7bd3ef19172c35f443476ce5657e1247c614ca66"),
    Triple("baloo2", "Baloo2[wght].ttf", "Baloo2-VF.ttf|683200|bc1b9f1191c0d6d23cb2ce0af66aba82ddbf8d6c"),
    Triple("khand", "Khand-Regular.ttf", "Khand-Regular.ttf|316808|0d8e9536f19b4a9842d95b699c33b7ac40f433cb"),
    Triple("gotu", "Gotu-Regular.ttf", "Gotu-Regular.ttf|697792|8182906d0266999a9df466a5370a2a9a06935fea"),
    Triple("glegoo", "Glegoo-Regular.ttf", "Glegoo-Regular.ttf|271368|388d1fe3c0985cabbf04eb5977fc6e6d0c553345")
)
val bundledHindiFontLicenses = listOf(
    Triple("notosansdevanagari", "Noto-Sans-Devanagari-OFL.txt", "4386|cd2cc5c94b4151933eba6ee508e3975274d6fa07"),
    Triple("notoserifdevanagari", "Noto-Serif-Devanagari-OFL.txt", "4386|cd2cc5c94b4151933eba6ee508e3975274d6fa07"),
    Triple("mukta", "Mukta-OFL.txt", "4367|12057d5a1c20c7476c62a7509756036dc6fb6ed8"),
    Triple("hind", "Hind-OFL.txt", "4373|6dd809659a8ccace7ccee108d024d33cd4f936da"),
    Triple("tirodevanagarihindi", "Tiro-Devanagari-Hindi-OFL.txt", "4387|9acb0fd8b8e54a98007113da015d33419180ca05"),
    Triple("kalam", "Kalam-OFL.txt", "4373|6dd809659a8ccace7ccee108d024d33cd4f936da"),
    Triple("sahitya", "Sahitya-OFL.txt", "4393|6dc46a4ed56ad2d3fc10295f7253c646ec468d98"),
    Triple("martel", "Martel-OFL.txt", "4436|8b5d372e76d3aec70a2c8debef29f4b9c8c98c30"),
    Triple("baloo2", "Baloo-2-OFL.txt", "4384|72adb492d4f0ede2a9dbdefed607f33ad3a6fc68"),
    Triple("khand", "Khand-OFL.txt", "4373|6dd809659a8ccace7ccee108d024d33cd4f936da"),
    Triple("gotu", "Gotu-OFL.txt", "4349|2d2aa47cca45768c93b3a9b65752f7b7b9163587"),
    Triple("glegoo", "Glegoo-OFL.txt", "4446|838d39e3aabb5e99b6163cd3ce4d1cb585444ef6")
)
val downloadHindiFonts = tasks.register("downloadHindiFonts") {
    outputs.dir(hindiFontAssets)
    doLast {
        val root = hindiFontAssets.get().asFile
        fun download(path: String, target: java.io.File, size: Long, expectedSha: String) {
            fun blobSha(bytes: ByteArray): String {
                val digest = MessageDigest.getInstance("SHA-1")
                digest.update(("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8))
                return digest.digest(bytes).joinToString("") { "%02x".format(it) }
            }
            if (target.isFile && target.length() == size &&
                runCatching { blobSha(target.readBytes()) == expectedSha }.getOrDefault(false)
            ) return
            val encoded = path.replace("[", "%5B").replace("]", "%5D")
            val connection = URI(
                "https://raw.githubusercontent.com/google/fonts/" + googleFontsCommit + "/" + encoded
            ).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 45000
            try {
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw GradleException("Could not download Hindi font asset: " + path)
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.size.toLong() != size || blobSha(bytes) != expectedSha) {
                    throw GradleException("Hindi font checksum failed: " + path)
                }
                target.parentFile.mkdirs()
                target.writeBytes(bytes)
            } finally {
                connection.disconnect()
            }
        }
        bundledHindiFonts.forEach { (family, file, details) ->
            val (output, size, sha) = details.split("|")
            download("ofl/" + family + "/" + file, root.resolve("viewer/fonts/" + output), size.toLong(), sha)
        }
        bundledHindiFontLicenses.forEach { (family, output, details) ->
            val (size, sha) = details.split("|")
            download("ofl/" + family + "/OFL.txt", root.resolve("viewer/fonts/licenses/" + output), size.toLong(), sha)
        }
    }
}


extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    sourceSets.getByName("main").assets.directories.add(hindiFontAssets)
    namespace = "com.arjun.gander"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arjun.gander"
        minSdk = 26
        targetSdk = 36
        versionCode = 37
        versionName = "2.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // A device test that hangs blocks the whole nightly run behind it, and
        // an emulator gives no sign of the difference between slow and stuck.
        // Three minutes is far longer than the slowest of these needs.
        testInstrumentationRunnerArguments["timeout_msec"] = "180000"
    }

    // The release keystore is intentionally not in the repo. Contributors without
    // it get an unsigned release build; debug builds always work.
    //
    // The password is read from GANDER_STORE_PASSWORD / GANDER_KEY_PASSWORD, which
    // belong in ~/.gradle/gradle.properties rather than here, because this file is
    // public and a password committed to it is a password published. The fallback
    // is the throwaway one the README tells contributors to generate their own key
    // with, so cloning and building keeps working with no configuration at all.
    val releaseKeystore = rootProject.file("keystore/gander.jks")
    if (releaseKeystore.exists()) {
        val storePass = (findProperty("GANDER_STORE_PASSWORD") as String?) ?: "gander-local"
        val keyPass = (findProperty("GANDER_KEY_PASSWORD") as String?) ?: storePass
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = storePass
                keyAlias = "gander"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            // Most of the dex is library code nothing here calls, which R8 drops, as
            // shrinkResources drops unused resources. Obfuscation buys no secrecy in an
            // open repo; the shrinking is the point. proguard-rules.pro says why line
            // numbers survive it.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // So a debug build installs alongside an existing release install rather
            // than being refused for having a different signing key, which would
            // otherwise mean uninstalling and losing recents and folder grants.
            applicationIdSuffix = ".debug"
        }
    }

    // No per-ABI split: with PDF rendering moved off Pdfium the app ships no native
    // code at all, so one APK serves every architecture.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }



    buildFeatures {
        // IPdfRenderer.aidl is how Gander talks to the isolated PDF renderer, and AGP 8
        // builds no AIDL unless asked.
        aidl = true
    }

    // AGP signs an encrypted list of the dependencies into the APK, which only Google can read and
    // F-Droid flags. The bundle keeps its copy, which Play Console reads to warn about an SDK.
    dependenciesInfo {
        includeInApk = false
    }

    lint {
        // Warnings are frozen into the baseline below, so that a new one fails the
        // build while the existing ones stay visible in the report rather than
        // blocking every change until they are all fixed. Some are accessibility
        // warnings, which is the category Play's pre-launch report reads, and
        // frozen is not fixed: they are worth coming back to.
        //
        // The three "newer version available" checks are off because they cannot
        // be baselined. Lint matches a baseline entry by its exact message, and
        // theirs name whatever upstream published last, so a frozen one stops
        // matching the day a dependency releases and turns the build red with no
        // change here at all.
        //
        // OldTargetApi is off for a like reason: it fires once lint knows of an
        // Android newer than targetSdk, which AGP 8.13 and the API 37 SDK brought
        // with no change here. The target moves when Play requires it.
        //
        // Regenerate after fixing some: delete app/lint-baseline.xml, run
        // ./gradlew lintDebug (it fails once, on purpose, having written a new
        // baseline), and read the diff before committing it.
        disable += setOf(
            "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi"
        )
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        // A lint failure should stop CI, not be buried in a report nobody opens
        abortOnError = true
        checkDependencies = false
    }

    testOptions {
        unitTests {
            // Robolectric reads the merged manifest, the resource table and the
            // assets directory. Without this it gets none of them, and every test
            // that inflates a layout or reads viewer/ fails on a missing resource.
            isIncludeAndroidResources = true
            // Some tests read files straight off the disk, where Gradle cannot see
            // them: ReleaseHygieneTest the changelog, the ProGuard rules, the store
            // listing and this file, VendoredLibsTest docs/VENDORED.md and the fetch
            // script, RangeParityTest the range cases. Declared as inputs, an edit to
            // any of them on its own reruns the tests instead of leaving them up to
            // date and unrun. The assets they read are inputs already.
            all { test ->
                test.inputs.files(
                    rootProject.file("CHANGELOG.md"),
                    file("proguard-rules.pro"),
                    rootProject.fileTree("fastlane/metadata/android") { include("**/*.txt") },
                    file("build.gradle.kts"),
                    rootProject.file("docs/VENDORED.md"),
                    rootProject.file("scripts/fetch-viewer-libs.sh"),
                    rootProject.file("tests/fixtures/range-cases.json"),
                ).withPropertyName("filesReadOffDisk")
                // Every test class shares one JVM, and Gradle gives it 512 MB unless told
                // otherwise. ZipReaderTest builds a 200,001-entry zip, and run near the end
                // of the suite it found that heap nearly full: out of memory in two of three
                // full runs on 2026-10-01, where on its own it takes half a second.
                test.maxHeapSize = "1g"
                // Robolectric 4.17 sets up Android 16's shared memory before each test through
                // a JDK-internal class that Java 21 doesn't export, so without this every test
                // on API 36 fails at setup. Drop it once robolectric/robolectric#11354 ships.
                test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }

    // The fixture documents live outside the module because three different test
    // tiers read the same files: unit tests as resources, instrumented tests as
    // assets in the test APK, and the Python viewer tests straight off disk.
    // One directory, generated by tests/fixtures/make_fixtures.py.
    sourceSets {
        getByName("test") { resources.directories.add("../tests/fixtures/files") }
        // The instrumented tests read the same files, but out of the test
        // APK's assets, because a device has no access to the repo.
        getByName("androidTest") { assets.directories.add("../tests/fixtures/files") }
    }
}

// AGP 9 provides Kotlin compilation directly; keep compiler warnings strict.
kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

tasks.named("preBuild").configure { dependsOn(downloadHindiFonts) }

// Requesting nothing is the whole promise, but permissions arrive transitively: Media3 contributes
// ACCESS_NETWORK_STATE, stripped in the manifest. Naming one permission there does not stop the next
// dependency bump adding another, and that would surface in the store listing rather than the build.
// So assert the invariant on the merged manifest instead of trusting the strip.
//
// Held as suffixes on the variant's own applicationId, since a debug build carries
// one and would otherwise fail against a hardcoded package name.
val permissionAllowlistSuffixes = setOf(
    // androidx.core declares this so libraries can registerReceiver(..., RECEIVER_NOT_EXPORTED).
    // Signature level and self-granted, so it is never shown to the user as a permission.
    ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
)

// The components other apps may start or send to, for the same reason: a dependency can export
// one without a word, as androidx.profileinstaller did.
val exportedAllowlist = setOf(
    // The launcher's icon
    "com.arjun.gander.MainActivity",
    // Open with and Share from other apps, which take only a content URI: see INTERNAL_VIEWER
    "com.arjun.gander.ViewerActivity",
    // Only a sender holding android.permission.DUMP, the shell's, can reach it
    "androidx.profileinstaller.ProfileInstallReceiver",
    // Debug builds only, from src/debug: the PDF renderer's spike, which adb hands a document to
    "com.arjun.gander.PdfSpikeActivity",
)

// The Kotlin style, as .editorconfig at the root sets it: ./gradlew ktlintCheck, or ktlintFormat
ktlint {
    version.set("1.8.0")
}

androidComponents.onVariants { variant ->
    val suffix = variant.name.replaceFirstChar { it.uppercase() }
    val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
    val appId = variant.applicationId

    val checkPermissions = tasks.register("check${suffix}Permissions") {
        description = "Fails if the merged manifest requests a permission or exports a component we did not sign off on."
        val manifestFile = mergedManifest
        val allowedSuffixes = permissionAllowlistSuffixes
        val allowedExports = exportedAllowlist
        val applicationId = appId
        val stamp = layout.buildDirectory.file("reports/permissions/$suffix.txt")
        inputs.file(manifestFile)
        outputs.file(stamp)
        doLast {
            val android = "http://schemas.android.com/apk/res/android"
            val manifest = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifestFile.get().asFile)
            fun elements(vararg tags: String) = tags.flatMap { tag ->
                val found = manifest.getElementsByTagName(tag)
                (0 until found.length).map { found.item(it) as org.w3c.dom.Element }
            }
            val requested = elements("uses-permission", "uses-permission-sdk-23")
                .map { it.getAttributeNS(android, "name") }
            val allowed = allowedSuffixes.map { applicationId.get() + it }.toSet()
            val unexpected = requested.filterNot { it in allowed }
            if (unexpected.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("Gander ships with no permissions, but $suffix requests:")
                        unexpected.forEach { appendLine("    $it") }
                        appendLine()
                        appendLine("A dependency added these. Either strip each one with")
                        appendLine("tools:node=\"remove\" in AndroidManifest.xml, or add it to")
                        append("permissionAllowlistSuffixes in app/build.gradle.kts with a reason.")
                    }
                )
            }
            val exported = elements("activity", "activity-alias", "service", "receiver", "provider")
                .filter { it.getAttributeNS(android, "exported") == "true" }
                .map { it.getAttributeNS(android, "name") }
            val unexpectedExports = exported.filterNot { it in allowedExports }
            if (unexpectedExports.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("$suffix exports components other apps can reach that nobody signed off on:")
                        unexpectedExports.forEach { appendLine("    $it") }
                        appendLine()
                        appendLine("A dependency added these. Either set android:exported=\"false\" on each")
                        appendLine("with tools:replace=\"android:exported\" in AndroidManifest.xml, or add it")
                        append("to exportedAllowlist in app/build.gradle.kts with a reason.")
                    }
                )
            }
            stamp.get().asFile.apply {
                parentFile.mkdirs()
                writeText((requested + exported).joinToString("\n"))
            }
        }
    }

    // Variant tasks are not registered yet while onVariants runs, so match lazily.
    //
    // Every output: assemble builds the APK that goes to GitHub releases, bundle the AAB that goes
    // to Play, which doesn't depend on assemble, and package the APK that install puts on a phone,
    // which doesn't either.
    tasks.matching { it.name == "assemble$suffix" || it.name == "bundle$suffix" || it.name == "package$suffix" }
        .configureEach { dependsOn(checkPermissions) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    // Swiping between a folder's photos. Material already brings in 1.1.0-beta02.
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.webkit:webkit:1.18.0")
    // Zoomable image view that tiles huge bitmaps
    implementation("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")
    // EXIF orientation for photos opened via SAF content URIs
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Video and audio playback
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
    // 4.16.1 is the first to support SDK 36, which is why the toolchain has to be JDK 21.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit-ktx:1.3.0")

    androidTestImplementation("com.google.truth:truth:1.4.5")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    // The WebView matchers: the only way to assert on what a viewer page drew
    androidTestImplementation("androidx.test.espresso:espresso-web:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    // Runs Google's accessibility checks on every Espresso action, which is
    // the same scan Play's pre-launch report applies to the native views
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.7.0")
}
