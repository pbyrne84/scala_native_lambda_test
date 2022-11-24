package com.github.pbyrne84

import io.circe.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source

abstract class BaseSpec extends AnyWordSpec with Matchers {

  protected def readFile(file: String): Either[Throwable, String] =
    scala.util
      .Using(Source.fromFile(new File(file))) { resource =>
        resource.getLines.toList.mkString
      }
      .toEither

  protected def parseJson(json: String): Json = {
    io.circe.parser.parse(json) match {
      case Left(error) => throw new RuntimeException(s"Cannot parse expected json $json", error)
      case Right(parsedJson) => parsedJson
    }
  }
}
