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

import com.wordnik.swagger.model._

import java.io.File

object BasicPythonGenerator extends BasicPythonGenerator {
  def main(args: Array[String]) = generateClient(args)
}

class BasicPythonGenerator extends BasicGenerator {
  // template used for models
  modelTemplateFiles += "model.mustache" -> ".py"

  // location of templates
  override def templateDir = "python"

  // where to write generated code
  override def destinationDir = "generated-code/python"

  // package for models
  override def modelPackage = Some("models")

  // file suffix
  override def fileSuffix = ".py"

  // reserved words which need special quoting
  // These will all be object properties, in which context we don't need
  // to worry about escaping them for Python.
  override def reservedWords = Set()

  // import/require statements for specific datatypes
  override def importMapping = Map()

  override def typeMapping = Map(
    "float" -> "float",
    "long" -> "long",
    "double" -> "float",
    "Array" -> "list",
    "boolean" -> "bool",
    "string" -> "str",
    "Date" -> "datetime"
  )

  override def toDeclaredType(dt: String): String = {
    val declaredType = typeMapping.getOrElse(dt, dt)
    declaredType.startsWith("Array") match {
      case true => {
        val innerType = dt.dropRight(1).substring(6)
        typeMapping.contains(innerType) match {
          case true => "list[" + typeMapping(innerType) + "]"
          case false => "list[" + innerType + "]"
        }
      }
      case _ => {
        declaredType
      }
    }
  }

  override def toDeclaration(obj: ModelProperty) = {
    var declaredType = toDeclaredType(obj.`type`)

    declaredType match {
      case "Array" => declaredType = "list"
      case e: String => {
        e
      }
    }

    val defaultValue = toDefaultValue(declaredType, obj)
    declaredType match {
      case "list" => {
        val inner = {
          obj.items match {
            case Some(items) => items.ref.getOrElse(items.`type`)
            case _ => {
              println("failed on " + declaredType + ", " + obj)
              throw new Exception("no inner type defined")
            }
          }
        }
        declaredType += "[" + toDeclaredType(inner) + "]"
        "list"
      }
      case _ =>
    }
    (declaredType, defaultValue)
  }

  // escape keywords
  override def escapeReservedWord(word: String) = "`" + word + "`"
}
