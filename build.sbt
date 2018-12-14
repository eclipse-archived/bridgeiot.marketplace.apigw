/**
 * Copyright (c) 2016-2017 Atos IT Solutions and Services GmbH
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
import com.typesafe.sbt.packager.docker.Cmd
import NativePackagerHelper._
import com.typesafe.sbt.packager.SettingsHelper._

val env = scala.util.Properties.envOrElse("PROJECTDEPS", "")

// usage of the flag: sbt "-DenableRetrieveManaged=true"
val enableRetrieveManagedProp = sys.props.getOrElse("enableRetrieveManaged", "false")

lazy val commonSettings = Seq(
  organization := "org.eclipse.bridgeiot",
  version := "0.9-SNAPSHOT",
  scalaVersion := "2.12.3",
  
  retrieveManaged := enableRetrieveManagedProp.toBoolean,
  
  resolvers += (publishTo in Universal).value.get,
  publishTo in Universal := Some(Resolver.mavenLocal),

  resolvers += "Local Nexus" at "https://nexus.big-iot.org/content/repositories/snapshots/"
)

val apiLocalDeps: List[ClasspathDep[ProjectReference]] = if (env == "")
  List(ProjectRef(file("../marketplace-microservice"), "marketplace-microservice"),
    ProjectRef(file("../exchange"), "exchange-api"))
else Nil

val apiLibDeps = if (apiLocalDeps.isEmpty)
  List("org.eclipse.bridgeiot" %% "marketplace-microservice" % "0.1-SNAPSHOT",
    "org.eclipse.bridgeiot" %% "exchange-api" % "0.9-SNAPSHOT")
else Nil

lazy val `marketplace-access-api` = (project in file("marketplace-access-api"))
  .settings(
    mappings in Universal := {if (enableRetrieveManagedProp.toBoolean) directory("lib_managed") else (mappings in Universal).value},
    mappings in Universal += { //   Add version.properties file to target directory
      val versionFile = target.value / "version.properties"
      IO.write(versionFile, s"version=${version.value}")
      versionFile -> "version.properties"
    },
    makeDeploymentSettings(Universal, packageBin in Universal, "zip"), 
    commonSettings,
    name := "marketplace-access-api",

    publishTo := Some("Local Nexus" at "https://nexus.big-iot.org/content/repositories/snapshots/"),
    credentials += Credentials(file("./.credentials")),
    credentials += Credentials(System.getenv("NEXUS_REALM"), System.getenv("NEXUS_HOST"),
      System.getenv("NEXUS_USER"), System.getenv("NEXUS_PASSWORD")),
    libraryDependencies ++= apiLibDeps
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(apiLocalDeps: _*)

val implLocalDeps = apiLocalDeps
val implLibDeps = apiLibDeps

lazy val `marketplace-apigw` = (project in file("."))
  .settings(
    mappings in Universal := {if (enableRetrieveManagedProp.toBoolean) directory("lib_managed") else (mappings in Universal).value},
    mappings in Universal += { //   Add version.properties file to target directory
      val versionFile = target.value / "version.properties"
      IO.write(versionFile, s"version=${version.value}")
      versionFile -> "version.properties"
    },
    makeDeploymentSettings(Universal, packageBin in Universal, "zip"), 
    commonSettings,
    name := "marketplace-apigw",
    mainClass in Compile := Some("access.server.Server"),

    libraryDependencies ++= implLibDeps ++ Seq(
      "org.eclipse.bridgeiot" %% "exchange-api" % "0.9-SNAPSHOT"
    ),

    dockerCommands := Seq(
      Cmd("FROM", "openjdk:8-jre-alpine"),
      Cmd("RUN", "apk update && apk add bash && rm -rf /var/cache/apk/* && adduser -S -H -u 2020 bigiot bigiot"),
      Cmd("EXPOSE", "8080"),
      Cmd("ADD", "opt /opt"),
      Cmd("WORKDIR", s"/opt/marketplace-apigw"),
      Cmd("RUN", "chmod", "+x", s"/opt/marketplace-apigw/bin/marketplace-apigw"),
      Cmd("USER", "bigiot"),
      Cmd("CMD", s"/opt/marketplace-apigw/bin/marketplace-apigw")
    ),

    dockerRepository := Some("registry.gitlab.com/big-iot"),

    defaultLinuxInstallLocation in Docker := "/opt/marketplace-apigw"
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .dependsOn(`marketplace-access-api`)
  .dependsOn(implLocalDeps: _*)
