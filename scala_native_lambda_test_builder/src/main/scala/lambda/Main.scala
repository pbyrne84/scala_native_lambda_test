package lambda

import org.slf4j.{Logger, LoggerFactory}

import java.io.PrintStream

/* example sqs message
{
    "Records": [
        {
            "messageId": "a78cdb8b-d8cb-4c28-be77-fc89608f022c",
            "receiptHandle": "AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==",
            "body": "I, am, the, one, and, only",
            "attributes": {
                "ApproximateReceiveCount": "1",
                "SentTimestamp": "1670426083016",
                "SenderId": "538645939706",
                "ApproximateFirstReceiveTimestamp": "1670426083021"
            },
            "messageAttributes": {},
            "md5OfBody": "a390ff989d692670fa09d8d64b134179",
            "eventSource": "aws:sqs",
            "eventSourceARN": "arn:aws:sqs:eu-west-2:538645939706:test-queue",
            "awsRegion": "eu-west-2"
        }
    ]
}
 */

final case class NoMessagePassedException() extends RuntimeException("no message was passed at first position")

object Main {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getSimpleName)

  def main(args: Array[String]): Unit = {
    logger.debug(s"mooo")
    logger.info(s"I haz cheezeburgers ${args.mkString(", ")}")
    logger.error("test error", new RuntimeException("a", new RuntimeException("b")))

    val message = args.mkString(", ").trim
    if (message.nonEmpty) {
      logger.info(s"processing message $message")
      SqsOperation.processMessage(message) match {
        case Left(error) =>
          logger.error(s"failed processing message $message", error)

          throw error

        case Right(_) =>
          logger.info(s"processed message $message")
      }
    } else {
      logger.error("No message passed")
      throw NoMessagePassedException()
    }

  }
}
