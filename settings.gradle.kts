pluginManagement {
  repositories {
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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

abstract class EnsurePreviewServerRunning :
  org.gradle.api.provider.ValueSource<Boolean, org.gradle.api.provider.ValueSourceParameters.None> {
  override fun obtain(): Boolean {
    val isListening =
      try {
        java.net.Socket().use { socket ->
          socket.connect(java.net.InetSocketAddress("127.0.0.1", 3000), 200)
          true
        }
      } catch (_: Exception) {
        false
      }
    if (!isListening) {
      val serverScript = java.io.File("/app/applet/server.cjs")
      if (serverScript.exists()) {
        try {
          ProcessBuilder("sh", "-c", "nohup node /app/applet/server.cjs >/dev/null 2>&1 &")
            .directory(java.io.File("/app/applet"))
            .start()
        } catch (_: Exception) {}
      }
    }
    return true
  }
}

val previewServerReady = providers.of(EnsurePreviewServerRunning::class) {}.get()

rootProject.name = "Netcon"

include(":app")
