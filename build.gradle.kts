plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom("$rootDir/config/detekt/detekt.yml")
  baseline = file("$rootDir/config/detekt/baseline.xml")
  source.setFrom(files("app/src/main/java", "app/src/test/java"))
  parallel = true
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
  format("misc") {
    target("**/*.md", "**/.gitignore")
    trimTrailingWhitespace()
    indentWithSpaces()
    endWithNewline()
  }
  format("xml") {
    target("**/*.xml")
    targetExclude("**/build/**")
    trimTrailingWhitespace()
    indentWithSpaces(4)
    endWithNewline()
  }
}
