#!/usr/bin/env amm

import java.io.File
import ammonite.ops._
import scala.collection.JavaConverters._

// import $ivy.`org.scalaz::scalaz-core:7.3.0-M9`, scalaz._, Scalaz._
// import $ivy.`com.slamdata::matryoshka-core:0.16.13`, matryoshka._, matryoshka.data._, matryoshka.implicits._
// import $ivy.`io.circe::circe-core:0.7.0`, io.circe._
// import $ivy.`commons-io:commons-io:2.5`, org.apache.commons.io.FileUtils

import $ivy.`com.functortech::serenity:0.1.0-SNAPSHOT`, serenity._, Serenity._
import org.apache.commons.io.{IOUtils, FileUtils}

// import $ivy.`org.typelevel::cats:0.9.0`, cats._

@main
def main(): Unit = {
  val target  = "posts/typeclasses/implicits.md"
  val outFile = s"_site/$target.html"

  println("Go!")
  val proc = Runtime.getRuntime.exec(
    s"pandoc $target -s" +
    " --filter ./plugins/pandoc-include-code/dist/build/pandoc-include-code/pandoc-include-code"
  )
  val out  = IOUtils.toString(proc.getInputStream, "utf8")
  FileUtils.writeStringToFile(new File(outFile), out, "utf8")
  println("Done")
}
