import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // Match the Kotlin stdlib bundled with the targeted IDE so the plugin stays
        // loadable on every supported Android Studio / IntelliJ build.
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Deliberately no `kotlin("test")`: it drags kotlin-stdlib into the test sandbox's plugin
    // `lib/` folder, which clashes with the IDE's own Kotlin builtins under K2 analysis. The
    // shipped plugin bundles no stdlib either (see `kotlin.stdlib.default.dependency`).
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // The IDE test fixtures are built on JUnit 3/4 (`BasePlatformTestCase` extends
    // `junit.framework.TestCase`), so the JUnit 4 jar is needed to compile against them.
    testImplementation("junit:junit:4.13.2")
    // The IDE test fixtures are JUnit 3 based (`BasePlatformTestCase`).
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")

    intellijPlatform {
        // Android Studio is built on the IntelliJ platform, so compiling against IntelliJ IDEA
        // Community is enough: the plugin only uses core `com.intellij.modules.platform` APIs.
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))

        // Only used by the integration test, which drives real Kotlin code completion to verify
        // that this contributor runs before the Kotlin one.
        bundledPlugin("org.jetbrains.kotlin")

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // No upper bound: the plugin uses stable APIs only and should keep working
            // on future Android Studio releases without a rebuild.
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}

tasks.test {
    useJUnitPlatform()

    // Android Studio runs the Kotlin plugin in K2 mode, so that is what the integration test
    // covers by default. Run `./gradlew test -PkotlinK2Mode=false` to check K1 as well.
    systemProperty("idea.kotlin.plugin.use.k2", providers.gradleProperty("kotlinK2Mode").orElse("true").get())
}
