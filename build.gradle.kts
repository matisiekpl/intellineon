plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.openapi.generator") version "7.14.0"
}

group = "com.mateuszwozniak"
version = "1.2-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:


        bundledPlugin("com.intellij.database")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("neon-api-spec.json").asFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    apiPackage.set("com.mateuszwozniak.intellineon.api.generated.apis")
    modelPackage.set("com.mateuszwozniak.intellineon.api.generated.models")
    packageName.set("com.mateuszwozniak.intellineon.api.generated")
    configOptions.set(
        mapOf(
            "library" to "jvm-okhttp4",
            "serializationLibrary" to "moshi",
            "dateLibrary" to "java8",
            "useCoroutines" to "false",
            "enumPropertyNaming" to "UPPERCASE"
        )
    )
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252.25557"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    named("compileKotlin") {
        dependsOn("openApiGenerate")
    }

    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
