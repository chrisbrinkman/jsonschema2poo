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

package com.wordnik.swagger.model

import scala.collection.mutable.LinkedHashMap

trait AllowableValues
case object AnyAllowableValues extends AllowableValues
case class AllowableListValues (values: List[String] = List(), valueType: String = "LIST") extends AllowableValues
case class AllowableRangeValues(min: String, max: String) extends AllowableValues

case class Model(
  var id: String,
  var name: String,
  qualifiedType: String,
  var properties: LinkedHashMap[String, ModelProperty],
  description: Option[String] = None,
  baseModel: Option[String] = None,
  discriminator: Option[String] = None)

case class ModelProperty(
  var `type`: String,
  qualifiedType: String,
  position: Int = 0,
  required: Boolean = false,
  description: Option[String] = None,
  allowableValues: AllowableValues = AnyAllowableValues,
  var items: Option[ModelRef] = None)

case class ModelRef(
  `type`: String,
  ref: Option[String] = None,
  qualifiedType: Option[String] = None)
