package lambda

import io.circe.Decoder
import org.slf4j.{Logger, LoggerFactory}

import java.util
object SqsOperation {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getSimpleName)

  def processMessage(message: String): Either[Throwable, true] = {
    println(message)

    (for {
      result <- SqsDecoding.decodeMany(message)(Decoder.decodeString)
    } yield {
      result
    }).left
      .map { error =>
        logger.error(s"failed decoding message $message", error)
        error
      }
      .map { success =>
        logger.info(s"decoded $success")
        true
      }
  }
}
