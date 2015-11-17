import java.io.File

import sbt.Keys._
import sbt._

val cae_artifactory_releases =
  Resolver.url(
    "Artifactory Realm",
    url("https://cae-artrepo.jpl.nasa.gov/artifactory/ext-release-local")
  )(Resolver.mavenStylePatterns)

val cae_artifactory_snapshots = 
  Resolver.url(
    "Artifactory Realm",
    url("https://cae-artrepo.jpl.nasa.gov/artifactory/plugins-snapshot-local")
  )(Resolver.mavenStylePatterns)
  
ivyLoggingLevel := UpdateLogging.Full

logLevel in Compile := Level.Debug

persistLogLevel := Level.Debug

/*
TODO
separate out package zip into separate repo
ask robert about plugin/resource manager versioning info
add in version and release info into xml files from jenkins env var
look at https://github.jpl.nasa.gov/secae/sbt.mbee.plugin to see reusable funcs for building md plugins
*/

val commonSettings: Seq[Setting[_]] = Seq(
  publishMavenStyle := true,
  publishTo := Some(cae_artifactory_snapshots),
  fullResolvers ++= Seq(new MavenRepository("cae ext-release-local", "https://cae-artrepo.jpl.nasa.gov/artifactory/ext-release-local"),
                        new MavenRepository("cae plugins-snapshot-local", "https://cae-artrepo.jpl.nasa.gov/artifactory/plugins-snapshot-local")
                    ),
  autoScalaLibrary := false,
  // disable using the Scala version in output paths and artifacts
  crossPaths := false,
  publishArtifact in (Compile, packageBin) := true,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := true
)

def moduleSettings(moduleID: ModuleID): Seq[Setting[_]] =
  Seq(
    name := moduleID.name,
    organization := moduleID.organization,
    version := moduleID.revision
  )

val artifactPluginZipFile = TaskKey[File]("Location of mdk plugin zip file")
lazy val extractArchives = TaskKey[File]("extract-archives", "Extracts base md zip")
lazy val getMdClasspath = TaskKey[Seq[Attributed[File]]]("get md jar classpath")
lazy val buildMdk = TaskKey[File]("build-mdk", "construct md plugin folder structure after compiling")
lazy val genResourceDescriptor = TaskKey[File]("gen-resource-descriptor", "generate resource descriptor")
lazy val zipMdk = TaskKey[File]("zip-mdk", "zip up mdk plugin")

// lib_patches package
val lib_patches_packageID = "gov.nasa.jpl.cae.magicdraw.packages" % "cae_md18_0_sp4_lib_patches" % "1.0"
val lib_patches_packageA = Artifact(lib_patches_packageID.name, "zip", "zip")
val lib_patches_package_zipID = lib_patches_packageID.artifacts(lib_patches_packageA)

val mdk_pluginID = "gov.nasa.jpl.cae.magicdraw.plugins" % "mdk" % "2.3-SNAPSHOT"
val mdk_pluginA = Artifact(mdk_pluginID.name, "zip", "zip")
val mdk_plugin_zipID = mdk_pluginID.artifacts(mdk_pluginA)

lazy val plugin = (project in file("."))
  .settings(commonSettings)
  .settings(artifactPluginZipFile := { baseDirectory.value / "target" / "package" / "CAE.MDK.Plugin.zip" })
  .settings(addArtifact( mdk_pluginA, artifactPluginZipFile).settings: _*)
  .settings(moduleSettings(mdk_pluginID): _*)
  .settings(
    homepage := Some(url("https://github.jpl.nasa.gov/mbee-dev/mdk")),
    organizationHomepage := Some(url("http://cae.jpl.nasa.gov"))
  )
  .settings(
    unmanagedJars in Compile <++= getMdClasspath,
    resourceDirectory := baseDirectory.value / "target" / "package",
    libraryDependencies += lib_patches_package_zipID,
    publish <<= publish dependsOn zipMdk,

    extractArchives <<= (baseDirectory, update, streams) map { (base, up, s) =>
      val extractFolder = base / "target" / "expand"
      val filter = artifactFilter(`type`="zip", extension = "zip")
      val zips: Seq[File] = up.matching(filter)
      s.log.info(s"*** Got: ${zips.size} zips")
      zips.foreach { zip =>
        s.log.info(s"\n\nzip: $zip")
        val files = IO.unzip(zip, extractFolder)
        s.log.info(s"=> extracted ${files.size} files!")
      }
      extractFolder
    },
    
    getMdClasspath := {
        val mdbase = extractArchives.value
        val mdlibjars = (mdbase / "lib") ** "*.jar"
        val mdpluginjars = (mdbase / "plugins") ** "*.jar"
        val mdjars = mdlibjars +++ mdpluginjars
        mdjars.classpath
    },
    
    buildMdk := { 
      val mdkjar = (packageBin in Compile).value
      val zipfolder = baseDirectory.value / "target" / "zip"
      IO.copyFile(baseDirectory.value / "profiles" / "MDK" / "SysML Extensions.mdxml", zipfolder / "profiles" / "MDK" / "SysML Extensions.mdxml", true)
      IO.copyDirectory(baseDirectory.value / "data" / "diagrams", zipfolder / "data" / "defaults" / "data" / "diagrams", true)
      IO.copyDirectory(baseDirectory.value / "DocGenUserScripts", zipfolder / "DocGenUserScripts", true)
      IO.copyFile(mdkjar, zipfolder / "plugins" / "gov.nasa.jpl.mbee.docgen" / "DocGen-plugin.jar", true)
      IO.copyDirectory(baseDirectory.value / "lib", zipfolder / "plugins" / "gov.nasa.jpl.mbee.docgen" / "lib", true)
      
      val pluginxml = IO.read(baseDirectory.value / "src" / "main" / "resources" / "plugin.xml")
      val towrite = pluginxml.replaceAllLiterally("@release.version.internal@", sys.props.getOrElse("BUILD_NUMBER", "1"))
      IO.write(zipfolder / "plugins" / "gov.nasa.jpl.mbee.docgen" / "plugin.xml", towrite, append=false)
      //IO.copyFile(baseDirectory.value / "src" / "main" / "resources" / "plugin.xml", zipfolder / "plugins" / "gov.nasa.jpl.mbee.docgen" / "plugin.xml", true)
      //get env var BUILD_NUMBER, GIT_COMMIT, JOB_NAME, BUILD_ID (date)
      zipfolder
    },
    
    genResourceDescriptor := {
        val zipfolder = buildMdk.value
        val template = IO.read(baseDirectory.value / "data" / "resourcemanager" / "MDR_Plugin_Docgen_91110_descriptor_template.xml")
        //val filesInZip = zipfolder ** "*.*"
        val subpaths = Path.selectSubpaths(zipfolder, "*.*")
        val content = ("" /: subpaths) { 
            case (result, (file, subpath)) =>
                result + "<file from=\"" + subpath + "\" to=\"" + subpath + "\"/>\n"
        }
        //streams.value.log.info(content)
        //TODO need release version info in descriptor
        val towrite = template.replaceAllLiterally("@installation@", content)
                              .replaceAllLiterally("@release.version.internal@", sys.props.getOrElse("BUILD_NUMBER", "1"))
                              .replaceAllLiterally("@release.date@", sys.props.getOrElse("BUILD_ID", "2015-33-33"))
        IO.write(zipfolder / "data" / "resourcemanager" / "MDR_Plugin_Docgen_91110_descriptor.xml", towrite, append=false)
        zipfolder
    },
    
    zipMdk := {
        val zipfolder = genResourceDescriptor.value;
        IO.zip(allSubpaths(zipfolder), artifactPluginZipFile.value)
        artifactPluginZipFile.value
    }
  )