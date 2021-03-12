import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.io.Source
import scala.util.{Try, Using}

object ResourceBundle {
  implicit val resourceBundleEncoder: Encoder.AsObject[ResourceBundle] = deriveEncoder[ResourceBundle]
  implicit val resourceBundleDecoder: Decoder[ResourceBundle] = deriveDecoder[ResourceBundle]
}

case class ResourceBundle(pattern: String)

object ResourceConfig {
  implicit val resourceConfigEncoder: Encoder.AsObject[ResourceConfig] = deriveEncoder[ResourceConfig]
  implicit val resourceConfigDecoder: Decoder[ResourceConfig] = deriveDecoder[ResourceConfig]
}

case class ResourceConfig(resources: List[ResourceBundle])

object CleanNativeImageConf {
  import io.circe.syntax._

  def cleanResourceBundles(invalidBundleRegexPatterns: List[String]): Either[Throwable, Boolean] = {
    val fileName = "src/main/resources/META-INF/native-image/resource-config.json"

    for {
      content <- readFile(fileName)
      resourceConfig <- io.circe.parser.decode[ResourceConfig](content)
      filteredJson = filterValuesFromResourceConfig(resourceConfig, invalidBundleRegexPatterns)
      _ <- writeFile(fileName, filteredJson)
    } yield true
  }

  private def readFile(fileName: String): Either[Throwable, String] = {
    Using(Source.fromFile(fileName)) { resource =>
      resource.getLines.mkString
    }.toEither
  }

  private def filterValuesFromResourceConfig(resourceConfig: ResourceConfig,
                                             invalidBundlePatterns: List[String]): Json = {
    resourceConfig
      .copy(
        resources = resourceConfig.resources.filter(
          resourceBundle => !invalidBundlePatterns.exists(regexPattern => resourceBundle.pattern.matches(regexPattern))
        )
      )
      .asJson
  }

  private def writeFile(fileName: String, contents: Json): Either[Throwable, Boolean] = {
    Try {
      reflect.io.File(fileName).writeAll(contents.spaces2)
    }.toEither.map(_ => true)
  }

  def cleanReflectConfig(invalidRegexPatterns: List[String]): Either[Throwable, Boolean] = {
    val fileName = "src/main/resources/META-INF/native-image/reflect-config.json"

    for {
      content <- readFile(fileName)
      json <- io.circe.parser.parse(content)
      filteredEntries <- filterValuesFromReflectConfigJson(json, invalidRegexPatterns)
      _ <- writeFile(fileName, Json.fromValues(filteredEntries))
    } yield true
  }

  private def filterValuesFromReflectConfigJson(json: Json, invalidRegexPatterns: List[String]) = {
    def filter(json: Json) = {
      def compare(nameJson: Json): Boolean = {
        nameJson.as[String].map { name =>
          invalidRegexPatterns.exists(pattern => name.matches(pattern))
        } match {
          case Left(cause) => throw new RuntimeException(s"name json $nameJson could not convert to string", cause)
          case Right(matches) => matches
        }
      }

      json.asObject
        .flatMap { jsonObject =>
          jsonObject("name").map(nameJson => compare(nameJson))
        }
        .getOrElse {
          println(s"could not find the key 'name'  in ${json.spaces2}")
          false
        }
    }

    for {
      listOfJson <- json.as[List[Json]]
      filteredJson = listOfJson.filterNot(filter)
    } yield filteredJson

  }

}
