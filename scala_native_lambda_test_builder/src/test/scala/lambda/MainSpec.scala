package lambda

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, LocalStackContainer}
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.utility.DockerImageName

class MainSpec extends AnyWordSpec {

  // val localstackImage = DockerImageName.parse( "localstack/localstack:0.11.3" )

//  override def container: Container = new LocalStackContainer("0.12.12", List(Service.S3))

  "x" should { //5678
    "y" in {
//
//      val sqsService = Service.SQS
//      val container = new LocalStackContainer("0.12.12", List(sqsService))
//      container.container.start()
//      container.services
//
//      println("**********************************************************************************************")
//      println()
//      println(container.container.getCurrentContainerInfo)
//      println("")
//      val endpoint = container.endpointConfiguration(sqsService).getServiceEndpoint
//      println(endpoint)
//      Thread.sleep(100000000)

      Main.main(Array.empty[String])

    }
  }

}
