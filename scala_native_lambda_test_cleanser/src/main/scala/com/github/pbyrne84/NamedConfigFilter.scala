package com.github.pbyrne84

import io.circe.Decoder.Result
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

object NamedConfigElement {
  implicit val namedConfigElementEncoder: Encoder[NamedConfigElement] = new Encoder[NamedConfigElement] {
    override def apply(a: NamedConfigElement): Json = a.originalJson
  }

  implicit val namedConfigElementDecoder: Decoder[NamedConfigElement] = new Decoder[NamedConfigElement] {
    override def apply(c: HCursor): Result[NamedConfigElement] = {
      for {
        name <- c.downField("name").as[String]
      } yield NamedConfigElement(name, c.value)
    }
  }
}

case class NamedConfigElement(name: String, originalJson: Json)

object SerializeConfig {

  implicit val serializationEncoder: Decoder[SerializeConfig] = deriveDecoder[SerializeConfig]
  implicit val serializationDecoder: Encoder[SerializeConfig] = deriveEncoder[SerializeConfig]
}

case class SerializeConfig(types: List[NamedConfigElement],
                           lambdaCapturingTypes: List[NamedConfigElement],
                           proxies: List[NamedConfigElement])

object NamedConfigFilter {

  def filterOutReflectConfigs(jsonContents: String, filterRegex: List[String]): Either[Throwable, Json] = {
    for {
      converted <- io.circe.parser.decode[List[NamedConfigElement]](jsonContents)
      filtered = filterOutEntries(converted, filterRegex)
    } yield filtered.asJson
  }

  private def filterOutEntries(entries: List[NamedConfigElement],
                               filterRegex: List[String]): List[NamedConfigElement] = {
    entries.filterNot(entry => filterRegex.exists(regex => entry.name.matches(regex)))
  }

  // as of 22.3 graal changed formats. It was like reflect. Graal can move around a bit.
  def filterOutSerializeConfigs(jsonContents: String, filterRegex: List[String]): Either[Throwable, Json] = {
    for {
      converted <- io.circe.parser.decode[SerializeConfig](jsonContents)

      filteredTypes = filterOutEntries(converted.types, filterRegex)
      filteredLambdaCapturingTypes = filterOutEntries(converted.lambdaCapturingTypes, filterRegex)
      filteredProxies = filterOutEntries(converted.proxies, filterRegex)
    } yield SerializeConfig(filteredTypes, filteredLambdaCapturingTypes, filteredProxies).asJson
  }
}
