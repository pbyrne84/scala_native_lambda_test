import com.github.pbyrne84.{GraalResourceConfig, NamedConfigElement, NamedConfigFilter}
import io.circe.Json
import io.circe.syntax.EncoderOps

import scala.io.Source
import scala.util.{Try, Using}
import org.apache.log4j.{ConsoleAppender, Level, Logger, PatternLayout}

import java.io.File

object CleanReflectionConfig {

  val console = new ConsoleAppender() //create appender

  //configure the appender
  val PATTERN = "%d [%p|%c|%C{1}] %m%n"
  console.setLayout(new PatternLayout(PATTERN))
  console.setThreshold(Level.INFO)
  console.activateOptions()

  //add appender to any Logger (here is root)
  Logger.getRootLogger.addAppender(console)

  private val logger = Logger.getRootLogger

  private val nativeImageConfigDir = "src/main/resources/META-INF/native-image/"
  private val resourceConfigFileLocation = nativeImageConfigDir + "resource-config.json"
  private val reflectionConfigFileLocation = nativeImageConfigDir + "reflect-config.json"
  private val serializationConfigFileLocation = nativeImageConfigDir + "serialization-config.json"

  def filterAndRewrite(includeRegexes: List[String],
                       bundleRegexes: List[String],
                       nameRegexes: List[String]): Either[Throwable, Boolean] = {

    for {
      _ <- listFiles(nativeImageConfigDir)
      _ <- updateResourceConfig(includeRegexes, bundleRegexes)
      _ <- updatedNamedConfigs(nameRegexes)

    } yield true
  }

  private def listFiles(directoryPath: String): Either[Throwable, Unit] = {
    Try {
      logger.info(s"Listing config files in $directoryPath")

      val directory = new File(directoryPath)
      directory.list().foreach { file =>
        logger.info("Config file - " + file)

        val configLocation = new File(directoryPath + file)
        if (configLocation.isFile) {
          Using(Source.fromFile(configLocation)) { openFile =>
            val contents = openFile.getLines.toList.mkString
            logger.info("Contents :" + contents)
          }.toEither.left.map(error => throw error)

          logger.info("")
        } else {
          logger.info(s"Skipping directory ${configLocation.getAbsoluteFile}")
        }

      }
    }.toEither

  }

  private def updateResourceConfig(includeRegexes: List[String], bundleRegexes: List[String]) = {
    for {
      json <- readFile(resourceConfigFileLocation)
      _ = logger.info(s"read reflection config:\n************************\n$json\n************************\n")
      updatedResourceConfigJson <- GraalResourceConfig.parse(json)
      _ <- writeFile(resourceConfigFileLocation,
                     updatedResourceConfigJson.filter(includeRegexes, bundleRegexes).writeJson)
    } yield true

  }

  private def readFile(fileName: String): Either[Throwable, String] = {
    Using(Source.fromFile(fileName)) { resource =>
      resource.getLines.mkString
    }.toEither
  }

  private def writeFile(fileName: String, contents: Json): Either[Throwable, Boolean] = {
    Try {
      // nice to have spaces, we are human after all. Though cyborg body with hacking capability would be better
      logger.info(
        s"writing cleaned $fileName reflection config:\n************************\n${contents.spaces2}\n************************\n"
      )
      reflect.io.File(fileName).writeAll(contents.spaces2)
    }.toEither.map(_ => true)
  }

  private def updatedNamedConfigs(nameRegexes: List[String]): Either[Throwable, Boolean] = {
    for {
      _ <- processReflectionConfig(reflectionConfigFileLocation, nameRegexes)
      _ <- processSerializationConfig(serializationConfigFileLocation, nameRegexes)
    } yield true
  }

  private def processReflectionConfig(filePath: String, nameRegexes: List[String]): Either[Throwable, Boolean] = {
    cleanJsonFile(filePath, nameRegexes, NamedConfigFilter.filterOutReflectConfigs)
  }

  private def cleanJsonFile(filePath: String,
                            nameRegexes: List[String],
                            call: (String, List[String]) => Either[Throwable, Json]) = {
    for {
      configJson <- readFile(filePath)
      filteredJson <- call(configJson, nameRegexes)
      _ <- writeFile(filePath, filteredJson.asJson)
    } yield true
  }

  private def processSerializationConfig(filePath: String, nameRegexes: List[String]): Either[Throwable, Boolean] = {
    cleanJsonFile(filePath, nameRegexes, NamedConfigFilter.filterOutSerializeConfigs)
  }

}
