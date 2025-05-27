plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.liuhao"
version = "1.0.2-SNAPSHOT"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2024.3.5")
    type.set("IC") // Target IntelliJ IDEA Community Edition for broader compatibility

    plugins.set(listOf("PythonCore:243.22562.145"))
    downloadSources.set(false)
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("243")
        untilBuild.set("251.*")
        
        // Add plugin compatibility information
        pluginDescription.set("""
            This plugin customizes Python import statements for specific directories.
            Instead of the standard PyCharm format 'from a.b.c import d',
            modules under specific directories will be imported as 'import a.b.c.d as d'
            
            Requirements: This plugin requires PyCharm or IntelliJ IDEA with the Python plugin installed.
        """.trimIndent())
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
