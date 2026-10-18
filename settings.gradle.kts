// 本机（离线沙箱）内置的 Maven 镜像：仅在目录存在时启用，外部环境自动忽略
pluginManagement {
    repositories {
        val localRepo = file("/opt/maven-repo")
        if (localRepo.isDirectory) {
            maven { url = uri(localRepo) }
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val localRepo = file("/opt/maven-repo")
        if (localRepo.isDirectory) {
            maven { url = uri(localRepo) }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "DyClean"
include(":app")
