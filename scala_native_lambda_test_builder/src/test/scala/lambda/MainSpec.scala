package lambda

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, LocalStackContainer}
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.utility.DockerImageName

class MainSpec extends AnyWordSpec {

  // val localstackImage = DockerImageName.parse( "localstack/localstack:0.11.3" )

 // override def container: Container = new LocalStackContainer("0.12.12", List(Service.S3))

  "x" should {
    "y" in {
      Main.main(Array.empty[String])
    }
  }

}
