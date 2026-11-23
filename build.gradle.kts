plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// --- Test locale -------------------------------------------------------------------------
//
// The suite checks the **French** interface, the project's reference language: several tests
// compare strings built from the locale of the JVM that runs them. Without this
// declaration, the suite was green on a French-speaking workstation and red on an English-speaking
// server (19 `ComparisonFailure` on the public repository's CI), with no fault in the code.
//
// Measurement, same test class, a single machine: `LC_ALL=en_US.UTF-8` -> 2 failures,
// `LC_ALL=fr_FR.UTF-8` -> success.
//
// The test JVM locale is therefore declared **here, once**: the suite yields the same
// verdict everywhere, and what is checked remains what the application displays in French.
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("user.language", "fr")
        systemProperty("user.country", "FR")
    }
}
