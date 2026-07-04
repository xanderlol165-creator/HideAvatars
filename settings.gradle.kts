@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven {
            name = "aliucord"
            url = uri("https://maven.aliucord.com/releases")
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "aliucord"
            url = uri("https://maven.aliucord.com/releases")
        }
    }
}

rootProject.name = "aliucord-plugins"
include(":plugins")

// 1. Manually include the first plugin and rename it
include(":plugins:MyFirstKotlinPlugin")
project(":plugins:MyFirstKotlinPlugin").name = "HideAvatarsToggle"

// 2. Manually include the Avatar Switcher
include(":plugins:AvatarSwitcher")
include(":plugins:DebateClip")
