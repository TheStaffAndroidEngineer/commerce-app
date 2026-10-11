import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

buildscript { dependencies { classpath(libs.kotlinGradlePlugin) } }
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.crashlytics) apply false
}

/** Captures dependency declarations during configuration; execution needs no Project access. */
abstract class VerifyModuleBoundaries : DefaultTask() {
    @get:Input abstract val modulePaths: ListProperty<String>
    @get:Input abstract val declaredEdges: ListProperty<String>
    @get:Input abstract val generatedTestEdges: ListProperty<String>
    @get:Input abstract val namespaceConfigurations: ListProperty<String>
    @get:OutputFile abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val modules = setOf(
            ":app", ":core:model", ":core:data", ":core:designsystem",
            ":feature:catalog", ":feature:saved", ":feature:cart",
        )
        // Gradle creates these empty parents for the nested project paths above.
        val namespaces = setOf(":core", ":feature")
        val permitted = setOf(
            ":app|implementation|:core:model",
            ":app|implementation|:core:data",
            ":app|implementation|:core:designsystem",
            ":app|implementation|:feature:catalog",
            ":app|implementation|:feature:saved",
            ":app|implementation|:feature:cart",
            ":core:data|api|:core:model",
            ":feature:catalog|api|:core:model",
            ":feature:catalog|implementation|:core:designsystem",
            ":feature:saved|api|:core:model",
            ":feature:saved|implementation|:core:designsystem",
            ":feature:cart|api|:core:model",
            ":feature:cart|implementation|:core:designsystem",
        )
        val actual = declaredEdges.get().toSet()
        val paths = modulePaths.get().toSet()
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(buildString {
                appendLine("Common Goods project dependency declarations")
                appendLine("Project paths: ${paths.sorted().joinToString()}")
                appendLine("Format: owner|configuration|target")
                declaredEdges.get().sorted().forEach { appendLine(it) }
                appendLine("AGP-generated test self-dependencies excluded from the policy:")
                generatedTestEdges.get().sorted().forEach { appendLine(it) }
                appendLine("Namespace configurations: ${namespaceConfigurations.get()}")
            })
        }
        check(paths == modules + namespaces) {
            "MODULE REGISTRY REQUIRES REVIEW: extra=${paths - modules - namespaces}; " +
                "missing=${modules + namespaces - paths}"
        }
        check(namespaceConfigurations.get().isEmpty()) {
            "NAMESPACE REQUIRES REVIEW: ${namespaceConfigurations.get()}"
        }
        check(actual == permitted) {
            "MODULE BOUNDARY VIOLATION: extra=${actual - permitted}; missing=${permitted - actual}"
        }
        logger.lifecycle("COMMON GOODS MODULE BOUNDARIES PASS: ${modules.size} modules, ${actual.size} declared edges")
    }
}

val moduleBoundaryCheck = tasks.register<VerifyModuleBoundaries>("verifyModuleBoundaries") {
    group = "verification"
    description = "Verifies the reviewed Common Goods project dependency graph."
    reportFile.set(layout.buildDirectory.file("reports/module-boundaries.txt"))
}

gradle.projectsEvaluated {
    val allEdges = subprojects.flatMap { owner ->
        owner.configurations.filter { it.isCanBeDeclared }.flatMap { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).map { dependency ->
                Triple(owner.path, configuration.name, dependency.path)
            }
        }
    }
    val generatedTestSelfEdge = Regex("(debug|release)(AndroidTest|UnitTest)(CompileClasspath|RuntimeClasspath)")
    val (generated, declarations) = allEdges.partition { (owner, configuration, target) ->
        owner == target && generatedTestSelfEdge.matches(configuration)
    }
    moduleBoundaryCheck.configure {
        modulePaths.set(subprojects.map { it.path }.sorted())
        declaredEdges.set(declarations.map { (owner, configuration, target) -> "$owner|$configuration|$target" }.sorted())
        generatedTestEdges.set(generated.map { (owner, configuration, target) -> "$owner|$configuration|$target" }.sorted())
        namespaceConfigurations.set(subprojects.filter { it.path in setOf(":core", ":feature") }
            .flatMap { owner -> owner.configurations.map { "${owner.path}|${it.name}" } }.sorted())
    }
}
