package lambda

import org.slf4j.{Logger, LoggerFactory}

object Main {

  private val logger: Logger = LoggerFactory.getLogger("Main")

  def main(args: Array[String]): Unit = {
    logger.info("I haz cheezeburgers")

    println("cheeze burgers")
  }
}
