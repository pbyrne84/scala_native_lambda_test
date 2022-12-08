package lambda

import io.circe
import io.circe.Decoder.{decodeString, Result}
import io.circe.DecodingFailure.Reason.CustomReason
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, HCursor}

import java.util.UUID

object SqsDecoding {
  // aDecoder is used by SqsDecoding[A] but Lazy[DerivedDecoder[A]] hides the requirement without compilation
  def decoder[A](implicit aDecoder: Decoder[A]): Decoder[SqsDecoding[A]] = {
    deriveDecoder[SqsDecoding[A]]
  }

  def createEncodedBodyDecoder[A](implicit bodyDecoder: Decoder[A]): Decoder[A] = {
    new Decoder[A] {
      override def apply(c: HCursor): Result[A] =
        for {
          decodedBodyString <- decodeString(c)
          bodyObject <- io.circe.parser
            .decode[A](decodedBodyString)
            .left
            .map((error: circe.Error) => DecodingFailure(CustomReason(error.getMessage), c))

        } yield bodyObject
    }
  }

  def decodeMany[A](
      manyMessages: String
  )(implicit bodyDecoder: Decoder[A]): Either[circe.Error, List[SqsDecoding[A]]] = {
    implicit val sqsDecoder: Decoder[SqsDecoding[A]] = SqsDecoding.decoder[A]

    for {
      json <- io.circe.parser.parse(manyMessages)
      records <- json.hcursor.get[List[SqsDecoding[A]]]("Records")
    } yield records

  }
}

case class SqsDecoding[A](
    messageId: UUID,
    receiptHandle: String,
    body: A,
    attributes: Map[String, String],
    messageAttributes: Map[String, String],
    md5OfBody: String,
    eventSource: String,
    eventSourceARN: String,
    awsRegion: String
)
