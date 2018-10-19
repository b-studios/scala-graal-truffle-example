lazy val graalvmVersion = "1.0.0-rc6"
lazy val languageName = "examplelanguage"

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    name := languageName,
    version := "0.1.0",
    assemblyOutputPath in assembly := file("target") / s"${languageName}.jar")

lazy val commonSettings = Seq(
  scalaVersion := "2.13.0-M4",
  test in assembly := {},
  assemblyExcludedJars in assembly := {
    val cp = (fullClasspath in assembly).value
    // https://stackoverflow.com/questions/41894055/how-to-exclude-jar-in-final-sbt-assembly-plugin
    cp filter { f =>
      val path = f.data.toString
      (path contains "com.oracle.truffle") ||
      (path contains "org.graalvm")
    }
  },

  // we fork the JVM to pass the Java Options
  Compile / run / fork := true,
  javaOptions ++= dump,

  libraryDependencies ++= Seq(
    "com.oracle.truffle" % "truffle-api" % graalvmVersion,
    "com.oracle.truffle" % "truffle-dsl-processor" % graalvmVersion,
    "com.oracle.truffle" % "truffle-tck" % graalvmVersion,
    "org.graalvm" % "graal-sdk" % graalvmVersion
  )
)

lazy val dump = Seq(
  "-Dgraal.Dump=Truffle:1",
  "-Dgraal.TruffleBackgroundCompilation=false",
  "-Dgraal.TraceTruffleCompilation=true",
  "-Dgraal.TraceTruffleCompilationDetails=true",
  "-XX:-UseJVMCIClassLoader"
)
