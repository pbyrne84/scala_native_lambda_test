package com.github.pbyrne84

import java.net.URL

class NamedConfigFilterSpec extends BaseSpec {

  "filterOutConfigs" should {
    "return the same contents for reflect config when no filters are passed" in {
      val reflectConfigContents = getResourceContents("reflect-config.json")
      val reflectConfigJson = parseJson(reflectConfigContents)

      NamedConfigFilter.filterOutConfigs(reflectConfigContents, List.empty) shouldBe Right(reflectConfigJson)
    }

    "return the same contents for serialization config when no filters are passed" in {
      val serializationConfigContents = getResourceContents("serialization-config.json")
      val serializationConfigJson = parseJson(serializationConfigContents)

      NamedConfigFilter.filterOutConfigs(serializationConfigContents, List.empty) shouldBe Right(
        serializationConfigJson
      )
    }

    "filter out entries matching a regex" in {
      val configJson = parseJson("""
          |[
          |  {
          |    "name": "java.io.ObjectStreamField"
          |  },
          |  {
          |    "name": "java.io.PrintStream"
          |  },
          |  {
          |    "name": "java.io.PrintWriter"
          |  },
          |  {
          |    "name": "java.io.Serializable",
          |    "allDeclaredFields": true,
          |    "allDeclaredMethods": true,
          |    "allPublicConstructors": true,
          |    "allDeclaredClasses": true
          |  },
          |  {
          |    "name": "java.io.UnsupportedEncodingException"
          |  },
          |  {
          |    "name": "java.lang.AbstractStringBuilder"
          |  }
          |]
          |""".stripMargin)

      val expected = parseJson("""
          |[
          |  {
          |    "name": "java.io.ObjectStreamField"
          |  },
          |  {
          |    "name": "java.io.Serializable",
          |    "allDeclaredFields": true,
          |    "allDeclaredMethods": true,
          |    "allPublicConstructors": true,
          |    "allDeclaredClasses": true
          |  },
          |  {
          |    "name": "java.io.UnsupportedEncodingException"
          |  }
          |]
          |""".stripMargin)

      NamedConfigFilter.filterOutConfigs(configJson.spaces2, List("java.io.P.*", "java.lang.*")) shouldBe Right(
        expected
      )

    }

  }

  private def getResourceContents(fileName: String): String = {
    Option(getClass.getClassLoader.getResource(fileName))
      .map((resource: URL) => readFile(resource.getFile)) match {
      case Some(errorOrContents) =>
        errorOrContents match {
          case Left(error) => throw error
          case Right(contents) => contents
        }
      case None => fail("resource not found: " + fileName)
    }

  }
}
