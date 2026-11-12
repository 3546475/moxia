pluginManagement {
    repositories {
        // 国内 runner（CNB）直连官方源容易超时：置 MOXIA_MIRROR=1 时阿里云优先、官方兜底。
        // 默认关闭 —— 本地与 GitHub Actions（runner 在境外）仍走官方源。
        val useMirror = System.getenv("MOXIA_MIRROR") == "1"
        if (useMirror) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Lets a local Miuix checkout (F:\AI code\miuix-ref) shadow the published artifacts:
        // `./gradlew :miuix-ui:publishToMavenLocal` there, then build the app here.
        mavenLocal()
        // 同上：MOXIA_MIRROR=1 时阿里云优先、官方兜底（jitpack 无阿里云镜像，保持直连）。
        if (System.getenv("MOXIA_MIRROR") == "1") {
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        // jitpack 上的构件只从 jitpack 取。Maven Central 对 com.github.* 记的 group 会回 429，
        // 而 429 是致命错误：Gradle 不会再往后找 jitpack，整次依赖解析直接失败。
        exclusiveContent {
            forRepository {
                maven { url = uri("https://jitpack.io") }
            }
            filter {
                includeGroup("com.github.gregcockroft")
                includeGroup("com.github.jitpack")
            }
        }
    }
}

rootProject.name = "Moxia"
include(":app")
include(":baselineprofile")
include(":benchmark")
