package com.twitter.intellij.pants

import java.nio.file.Paths
import com.twitter.intellij.pants.PantsIdeaPluginTest.TestData
import org.virtuslab.ideprobe.Assertions
import org.virtuslab.ideprobe.Config
import org.virtuslab.ideprobe.ConfigFormat
import org.virtuslab.ideprobe.protocol.FileRef
import org.virtuslab.ideprobe.protocol.ModuleRef
import org.virtuslab.ideprobe.protocol.ProjectRef
import org.virtuslab.ideprobe.protocol.Reference
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

object PantsIdeaPluginTest extends ConfigFormat {
  object TestData {
    case class Module(name: String, contentRoots: Seq[String])
    implicit val moduleReader: ConfigReader[Module] = deriveReader[Module]
  }
}

final class PantsIdeaPluginTest extends PantsTestSuite with Assertions {
  @Test
  def checkModules(): Unit = {
    fixtureFromConfig().run { intelliJ =>
      def relative(absolutePath: String): String = intelliJ.workspace.relativize(Paths.get(absolutePath)).toString

      val projectModel = intelliJ.probe.projectModel()

      val projectName = intelliJ.config[String]("project.name")

      val expectedModules = intelliJ.config[Seq[TestData.Module]]("project.modules")
      val importedModules =
        projectModel.modules.map(module => TestData.Module(module.name, module.contentRoots.map(relative)))

      // assertEquals(projectName, projectModel.name)
      Assert.assertTrue(
        s"Expected modules: $expectedModules, actual modules: $importedModules",
        expectedModules.toSet.subsetOf(importedModules.toSet))
    }
  }

  @Test
  def setsSdkOnImport(): Unit = {
    fixtureFromConfig()
      .withDisplay
      .run { intellij =>
        val projectSdk = intellij.probe.projectSdk()

        val project = intellij.probe.projectModel()

        val modulesWithoutSdk = project.modules
          .filter(module => module.kind.isDefined)
          .map(m => ModuleRef(m.name))
          .filter(module => intellij.probe.moduleSdk(module).isEmpty)

        Assert.assertTrue(s"Project without sdk: ${project.name}", projectSdk.isDefined)
        Assert.assertTrue(s"Modules without sdk: $modulesWithoutSdk", modulesWithoutSdk.isEmpty)
      }
  }

  @Test
  def referencesOtherBUILDFiles(): Unit = {
    val config = Config.fromClasspath("ThriftIdeaPluginTest/ideprobe.conf")
    fixtureFromConfig(config).run { intellij =>
      val project = ProjectRef("java.app:main")

      def file(name: String): FileRef = {
        val path = intellij.workspace.resolve(name).toString
        FileRef(project, path)
      }

      val thriftLibBuild = file("thrift-lib/BUILD")
      val thirdPartyBuild = file("3rdParty/BUILD")

      val refs = intellij.probe.fileReferences(path = intellij.workspace.resolve("java-app/BUILD").toString)

      assertEquals(2, refs.size)
      assertContains(refs)(
        Reference("3rdParty", Reference.Target.File(thirdPartyBuild)),
        Reference("thrift-lib", Reference.Target.File(thriftLibBuild))
      )
    }
  }

}
