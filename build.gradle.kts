plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.spotless)
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
