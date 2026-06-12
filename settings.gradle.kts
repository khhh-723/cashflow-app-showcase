pluginManagement {
    repositories {
        maven {
            url = uri(settingsDir.resolve(".gradle-home/local-maven"))
        }
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://developer.huawei.com/repo/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(settingsDir.resolve(".gradle-home/local-maven"))
        }
        google()
        mavenCentral()
        maven("https://developer.huawei.com/repo/")
    }
}

rootProject.name = "SuishouLedger"
include(":app")
