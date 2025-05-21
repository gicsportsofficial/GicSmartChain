import sbt.Keys._
import sbt._

object RunApplicationSettings extends AutoPlugin {
  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(
      Seq(
        mainClass := Some("com.gicsports.Application"),
        discoveredMainClasses := (Compile / mainClass).value.toSeq,
        run / fork := true
      ))
}
