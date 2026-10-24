plugins {
	alias(libs.plugins.android.library)
}

val jadxRoot = rootProject.layout.projectDirectory.dir("third_party/jadx")

// Upstream jadx 1.5.6 modules compiled straight from source into this Android library.
// GUI, CLI, java-convert (R8/dx), smali/raung/aab/xapk plugins are intentionally not included.
val upstreamModules = listOf(
	"jadx-core",
	"jadx-commons/jadx-zip",
	"jadx-plugins/jadx-input-api",
	"jadx-plugins/jadx-dex-input",
	"jadx-plugins/jadx-java-input",
)

// Upstream files that are replaced by src/overrides/java (path relative to src/main/java).
val overriddenFiles = listOf(
	"jadx/api/gui/tree/ITreeNode.java",
	"jadx/api/plugins/gui/ISettingsGroup.java",
	"jadx/api/plugins/gui/JadxGuiContext.java",
	"jadx/core/utils/DotGraphUtils.java",
	"jadx/core/utils/android/Res9patchStreamDecoder.java",
	"jadx/core/utils/files/FileUtils.java",
	"jadx/zip/fallback/FallbackZipParser.java",
)

val prepareJadxSources by tasks.registering(Sync::class) {
	duplicatesStrategy = DuplicatesStrategy.FAIL
	into(layout.buildDirectory.dir("generated/jadx/java"))
	upstreamModules.forEach { module ->
		from(jadxRoot.dir("$module/src/main/java")) {
			exclude(overriddenFiles)
		}
	}
	from(layout.projectDirectory.dir("src/overrides/java"))
}

android {
	namespace = "jadx.android.engine"
	compileSdk = 35

	defaultConfig {
		minSdk = 31
		consumerProguardFiles("consumer-rules.pro")
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}

	sourceSets {
		getByName("main") {
			java.srcDir(prepareJadxSources.map { it.destinationDir })
			resources.srcDir(jadxRoot.dir("jadx-core/src/main/resources").asFile)
		}
	}

	lint {
		abortOnError = false
		checkReleaseBuilds = false
	}
}

tasks.named("preBuild") {
	dependsOn(prepareJadxSources)
}

dependencies {
	compileOnly(libs.jetbrains.annotations)
	api(libs.slf4j.api)
	implementation(libs.gson)
	implementation(libs.guava)
	implementation(libs.smali.baksmali) {
		exclude(group = "com.beust", module = "jcommander")
	}
	implementation(libs.raung.disasm)
}
