/**
 *  Copyright 2013 Wordnik, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.wordnik.swagger.codegen

import com.wordnik.swagger.codegen.language.CodegenConfig

import java.io.{FilenameFilter, File, FileWriter}

import scala.collection.mutable.{ ListBuffer, HashMap }
import scala.io.Source
import org.json4s.jackson.JsonMethods._
import org.json4s._
import scala.Some
import com.wordnik.swagger.model.{SwaggerSerializers, Model}
import scala.Tuple2

abstract class BasicGenerator extends CodegenConfig with PathUtil {
  def packageName = "com.wordnik.client"
  def templateDir = "src/main/resources/scala"
  def destinationDir = "generated-code/src/main/scala"
  def fileSuffix = ".scala"

  override def modelPackage: Option[String] = Some("com.wordnik.client.model")

  val codegen = new Codegen(this)

  def generateClient(args: Array[String]) = {
    generateClientWithoutExit(args)
    System.exit(0)
  }

  def generateClientWithoutExit(args: Array[String]) {
    if (args.length == 0) {
      throw new IllegalArgumentException("Path to JSON files is required.")
    }

    val basePath = args(0)

    implicit val formats = SwaggerSerializers.formats

    val jsonDir = new java.io.File(basePath)
    val jsonMap = jsonDir.list(new FilenameFilter {
      def accept(file: java.io.File, name: String): Boolean = name.endsWith(".json")
    }).map(fileName => {
      val json = Source.fromFile(basePath + "/" + fileName).mkString
      val doc = parse(json).extract[Model]
      Tuple2(doc.id, doc)
    }).toMap

    val jsonBundle = prepareModelMap(jsonMap)
    val jsonFiles = bundleToSource(jsonBundle, modelTemplateFiles.toMap)
    writeFiles(jsonFiles)
  }

  def writeFiles(sourceList: List[(String, String)]) {
    sourceList.map(m => {
      val filename = m._1

      val file = new java.io.File(filename)
      file.getParentFile.mkdirs

      val fw = new FileWriter(filename, false)
      fw.write(m._2 + "\n")
      fw.close()
      println("wrote model " + filename)
    })
  }

  /**
   * creates a map of models and properties needed to write source
   */
  def prepareModelMap(models: Map[String, Model]): List[Map[String, AnyRef]] = {
    (for ((name, schema) <- models) yield {
      if (!defaultIncludes.contains(name)) {
        val m = new HashMap[String, AnyRef]
        m += "name" -> toModelName(name)
        m += "className" -> name
        m += "filename" -> toModelFilename(name)
        m += "models" -> List((name, schema))
        m += "package" -> modelPackage
        m += "outputDirectory" -> (destinationDir + File.separator + modelPackage.getOrElse("").replace(".", File.separator))
        m += "newline" -> "\n"

        Some(m.toMap)
      }
      else None
    }).flatten.toList
  }

  def bundleToSource(bundle:List[Map[String, AnyRef]], templates: Map[String, String]): List[(String, String)] = {
    val output = new ListBuffer[(String, String)]
    bundle.foreach(m => {
      for ((file, suffix) <- templates) {
        output += Tuple2(m("outputDirectory").toString + File.separator + m("filename").toString + suffix, codegen.generateSource(m, file))
      }
    })
    output.toList
  }
}
