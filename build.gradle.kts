import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.3") {
            useInstaller = false
            useCache = true
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginVerification {
        ides {
            recommended()
        }
        failureLevel.set(listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.INVALID_PLUGIN
        ))
    }
}


tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    patchPluginXml {
        sinceBuild.set("253")
        untilBuild.set("261.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(providers.gradleProperty("intellijPublishToken"))
    }
}

