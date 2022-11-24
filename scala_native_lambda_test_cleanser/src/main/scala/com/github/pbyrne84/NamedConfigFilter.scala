package com.github.pbyrne84

import io.circe.Decoder.Result
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}

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

object NamedConfigFilter {

  def filterOutConfigs(jsonContents: String, filterRegex: List[String]): Either[Throwable, Json] = {
    for {
      converted <- io.circe.parser.decode[List[NamedConfigElement]](jsonContents)
      filtered = filterOutEntries(converted, filterRegex)
    } yield filtered.asJson
  }

  private def filterOutEntries(entries: List[NamedConfigElement],
                               filterRegex: List[String]): List[NamedConfigElement] = {
    entries.filterNot(entry => filterRegex.exists(regex => entry.name.matches(regex)))
  }
}
