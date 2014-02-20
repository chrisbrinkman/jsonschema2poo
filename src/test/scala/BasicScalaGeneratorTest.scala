/**
 * Copyright 2013 Wordnik, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.wordnik.swagger.model._
import com.wordnik.swagger.codegen.BasicScalaGenerator

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers


@RunWith(classOf[JUnitRunner])
class BasicScalaGeneratorTest extends FlatSpec with ShouldMatchers {
  val config = new BasicScalaGenerator

  behavior of "BasicScalaGenerator"

  /*
   * returns the model package
   */
  it should "get the model package" in {
    config.modelPackage should be(Some("com.wordnik.client.model"))
  }

  /*
   * types are mapped between swagger types and language-specific
   * types
   */
  it should "convert to a declared type" in {
    config.toDeclaredType("boolean") should be("Boolean")
    config.toDeclaredType("string") should be("String")
    config.toDeclaredType("int") should be("Int")
    config.toDeclaredType("float") should be("Float")
    config.toDeclaredType("long") should be("Long")
    config.toDeclaredType("double") should be("Double")
    config.toDeclaredType("object") should be("Any")
  }

  /*
   * declarations are used in models, and types need to be
   * mapped appropriately
   */
  it should "convert a string a declaration" in {
    val expected = Map("string" ->("String", "_"),
      "int" ->("Int", "0"),
      "float" ->("Float", "0f"),
      "long" ->("Long", "0L"),
      "double" ->("Double", "0.0"),
      "object" ->("Any", "_"))
    expected.map(e => {
      val model = ModelProperty(e._1, "nothing")
      config.toDeclaration(model) should be(e._2)
    })
  }

  /*
   * codegen should honor special imports to avoid generating
   * classes
   */
  it should "honor the import mapping" in {
    config.importMapping("Date") should be("java.util.Date")
  }

  /*
   * single tick reserved words
   */
  it should "quote a reserved var name" in {
    config.toVarName("package") should be("`package`")
  }

  /*
   * support list declarations with string inner value and the correct default value
   */
  it should "create a declaration with a List of strings" in {
    val property = ModelProperty(
      `type` = "Array",
      qualifiedType = "nothing",
      items = Some(ModelRef(`type` = "string")))
    val m = config.toDeclaration(property)
    m._1 should be("List[String]")
    m._2 should be("_")
  }

  /*
   * support list declarations with int inner value and the correct default value
   */
  it should "create a declaration with a List of ints" in {
    val property = ModelProperty(
      `type` = "Array",
      qualifiedType = "nothing",
      items = Some(ModelRef(`type` = "int")))
    val m = config.toDeclaration(property)
    m._1 should be("List[Int]")
    m._2 should be("0")
  }

  /*
   * support list declarations with float inner value and the correct default value
   */
  it should "create a declaration with a List of floats" in {
    val property = ModelProperty(
      `type` = "Array",
      qualifiedType = "nothing",
      items = Some(ModelRef(`type` = "float")))
    val m = config.toDeclaration(property)
    m._1 should be("List[Float]")
    m._2 should be("0f")
  }

  /*
   * support list declarations with double inner value and the correct default value
   */
  it should "create a declaration with a List of doubles" in {
    val property = ModelProperty(
      `type` = "Array",
      qualifiedType = "nothing",
      items = Some(ModelRef(`type` = "double")))
    val m = config.toDeclaration(property)
    m._1 should be("List[Double]")
    m._2 should be("0.0")
  }

  /*
   * support list declarations with complex inner value and the correct default value
   */
  it should "create a declaration with a List of complex objects" in {
    val property = ModelProperty(
      `type` = "Array",
      qualifiedType = "Array",
      items = Some(ModelRef(`type` = "User")))
    val m = config.toDeclaration(property)
    m._1 should be("List[User]")
    m._2 should be("_")
  }
}
