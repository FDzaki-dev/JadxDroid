import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.detekt)
}

// Release version: "<upstream jadx version>-android.<n>", injected by the release workflow from the git tag.
val releaseVersion: String = (findProperty("releaseVersion") as String?) ?: "1.5.6-android.2"
val versionMatch = requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)-android\.(\d+)""").matchEntire(releaseVersion)) {
	"releaseVersion harus berformat X.Y.Z-android.N, dapat: $releaseVersion"
}
val (vMajor, vMinor, vPatch, vBuild) = versionMatch.destructured.toList().map(String::toInt)

// Signing material comes from the environment (CI decodes the keystore secret); never hardcoded.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
	namespace = "com.jadxdroid.app"
	compileSdk = 35

	defaultConfig {
		applicationId = "com.jadxdroid.app"
		minSdk = 31
		targetSdk = 35
		versionCode = (vMajor * 10_000 + vMinor * 100 + vPatch) * 100 + vBuild
		versionName = releaseVersion
	}

	signingConfigs {
		if (hasReleaseSigning) {
			create("release") {
				storeFile = file(requireNotNull(keystorePath))
				storePassword = System.getenv("KEYSTORE_PASSWORD")
				keyAlias = System.getenv("KEY_ALIAS")
				keyPassword = System.getenv("KEY_PASSWORD")
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			signingConfig = signingConfigs.findByName("release")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}

	buildFeatures {
		compose = true
	}

	packaging {
		resources {
			excludes += setOf(
				"/META-INF/{AL2.0,LGPL2.1}",
				"/META-INF/DEPENDENCIES",
				"/META-INF/LICENSE*",
				"/META-INF/NOTICE*",
				"/META-INF/INDEX.LIST",
			)
		}
	}

	lint {
		abortOnError = false
		checkDependencies = true
		textReport = true
		textOutput = layout.buildDirectory.file("reports/lint/lint-results.txt").get().asFile
		htmlReport = false
		xmlReport = false
		sarifReport = false
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

detekt {
	buildUponDefaultConfig = true
	config.setFrom(rootProject.file("config/detekt/detekt.yml"))
	parallel = true
}

tasks.withType<Detekt>().configureEach {
	reports {
		txt.required.set(true)
		html.required.set(false)
		xml.required.set(false)
		sarif.required.set(false)
		md.required.set(false)
	}
}

dependencies {
	implementation(project(":jadx-engine"))

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.ktx)
	implementation(libs.androidx.work.runtime.ktx)
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.compose.material3)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.okio)

	// SLF4J 2 provider for Android: routes jadx logging to logcat (see assets/logback.xml).
	implementation(libs.logback.android)
}
