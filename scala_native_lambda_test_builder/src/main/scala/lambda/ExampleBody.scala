package lambda

import io.circe.{Decoder, Encoder}

object ExampleBody {

  import io.circe.generic.semiauto._

  implicit val exampleBodyEncoder: Encoder.AsObject[ExampleBody] = deriveEncoder[ExampleBody]
  implicit val exampleBodyDecoder: Decoder[ExampleBody] = deriveDecoder[ExampleBody]

  val encodedExampleBodyDecoder: Decoder[ExampleBody] =
    SqsDecoding.createEncodedBodyDecoder(ExampleBody.exampleBodyDecoder)

  // The message in the body is always encoded json which makes things fun
  val sqsEncodedMessageBodyStringDecoder: Decoder[SqsDecoding[ExampleBody]] =
    SqsDecoding.decoder(encodedExampleBodyDecoder)

}

case class ExampleBody(name: String)
