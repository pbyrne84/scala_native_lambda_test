package lambda

class MainSpec extends BaseSpec {

  // val localstackImage = DockerImageName.parse( "localstack/localstack:0.11.3" )

//  override def container: Container = new LocalStackContainer("0.12.12", List(Service.S3))

  "main" should { // 5678
    "throw an exception when no message is passed" in {
      a[NoMessagePassedException] should be thrownBy Main.main(Array.empty[String])
    }

    "not fail on a successful message" in {
      val message = parseJson(
        """
          |{
          |    "Records": [
          |        {
          |            "messageId": "a78cdb8b-d8cb-4c28-be77-fc89608f022c",
          |            "receiptHandle": "AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==",
          |            "body": "I, am, the, one, and, only",
          |            "attributes": {
          |                "ApproximateReceiveCount": "1",
          |                "SentTimestamp": "1670426083016",
          |                "SenderId": "538645939706",
          |                "ApproximateFirstReceiveTimestamp": "1670426083021"
          |            },
          |            "messageAttributes": {},
          |            "md5OfBody": "a390ff989d692670fa09d8d64b134179",
          |            "eventSource": "aws:sqs",
          |            "eventSourceARN": "arn:aws:sqs:eu-west-2:538645939706:test-queue",
          |            "awsRegion": "eu-west-2"
          |        }
          |    ]
          |}
          |""".stripMargin.trim
      )

      // hack as currently when the message is passed by the shell script it splits things up on args
      // should try porting https://github.com/redskap/aws-lambda-java-runtime to scala to get around this hackery
      val messageSplitByComma = message.spaces2.split(",")
      Main.main(messageSplitByComma)
    }
  }

}
