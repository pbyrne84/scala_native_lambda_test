package com.github.pbyrne84
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._

case class GraalResourceConfigParsingError(message: String, cause: Throwable) extends RuntimeException(message, cause)

object GraalResourceConfig {

  implicit val graalReflectionConfigEncoder: Encoder.AsObject[GraalResourceConfig] =
    deriveEncoder[GraalResourceConfig]

  implicit val graalReflectionConfigDecoder: Decoder[GraalResourceConfig] =
    deriveDecoder[GraalResourceConfig]

  implicit val graalReflectionResourcesEncoder: Encoder.AsObject[Resources] =
    deriveEncoder[Resources]

  implicit val graalReflectionResourcesDecoder: Decoder[Resources] =
    deriveDecoder[Resources]

  implicit val graalReflectionIncludeEncoder: Encoder.AsObject[Include] =
    deriveEncoder[Include]

  implicit val graalReflectionIncludeDecoder: Decoder[Include] =
    deriveDecoder[Include]

  implicit val graalReflectionBundleEncoder: Encoder.AsObject[Bundle] =
    deriveEncoder[Bundle]

  implicit val graalReflectionBundleDecoder: Decoder[Bundle] =
    deriveDecoder[Bundle]

  def parse(json: String): Either[Throwable, GraalResourceConfig] = {
    io.circe.parser
      .decode[GraalResourceConfig](json)
      .left
      .map(error => GraalResourceConfigParsingError(s"The json $json is invalid", error))

  }

}

case class GraalResourceConfig(resources: Resources, bundles: List[Bundle]) {
  def filter(includeRegexes: List[String], bundleRegexes: List[String]): GraalResourceConfig = {
    val currentIncludes = resources.includes

    val filteredIncludes: List[Include] = currentIncludes.filterNot(
      currentInclude =>
        includeRegexes.exists(
          regex =>
            currentInclude.pattern
              .matches(regex)
      )
    )

    val filteredBundles: List[Bundle] = bundles.filterNot(
      currentBundle =>
        bundleRegexes.exists(
          regex =>
            currentBundle.name
              .matches(regex)
      )
    )

    GraalResourceConfig(resources = Resources(filteredIncludes), bundles = filteredBundles)
  }

  def writeJson: Json = GraalResourceConfig.graalReflectionConfigEncoder(this)

}

case class Resources(includes: List[Include])

case class Include(pattern: String)

case class Bundle(name: String)
