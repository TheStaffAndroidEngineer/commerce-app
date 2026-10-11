pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "common-goods"
include(":app", ":core:model", ":core:data", ":core:designsystem", ":feature:catalog", ":feature:saved", ":feature:cart")
