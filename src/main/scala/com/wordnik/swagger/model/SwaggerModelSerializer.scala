package com.wordnik.swagger.model

import org.json4s._
import org.json4s.JsonDSL._

import scala.collection.mutable.{ListBuffer, LinkedHashMap}

object SwaggerSerializers {

  val jsonSchemaTypeMap = Map(
    // simple types
    ("integer", "int32") -> "int",
    ("integer", "int64") -> "long",
    ("number", "float") -> "float",
    ("number", "double") -> "double",
    ("string", "byte") -> "byte",
    ("string", "date") -> "Date",
    ("string", "date-time") -> "Date",

    // containers
    ("array", "") -> "Array",
    ("set", "") -> "Set"
  )

  def toJsonSchema(name: String, `type`: String): JObject = {
    `type` match {
      case "int"       => (name -> "integer") ~ ("format" -> "int32")
      case "long"      => (name -> "integer") ~ ("format" -> "int64")
      case "float"     => (name -> "number")  ~ ("format" -> "float")
      case "double"    => (name -> "number")  ~ ("format" -> "double")
      case "string"    => (name -> "string")  ~ ("format" -> JNothing)
      case "byte"      => (name -> "string")  ~ ("format" -> "byte")
      case "boolean"   => (name -> "boolean") ~ ("format" -> JNothing)
      case "Date"      => (name -> "string")  ~ ("format" -> "date-time")
      case "date"      => (name -> "string")  ~ ("format" -> "date")
      case "date-time" => (name -> "string")  ~ ("format" -> "date-time")
      case "Array"     => (name -> "array")
      case _           => {
        val ComplexTypeMatcher = "([a-zA-Z]*)\\[([a-zA-Z\\.\\-]*)\\].*".r
        `type` match {
          case ComplexTypeMatcher(container, value) => 
            toJsonSchemaContainer(container) ~ {
              ("items" -> {if(isSimpleType(value))
                  toJsonSchema("type", value)
                else
                  toJsonSchema("$ref", value)})
            }
          case _ => (name -> `type`)    ~ ("format" -> JNothing)
        }
      }
    }
  }

  def toJsonSchemaContainer(name: String): JObject = {
    name match {
      case "List"      => ("type" -> "array")   ~ ("format" -> JNothing)
      case "Array"     => ("type" -> "array")   ~ ("format" -> JNothing)
      case "Set"       => ("type" -> "array")   ~ ("uniqueItems" -> true)
      case _           => ("type" -> JNothing)
    }
  }

  def isSimpleType(name: String) = {
    Set("int", "long", "float", "double", "string", "byte", "boolean", "Date", "date", "date-time", "array", "set").contains(name)
  }

  def formats = {
        DefaultFormats + 
          new ModelSerializer + 
          new ModelPropertySerializer +
          new ModelRefSerializer + 
          new AllowableValuesSerializer
  }

  class ModelSerializer extends CustomSerializer[Model](implicit formats => ({
    case json =>
      val output = new LinkedHashMap[String, ModelProperty]
      val required = (json \ "required").extract[Set[String]]
      json \ "properties" match {
        case JObject(entries) => {
          entries.map({
            case (key, value) => {
              val prop = value.extract[ModelProperty]
              if(required.contains(key))
                output += key -> prop.copy(required = true)
              else
                output += key -> prop
            }
          })
        }
        case _ =>
      }

      Model(
        (json \ "id").extractOrElse("ID_NOT_FOUND"),
        (json \ "name").extractOrElse(""),
        (json \ "qualifiedType").extractOrElse((json \ "id").extractOrElse("")),
        output,
        (json \ "description").extractOpt[String]
      )
    }, {
    case x: Model =>
      val required: List[String] = (for((name, prop) <- x.properties) yield {
        if(prop.required) Some(name)
        else None
      }).flatten.toList

      ("id" -> x.id) ~
      ("name" -> x.name) ~
      ("required" -> (required.size match {
        case 0 => JNothing
        case _ => Extraction.decompose(required)
      })) ~
      ("properties" -> {
        (x.properties: @unchecked) match {
          case e: LinkedHashMap[String, ModelProperty] => Extraction.decompose(e.toMap)
          case _ => JNothing
        }
      })
    }
  ))

  class ModelPropertySerializer extends CustomSerializer[ModelProperty] (implicit formats => ({
    case json =>
      val `type` = (json \ "$ref") match {
        case e: JString => e.s
        case _ => {
          // convert the jsonschema types into swagger types.  Note, this logic will move elsewhere soon
          val t = SwaggerSerializers.jsonSchemaTypeMap.getOrElse(
            ((json \ "type").extractOrElse(""), (json \ "format").extractOrElse(""))
          , (json \ "type").extractOrElse(""))
          val isUnique = (json \ "uniqueItems") match {
            case e: JBool => e.value
            case e: JString => e.s.toBoolean
            case _ => false
          }
          if(t == "Array" && isUnique) "Set"
          else t
        }
      }

      val output = new ListBuffer[String]
      json \ "enum" match {
        case JArray(entries) => entries.map {
          case e: JInt => output += e.num.toString
          case e: JBool => output += e.value.toString
          case e: JString => output += e.s
          case e: JDouble => output += e.num.toString
          case _ =>
        }
        case _ =>
      }
      val allowableValues = {
        if(output.size > 0) AllowableListValues(output.toList)
        else {
          val min = (json \ "min") match {
            case e: JObject => e.extract[String]
            case _ => ""
          }
          val max = (json \ "max") match {
            case e: JObject => e.extract[String]
            case _ => ""
          }
          if(min != "" && max != "")
            AllowableRangeValues(min, max)
          else
            AnyAllowableValues
        }
      }
      ModelProperty(
        `type` = `type`,
        `qualifiedType` = (json \ "qualifiedType").extractOpt[String].getOrElse(`type`),
        required = (json \ "required") match {
          case e:JString => e.s.toBoolean
          case e:JBool => e.value
          case _ => false
        },
        description = (json \ "description").extractOpt[String],
        allowableValues = allowableValues,
        items = {
          (json \ "items").extractOpt[ModelRef] match {
            case Some(e: ModelRef) if(e.`type` != null || e.ref != None) => Some(e)
            case _ => None
          }
        }
      )
    }, {
    case x: ModelProperty =>
      val output = toJsonSchema("type", x.`type`) ~
      ("description" -> x.description) ~
      ("items" -> Extraction.decompose(x.items))

      x.allowableValues match {
        case AllowableListValues(values, "LIST") => 
          output ~ ("enum" -> Extraction.decompose(values))
        case AllowableRangeValues(min, max)  => 
          output ~ ("minimum" -> min) ~ ("maximum" -> max)
        case _ => output
      }
    }
  ))

  class ModelRefSerializer extends CustomSerializer[ModelRef](implicit formats => ({
    case json =>

      val `type` = (json \ "type") match {
        case e: JString => e.s
        case _ => ""
      }
      val format = (json \ "format") match {
        case e: JString => e.s
        case _ => ""
      }
      val jsonSchemaType = jsonSchemaTypeMap.getOrElse((`type`, format), `type`)

      ModelRef(
        jsonSchemaType match {
          case e: String if(e != "") => e
          case _ => null
        },
        (json \ "$ref").extractOpt[String]
      )
    }, {
      case x: ModelRef =>
      ("type" -> {
        x.`type` match {
          case e:String => Some(e)
          case _ => None
        }
      }) ~
      ("$ref" -> x.ref)
    }
  ))

  class AllowableValuesSerializer extends CustomSerializer[AllowableValues](implicit formats => ({
    case json =>
      json \ "valueType" match {
        case JString(x) if x.equalsIgnoreCase("list") => {
          val output = new ListBuffer[String]
          val properties = (json \ "values") match {
            case JArray(entries) => entries.map {
              case e:JInt => output += e.num.toString
              case e:JBool => output += e.value.toString
              case e:JString => output += e.s
              case e:JDouble => output += e.num.toString
              case _ =>
            }
            case _ =>
          }
          AllowableListValues(output.toList)
        }
        case JString(x) if x.equalsIgnoreCase("range") =>
          AllowableRangeValues((json \ "min").extract[String], (json \ "max").extract[String])
        case _ => AnyAllowableValues
      }
    }, {
      case AllowableListValues(values, "LIST") => 
        ("valueType" -> "LIST") ~ ("values" -> Extraction.decompose(values))
      case AllowableRangeValues(min, max)  => 
        ("valueType" -> "RANGE") ~ ("min" -> min) ~ ("max" -> max)
    }
  ))
}
