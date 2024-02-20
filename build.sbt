import sbtghactions.JavaSpec.Distribution.Zulu
import sbtghactions.UseRef.Public

organization := "com.github.swagger-akka-http"

name := "swagger-pekko-http"

val swaggerVersion = "2.2.20"
val pekkoVersion = "1.0.2"
val pekkoHttpVersion = "1.0.1"
val jacksonVersion = "2.16.1"
val slf4jVersion = "2.0.12"
val scala213 = "2.13.12"

ThisBuild / scalaVersion := scala213
ThisBuild / crossScalaVersions := Seq(scala213, "2.12.18", "3.3.1")

update / checksums := Nil

//resolvers ++= Resolver.sonatypeOssRepos("snapshots")
//resolvers += "Apache Snapshot Repo" at "https://repository.apache.org/content/groups/snapshots/"

autoAPIMappings := true

apiMappings ++= {
  def mappingsFor(organization: String, names: List[String], location: String, revision: (String) => String = identity): Seq[(File, URL)] =
    for {
      entry: Attributed[File] <- (Compile / fullClasspath).value
      module: ModuleID <- entry.get(moduleID.key)
      if module.organization == organization
      if names.exists(module.name.startsWith)
    } yield entry.data -> url(location.format(revision(module.revision)))

  val mappings: Seq[(File, URL)] =
    mappingsFor("org.scala-lang", List("scala-library"), "https://scala-lang.org/api/%s/") ++
      mappingsFor("org.apache.pekko", List("pekko-actor", "pekko-stream"), "https://pekko.apache.org/api/pekko/%s/") ++
      mappingsFor("org.apache.pekko", List("pekko-http"), "https://pekko.apache.org/api/pekko-http/%s/") ++
      mappingsFor("io.swagger.core.v3", List("swagger-core-jakarta"), "https://javadoc.io/doc/io.swagger.core.v3/swagger-core/%s/") ++
      mappingsFor("io.swagger.core.v3", List("swagger-jaxrs2-jakarta"), "https://javadoc.io/doc/io.swagger.core.v3/swagger-jaxrs2/%s/") ++
      mappingsFor("io.swagger.core.v3", List("swagger-models-jakarta"), "https://javadoc.io/doc/io.swagger.core.v3/swagger-models/%s/") ++
      mappingsFor("com.fasterxml.jackson.core", List("jackson-core"), "https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-core/%s/") ++
      mappingsFor("com.fasterxml.jackson.core", List("jackson-databind"), "https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/%s/")

  mappings.toMap
}

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  "org.apache.pekko" %% "pekko-testkit" % pekkoVersion % Test,
  "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpVersion % Test,
  "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % Test,
  "io.swagger.core.v3" % "swagger-core-jakarta" % swaggerVersion,
  "io.swagger.core.v3" % "swagger-annotations-jakarta" % swaggerVersion,
  "io.swagger.core.v3" % "swagger-models-jakarta" % swaggerVersion,
  "io.swagger.core.v3" % "swagger-jaxrs2-jakarta" % swaggerVersion,
  "com.github.swagger-akka-http" %% "swagger-scala-module" % "2.12.3",
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
  "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.json4s" %% "json4s-native" % "4.0.7" % Test,
  "jakarta.ws.rs" % "jakarta.ws.rs-api" % "3.0.0" % Test,
  "joda-time" % "joda-time" % "2.12.7" % Test,
  "org.joda" % "joda-convert" % "2.2.3" % Test,
  "org.slf4j" % "slf4j-simple" % slf4jVersion % Test
)

Test / testOptions += Tests.Argument("-oD")

Test / publishArtifact := false

Test / parallelExecution := false

pomIncludeRepository := { _ => false }

homepage := Some(url("https://github.com/swagger-akka-http/swagger-pekko-http"))

licenses := Seq("The Apache Software License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))

pomExtra := (
  <developers>
    <developer>
      <id>pjfanning</id>
      <name>PJ Fanning</name>
      <url>https://github.com/pjfanning</url>
    </developer>
    <developer>
      <id>mhamrah</id>
      <name>Michael Hamrah</name>
      <url>http://michaelhamrah.com</url>
    </developer>
    <developer>
      <id>efuquen</id>
      <name>Edwin Fuquen</name>
      <url>http://parascal.com</url>
    </developer>
    <developer>
      <id>rliebman</id>
      <name>Roberto Liebman</name>
      <url>https://github.com/rleibman</url>
    </developer>
  </developers>)

MetaInfLicenseCopy.settings

ThisBuild / githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("coverage", "test", "coverageReport")))
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec(Zulu, "8"), JavaSpec(Zulu, "11"), JavaSpec(Zulu, "17"))
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("main")),
  RefPredicate.Equals(Ref.Branch("swagger-1.5")),
  RefPredicate.StartsWith(Ref.Tag("v"))
)

/*
ThisBuild / githubWorkflowBuildPostamble := Seq(
  WorkflowStep.Use(Public("codecov", "codecov-action", "v3"), Map("fail_ci_if_error" -> "true"))
)
*/

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
      "CI_SNAPSHOT_RELEASE" -> "+publishSigned"
    )
  )
)
