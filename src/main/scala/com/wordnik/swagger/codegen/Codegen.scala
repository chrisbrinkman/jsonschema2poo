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
import com.wordnik.swagger.codegen.language.CodegenConfig

import org.json4s.jackson.Serialization.write

import org.fusesource.scalate._

import java.io.{ File, InputStream }

import io.Source
import collection.mutable.{ HashMap, ListBuffer, HashSet }
import collection.JavaConversions._

object Codegen {
  val templates = new HashMap[String, (String, (TemplateEngine, Template))]
}

class Codegen(config: CodegenConfig) {
  implicit val formats = SwaggerSerializers.formats
  val primitives = List("int", "string", "long", "double", "float", "boolean", "void")
  val containers = List("List", "Map", "Set", "Array")

  def generateSource(bundle: Map[String, AnyRef], templateFile: String): String = {
    val allImports = new HashSet[String]
    val includedModels = new HashSet[String]
    val modelList = new ListBuffer[Map[String, AnyRef]]
    val models = bundle("models")

    models match {
      case e: List[Tuple2[String, Model]] => {
        e.foreach(m => {
          includedModels += m._1
          val modelMap = modelToMap(m._1, m._2)
          modelMap.getOrElse("imports", None) match {
            case im: Set[Map[String, String]] => im.foreach(m => m.map(e => allImports += e._2))
            case None =>
          }
          modelList += modelMap
        })
      }
      case None =>
    }

    val modelData = Map[String, AnyRef]("model" -> modelList.toList)

    val f = new ListBuffer[AnyRef]

    val imports = new ListBuffer[Map[String, String]]
    val importScope = config.modelPackage match {
      case Some(s) => s + "."
      case None => ""
    }
    // do the mapping before removing primitives!
    allImports.foreach(i => {
      val model = config.toModelName(i)
      includedModels.contains(model) match {
        case false => {
          config.importMapping.containsKey(model) match {
            case true => {
              if(!imports.flatten.map(m => m._2).toSet.contains(config.importMapping(model))) {
                imports += Map("import" -> config.importMapping(model))
              }
            }
            case false =>
          }
        }
        case true =>
      }
    })

    allImports --= config.defaultIncludes
    allImports --= primitives
    allImports --= containers
    allImports.foreach(i => {
      val model = config.toModelName(i)
      includedModels.contains(model) match {
        case false => {
          config.importMapping.containsKey(model) match {
            case true =>
            case false => {
              if(!imports.flatten.map(m => m._2).toSet.contains(importScope + model)){
                imports += Map("import" -> (importScope + model))
              }
            }
          }
        }
        case true => // no need to add the model
      }
    })

    val rootDir = new java.io.File(".")
    val (resourcePath, (engine, template)) = Codegen.templates.getOrElseUpdate(templateFile, compileTemplate(templateFile, Some(rootDir)))

    val requiredModels = {
      for(i <- allImports) yield {
        HashMap("name" -> i, "hasMore" -> "true")
      }
    }.toList

    requiredModels.size match {
      case i if (i > 0) => requiredModels.last += "hasMore" -> "false"
      case _ =>
    }

    val data = Map[String, AnyRef](
      "name" -> bundle("name"),
      "package" -> bundle("package"),
      "baseName" -> bundle.getOrElse("baseName", None),
      "className" -> bundle("className"),
      "imports" -> imports,
      "requiredModels" -> requiredModels,
      "models" -> modelData,
      "basePath" -> bundle.getOrElse("basePath", ""))
    val output = engine.layout(resourcePath, template, data.toMap)

    //  a shutdown method will be added to scalate in an upcoming release
    engine.compiler.shutdown
    output
  }


  protected def compileTemplate(templateFile: String, rootDir: Option[File] = None, engine: Option[TemplateEngine] = None): (String, (TemplateEngine, Template)) = {
    val engine = new TemplateEngine(rootDir orElse Some(new File(".")))
    val srcName = config.templateDir + "/" + templateFile
    val srcStream = {
      getClass.getClassLoader.getResourceAsStream(srcName) match {
        case is: java.io.InputStream => is
        case _ => {
          val f = new java.io.File(srcName)
          if (!f.exists) throw new Exception("Missing template: " + srcName)
          else new java.io.FileInputStream(f)
        }
      }
    }
    val template = engine.compile(
      TemplateSource.fromText(config.templateDir + File.separator + templateFile,
        Source.fromInputStream(srcStream).mkString))
    (srcName, engine -> template)
  }

  def rawAllowableValuesToString(v: AllowableValues) = {
    v match {
      case av: AllowableListValues => {
        av
      }
      case av: AllowableRangeValues => {
        av
      }
      case _ => None
    }
  }


  def allowableValuesToString(v: AllowableValues) = {
    v match {
      case av: AllowableListValues => {
        Some(av.values.mkString("LIST[", ",", "]"))
      }
      case av: AllowableRangeValues => {
        Some("RANGE[" + av.min + "," + av.max + "]")
      }
      case _ => None
    }
  }

  def modelToMap(className: String, model: Model): Map[String, AnyRef] = {
    val data: HashMap[String, AnyRef] =
      HashMap(
        "classname" -> config.toModelName(className),
        "classVarName" -> config.toVarName(className), // suggested name of object created from this class
        "modelPackage" -> config.modelPackage,
        "description" -> model.description,
        "newline" -> "\n")

    val l = new ListBuffer[AnyRef]

    val imports = new HashSet[AnyRef]
    model.properties.map(prop => {
      val propertyDocSchema = prop._2
      val dt = propertyDocSchema.`type`

      var baseType = dt
      // import the object inside the container
      if (propertyDocSchema.items != null) {
        // import the container
        imports += Map("import" -> dt)
        propertyDocSchema.items match {
          case Some(items) => baseType = items.ref.getOrElse(items.`type`)
          case _ =>
        }
      }
      baseType = config.typeMapping.contains(baseType) match {
        case true => config.typeMapping(baseType)
        case false => {
          // imports += Map("import" -> config.toDeclaredType(baseType))
          baseType
        }
      }
      (config.defaultIncludes ++ config.languageSpecificPrimitives).toSet.contains(baseType) match {
        case true =>
        case _ => {
          imports += Map("import" -> baseType)
        }
      }

      val isList = if (isListType(propertyDocSchema.`type`)) true else None
      val isMap = if (isMapType(propertyDocSchema.`type`)) true else None
      val isNotContainer = if (!isListType(propertyDocSchema.`type`) && !isMapType(propertyDocSchema.`type`)) true else None
      val isContainer = if (isListType(propertyDocSchema.`type`) || isMapType(propertyDocSchema.`type`)) true else None

      val properties =
        HashMap(
          "name" -> config.toVarName(prop._1),
          "nameSingular" -> {
            val name = config.toVarName(prop._1)
            if (name.endsWith("s") && name.length > 1) name.substring(0, name.length - 1) else name
          },
          "baseType" -> {
            if (primitives.contains(baseType))
              baseType
            else
              config.modelPackage match {
                case Some(p) => p + "." + baseType
                case _ => baseType
              }
          },
          "baseTypeVarName" -> config.toVarName(baseType),
          "baseName" -> prop._1,
          "datatype" -> config.toDeclaration(propertyDocSchema)._1,
          "defaultValue" -> config.toDeclaration(propertyDocSchema)._2,
          "description" -> propertyDocSchema.description,
          "notes" -> propertyDocSchema.description,
          "allowableValues" -> rawAllowableValuesToString(propertyDocSchema.allowableValues),        
          (if(propertyDocSchema.required) "required" else "isNotRequired") -> "true",
          "getter" -> config.toGetter(prop._1, config.toDeclaration(propertyDocSchema)._1),
          "setter" -> config.toSetter(prop._1, config.toDeclaration(propertyDocSchema)._1),
          "isList" -> isList,
          "isMap" -> isMap,
          "isContainer" -> isContainer,
          "isNotContainer" -> isNotContainer,
          "hasMore" -> "true")
      (config.languageSpecificPrimitives.contains(baseType) || primitives.contains(baseType)) match {
        case true => properties += "isPrimitiveType" -> "true"
        case _ => properties += "complexType" -> config.toModelName(baseType)
      }
      l += properties
    })
    if(l.size > 0) {
      val last = l.last.asInstanceOf[HashMap[String, String]]
      last.remove("hasMore")
    }
    data += "vars" -> l
    data += "imports" -> imports.toSet
    config.processModelMap(data.toMap)
  }

  /**
   * gets an input stream from resource or file
   */
  def getInputStream(path: String): InputStream = {
    getClass.getClassLoader.getResourceAsStream(path) match {
      case is: InputStream => is
      case _ => new java.io.FileInputStream(path)
    }
  }

  def writeJson(m: AnyRef): String = {
    Option(System.getProperty("modelFormat")) match {
      case _ => write(m)
    }
  }

  protected def isListType(dt: String) = isCollectionType(dt, "List") || isCollectionType(dt, "Array") || isCollectionType(dt, "Set")

  protected def isMapType(dt: String) = isCollectionType(dt, "Map")

  protected def isCollectionType(dt: String, str: String) = {
    if (dt.equals(str))
      true
    else
      dt.indexOf("[") match {
        case -1 => false
        case n: Int => {
          if (dt.substring(0, n) == str) {
            true
          } else false
        }
      }
  }
}
