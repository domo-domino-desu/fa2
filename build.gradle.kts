import me.domino.fa2.build.registerGenerateAppIconsTask

plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinx.serialization) apply false
}

allprojects {
  configurations.configureEach {
    resolutionStrategy.eachDependency {
      when ("${requested.group}:${requested.name}") {
        "io.netty:netty-codec",
        "io.netty:netty-codec-http",
        "io.netty:netty-codec-http2",
        "io.netty:netty-common",
        "io.netty:netty-handler",
        "io.netty:netty-handler-proxy" -> {
          useVersion("4.1.133.Final")
          because("Dependabot security alerts require Netty 4.1.133.Final or newer.")
        }
        "org.bouncycastle:bcpkix-jdk15to18",
        "org.bouncycastle:bcprov-jdk15to18",
        "org.bouncycastle:bcutil-jdk15to18" -> {
          useVersion("1.84")
          because("Dependabot security alerts require BouncyCastle 1.84 or newer.")
        }
        "org.apache.commons:commons-lang3" -> {
          useVersion("3.18.0")
          because("Dependabot security alerts require Commons Lang 3.18.0 or newer.")
        }
        "org.bitbucket.b_c:jose4j" -> {
          useVersion("0.9.6")
          because("Dependabot security alerts require jose4j 0.9.6 or newer.")
        }
        "org.codehaus.plexus:plexus-utils" -> {
          useVersion("3.6.1")
          because("Dependabot security alerts require plexus-utils 3.6.1 or newer.")
        }
        "org.jdom:jdom2" -> {
          useVersion("2.0.6.1")
          because("Dependabot security alerts require JDOM 2.0.6.1 or newer.")
        }
      }
    }
  }
}

registerGenerateAppIconsTask()
