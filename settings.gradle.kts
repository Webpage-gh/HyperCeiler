@file:Suppress("UnstableApiUsage")

pluginManagement {
    // 国内镜像仅在本地构建时启用；CI（GitHub Actions）直连官方仓库，
    // 避免镜像网关 5xx 导致 Gradle 直接中止依赖解析。
    val useMirror = System.getenv("CI").isNullOrEmpty()
    repositories {
        maven("https://jitpack.io")
        maven("https://api.xposed.info")
        if (useMirror) {
            maven("https://mirrors.tuna.tsinghua.edu.cn/maven/")
            maven("https://maven.aliyun.com/repository/google")
            // maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    val useMirror = System.getenv("CI").isNullOrEmpty()
    repositories {
        maven("https://jitpack.io")
        maven("https://api.xposed.info")
        if (useMirror) {
            maven("https://mirrors.tuna.tsinghua.edu.cn/maven/")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "HyperCeiler"
include(":app", ":hidden-api", ":app:processor")
