plugins {
    id("generate-build-config")

    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.9.0"
    id("com.android.library")
    id("org.jetbrains.compose")
    id("app.cash.sqldelight") version "2.0.0"
}

val buildConfigDir: Provider<Directory> get() = project.layout.buildDirectory.dir("generated/buildconfig")

kotlin {
    androidTarget()

    jvm("desktop")

    sourceSets {
        commonMain {
            kotlin {
                srcDir(rootProject.file("spmp-server/src/commonMain/kotlin/spms/socketapi/shared/"))
            }

            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.material)
                implementation(compose.material3)
                implementation(compose.components.resources)

                implementation(project(":ComposeKit:lib"))

                implementation("com.squareup.okhttp3:okhttp:4.11.0")
                implementation("com.google.code.gson:gson:2.10.1")
                implementation("org.apache.commons:commons-text:1.10.0")
                implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
                implementation("org.jsoup:jsoup:1.16.1")
                implementation("org.burnoutcrew.composereorderable:reorderable:0.9.2")
                implementation("com.github.SvenWoltmann:color-thief-java:v1.1.2")
                implementation("com.github.catppuccin:java:v1.0.0")
                implementation("com.github.paramsen:noise:2.0.0")
                implementation("org.kobjects.ktxml:core:0.2.3")
                implementation("org.bitbucket.ijabz:jaudiotagger:v3.0.1")
                implementation("com.github.teamnewpipe:NewPipeExtractor:v0.22.7")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.zeromq:jeromq:0.5.3")
            }

            kotlin.srcDir(buildConfigDir)
        }

        val androidMain by getting {
            dependencies {
                api("androidx.activity:activity-compose:1.8.1")
                api("androidx.core:core-ktx:1.12.0")
                api("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.palette:palette:1.0.0")
                implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

                val media3_version = "1.2.0"
                implementation("androidx.media3:media3-exoplayer:$media3_version")
                implementation("androidx.media3:media3-ui:$media3_version")
                implementation("androidx.media3:media3-session:$media3_version")

                implementation("com.google.accompanist:accompanist-pager:0.21.2-beta")
                implementation("com.google.accompanist:accompanist-pager-indicators:0.21.2-beta")
                implementation("com.google.accompanist:accompanist-systemuicontroller:0.21.2-beta")
                //noinspection GradleDependency
                implementation("com.github.andob:android-awt:1.0.0")
                implementation("com.github.toasterofbread:KizzyRPC:84e79614b4")
                implementation("app.cash.sqldelight:android-driver:2.0.0")
                implementation("com.anggrayudi:storage:1.5.5")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.0")
                implementation("io.github.jan-tennert.supabase:functions-kt:1.3.2")
                implementation("io.ktor:ktor-client-cio:2.3.6")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("com.github.ltttttttttttt:load-the-image:1.0.5")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.0")
                implementation("com.github.caoimhebyrne:KDiscordIPC:0.2.2")
            }
        }
    }
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.toasterofbread.spmp.shared"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main") {
        res.srcDirs("src/androidMain/res")
        resources.srcDirs("src/commonMain/resources")
    }
}

val DATABASE_VERSION: Int = 4

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.toasterofbread.${project.parent!!.name}.db")

            // Version specification kept for backwards-compatibility
            version = DATABASE_VERSION
        }
    }
}

val fixDatabaseVersion = tasks.register("fixDatabaseVersion") {
    doLast {
        val file: File = project.file("build/generated/sqldelight/code/Database/commonMain/com/toasterofbread/${project.parent!!.name}/db/shared/DatabaseImpl.kt")
        val lines: MutableList<String> = file.readLines().toMutableList()
        var found: Boolean = false

        for (i in 0 until lines.size) {
            if (lines[i].endsWith("override val version: Long")) {
                lines[i + 1] = "      get() = $DATABASE_VERSION"
                found = true
                break
            }
        }

        check(found) { "Version line not found in $file" }

        file.writer().use { writer ->
            for (line in lines) {
                writer.write(line + "\n")
            }
        }
    }
}

tasks.all {
    if (name == "generateCommonMainDatabaseInterface") {
        finalizedBy(fixDatabaseVersion)
    }
}
