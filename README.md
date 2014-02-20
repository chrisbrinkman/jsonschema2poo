# JSON Schema -> Plain Old Objects (POO)

[![Build Status](https://travis-ci.org/skormos/jsonschema2poo.png?branch=master)](https://travis-ci.org/skormos/jsonschema2poo)

## Overview
A blatant and shameless fork of the Swagger Codegen project, which seemed to be the *only* one available that would read
JSON Schema files, and produce objects, not just of Scala (which was the initial requirement), but had the added benefit
of supporting multiple output languages. Aside from the initial commits (and maybe a few more immediate remidiation
commits), this project will probably languish, but will be kept abreast of any pull requests. Thanks to the Swagger Codegen
developers for a great implementation.

### Prerequisites
You need the following installed and available in your $PATH:

* [Java 1.7](http://java.oracle.com)
* [Apache maven 3.0.3 or greater](http://maven.apache.org/)
* [Scala 2.10.3](http://www.scala-lang.org)
* [sbt (only download if you're building on Windows)](http://www.scala-sbt.org/)

You also need to add the scala binary to your PATH.

After cloning the project, you need to build it from source with this command:

```
./sbt assembly
```

or for Windows...

```
sbt assembly
```


### To generate a sample model set

```
./bin/scala-petstore.sh
```

This will run the script in [samples/petstore/ScalaPetstoreCodegen.scala](https://github.com/skormos/javaschema2poo/blob/master/samples/petstore/scala/ScalaPetstoreCodegen.scala) and create the sample output.
You can then compile the objects in your code base.

Other languages have samples, too:
```
./bin/flash-petstore.sh
./bin/java-petstore.sh
./bin/objc-petstore.sh
./bin/php-petstore.sh
./bin/python-petstore.sh
./bin/python3-petstore.sh
./bin/ruby-petstore.sh
```

You will probably want to override some of the defaults--like packages, etc.  For doing this, just create a scala
script with the overrides you want.  Follow [ScalaPetstoreCodegen](https://github.com/skormos/javaschema2poo/blob/master/samples/petstore/scala/ScalaPetstoreCodegen.scala) as an example:

For example, create `src/main/scala/MyCodegen.scala` with these contents:

```scala
import com.wordnik.swagger.codegen.BasicScalaGenerator

object MyCodegen extends BasicScalaGenerator {
  def main(args: Array[String]) = generateClient(args)

  // location of templates
  override def templateDir = "scala"

  // where to write generated code
  override def destinationDir = "client/scala/src/main/scala"

  // api invoker package
  override def invokerPackage = "com.myapi.client"

  // package for models
  override def modelPackage = Some("com.myapi.client.model")

  // package for api classes
  override def apiPackage = Some("com.myapi.client.api")

  // supporting classes
  override def supportingFiles = List(
    ("apiInvoker.mustache", destinationDir + java.io.File.separator + packageName.replaceAll("\\.", java.io.File.separator), "ApiInvoker.scala"),
    ("pom.mustache", destinationDir, "pom.xml")
  )
}
```

Now you can generate your client like this:

```
./bin/runscala.sh src/main/scala/MyCodegen.scala path/to/json/schemas
```

w00t!  Thanks to the scala interpretor, you didn't even need to recompile.

### Modifying the client library format
Want a different language supported?  No problem!  Swagger codegen processes mustache templates with the [scalate](http://scalate.fusesource.org/)
engine.  You can modify our templates or make your own.

You can look at `src/main/resources/${your-language}` for examples.  To make your own templates, create your own files
and override the `templateDir` in your script to point to the right place.  It actually is that easy.

### To build the generator library

This will create the code generation jar from source.

```
./sbt assembly
```

Note!  The templates are included in the library generated.  If you want to modify the templates, you'll need to
either repackage the library OR modify your codegen script to use a file path!

License
-------

Copyright 2013 Wordnik, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at [apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
