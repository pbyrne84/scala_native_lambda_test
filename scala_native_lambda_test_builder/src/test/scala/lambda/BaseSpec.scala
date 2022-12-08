package lambda

import io.circe.Json
import org.scalactic.Prettifier
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class BaseSpec extends AnyWordSpec with Matchers {

  // copied from
  // https://github.com/pbyrne84/scala-case-class-prettification/blob/master/modules/scala-case-class-prettification-test/src/main/scala/com/bintray/scala/prettification/scalatest/Prettifiers.scala
  // makes diffs human
  val caseClassPrettifier: CaseClassPrettifier = new CaseClassPrettifier

  implicit val prettifier: Prettifier = Prettifier.apply {
    case a: AnyRef if CaseClassPrettifier.shouldBeUsedInTestMatching(a) =>
      caseClassPrettifier.prettify(a)

    case a: Any => Prettifier.default(a)
  }

  def parseJson(json: String): Json = {
    io.circe.parser.parse(json) match {
      case Left(error) => fail(s"parsing $json failed with ${error.message}")
      case Right(parsedJson) => parsedJson
    }
  }
}
