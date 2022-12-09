package lambda

import io.circe.{Decoder, Json}

import java.util.UUID

class SqsDecodingSpec extends BaseSpec {

  private val receiptHandle =
    """AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RIN=="""

  private val messageId = UUID.fromString("a78cdb8b-d8cb-4c28-be77-fc89608f022c")

  "SqsDecoding" can {
    "decode a single message" should {
      "when the message body is a simply string" in {
        val bodyString = "I, am, the, one, and, only"
        val json = createMessageJson(s"\"$bodyString\"")

        implicit val sqsMessageStrongDecoder: Decoder[SqsDecoding[String]] = SqsDecoding.decoder(Decoder.decodeString)

        io.circe.parser.decode[SqsDecoding[String]](json.spaces2) shouldBe Right(
          createSqsDecoding(bodyString)
        )
      }

      "when the message body is encoded json of an object that can be a case class" in {
        val exampleBody = ExampleBody(name = "Unknown")
        // playing the add the backslashes game is always fun
        val encodedBody = "\"{\\n  \\\"name\\\" : \\\"Unknown\\\"\\n}\""

        val jsonWithEncodedBody = createMessageJson(encodedBody)
        val expectedConversion = createSqsDecoding(exampleBody)

        implicit val sqsMessageStrongDecoder: Decoder[SqsDecoding[ExampleBody]] =
          ExampleBody.sqsEncodedMessageBodyStringDecoder

        io.circe.parser.decode[SqsDecoding[ExampleBody]](jsonWithEncodedBody.spaces2) shouldBe Right(expectedConversion)

      }
    }

    def createMessageJson(messageBody: String): Json = {
      // Make sure if structure is not valid fail here intelligently
      parseJson(
        s"""
           |{
           |    "messageId": "$messageId",
           |    "receiptHandle": "$receiptHandle",
           |    "body": $messageBody,
           |    "attributes": {
           |        "ApproximateReceiveCount": "1",
           |        "SentTimestamp": "1670426083016"
           |    },
           |    "messageAttributes": {},
           |    "md5OfBody": "a390ff989d692670fa09d8d64b134179",
           |    "eventSource": "aws:sqs",
           |    "eventSourceARN": "arn:aws:sqs:eu-west-2:538645939706:test-queue",
           |    "awsRegion": "eu-west-2"
           |}
           |""".stripMargin
      )
    }

    def createSqsDecoding[A](body: A): SqsDecoding[A] = {
      SqsDecoding[A](
        messageId = messageId,
        receiptHandle = receiptHandle,
        body = body,
        attributes = Map(
          "ApproximateReceiveCount" -> "1",
          "SentTimestamp" -> "1670426083016"
        ),
        messageAttributes = Map.empty,
        md5OfBody = "a390ff989d692670fa09d8d64b134179",
        eventSource = "aws:sqs",
        eventSourceARN = "arn:aws:sqs:eu-west-2:538645939706:test-queue",
        awsRegion = "eu-west-2"
      )
    }

    "decode many" should {
      "receiving a message from amazon" in {
        val body1String = "body1"
        val body1Json = createMessageJson(s"\"$body1String\"")

        val body2String = "body2"
        val body2Json = createMessageJson(s"\"$body2String\"")

        val amazonMessages =
          s"""
            |{
            |    "Records": [
            |       $body1Json,
            |       $body2Json
            |    ]
            |}
            |""".stripMargin

        println(amazonMessages)

        SqsDecoding.decodeMany(amazonMessages)(Decoder.decodeString) shouldBe Right(
          List(
            createSqsDecoding(body1String),
            createSqsDecoding(body2String)
          )
        )

      }
    }
  }

}
