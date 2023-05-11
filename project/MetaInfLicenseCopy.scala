import sbt.Keys._
import sbt._

/**
  * Copies LICENSE file into jar META-INF dir
  */
object MetaInfLicenseCopy {

  val settings: Seq[Setting[_]] = inConfig(Compile)(
    Seq(
      resourceGenerators += copyFileToMetaInf(resourceManaged, "LICENSE")
    )
  )

  def copyFileToMetaInf(dir: SettingKey[File], fileName: String) = Def.task[Seq[File]] {
    val fromFile = (LocalRootProject / baseDirectory).value / fileName
    val toFile = resourceManaged.value / "META-INF" / fileName
    IO.copyFile(fromFile, toFile)
    Seq(toFile)
  }

}
