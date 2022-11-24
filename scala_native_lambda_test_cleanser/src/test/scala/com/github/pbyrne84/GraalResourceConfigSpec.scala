package com.github.pbyrne84

class GraalResourceConfigSpec extends BaseSpec {

  private val validJsonText =
    """
      |{
      |  "resources": {
      |    "includes": [
      |      {
      |        "pattern": "\\Qlibrary.properties\\E"
      |      },
      |      {
      |        "pattern": "\\Qlog4j.properties\\E"
      |      },
      |      {
      |        "pattern": "\\Qorg/slf4j/impl/StaticLoggerBinder.class\\E"
      |      }
      |    ]
      |  },
      |  "bundles": [
      |    {
      |      "name": "org.scalatest.ScalaTestBundle"
      |    },
      |    {
      |      "name": "org.me.OtherBundle"
      |    },
      |    {
      |      "name": "org.me.AnotherOtherBundle"
      |    }
      |  ]
      |}
      |""".stripMargin.trim

  private val validConfig = GraalResourceConfig(
    resources = Resources(
      includes = List(
        Include("\\Qlibrary.properties\\E"),
        Include("\\Qlog4j.properties\\E"),
        Include("\\Qorg/slf4j/impl/StaticLoggerBinder.class\\E")
      ),
    ),
    bundles = List(
      Bundle("org.scalatest.ScalaTestBundle"),
      Bundle("org.me.OtherBundle"),
      Bundle("org.me.AnotherOtherBundle")
    )
  )

  "parsing" should {
    "parse successfully" in {

      GraalResourceConfig.parse(validJsonText) shouldBe Right(validConfig)
    }

    "error with original json so we can debug on failure" in {
      val invalidJsonText =
        """
          |{
          |  "resourcessss": {
          |    "includes": [
          |      {
          |        "pattern": "\\Qlibrary.properties\\E"
          |      },
          |      {
          |        "pattern": "\\Qlog4j.properties\\E"
          |      },
          |      {
          |        "pattern": "\\Qorg/slf4j/impl/StaticLoggerBinder.class\\E"
          |      }
          |    ]
          |  },
          |  "bundles": [
          |    {
          |      "name": "org.scalatest.ScalaTestBundle"
          |    }
          |  ]
          |}
          |""".stripMargin.trim

      GraalResourceConfig.parse(invalidJsonText).left.map(_.getMessage) match {
        case Left(errorMessage) => errorMessage should include(invalidJsonText)
        case Right(graalReflectionConfig) => fail(s"expected error got $graalReflectionConfig")
      }
    }
  }


  "filtering" should {
    "remove things via regex and rewrite json" in {
      val expectedJsonText =
        """
          |{
          |  "resources": {
          |    "includes": [
          |      {
          |        "pattern": "\\Qlibrary.properties\\E"
          |      }
          |    ]
          |  },
          |  "bundles": [
          |    {
          |      "name": "org.me.OtherBundle"
          |    }
          |
          |  ]
          |}
          |""".stripMargin.trim

      val expectedJsonObject = parseJson(expectedJsonText)

      validConfig
        .filter(
          includeRegexes = List(".*log4j.properties.*", ".*StaticLoggerBinder.*"),
          bundleRegexes = List(".*AnotherOtherBundle.*", ".*ScalaTestBundle.*")
        )
        .writeJson shouldBe expectedJsonObject

    }
  }

}
