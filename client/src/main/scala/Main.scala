import cats.effect.IO
import com.example.protos.hello._
import fs2._
import io.grpc._
import org.lyranthe.fs2_grpc.java_runtime.implicits._
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends StreamApp[IO] {
  val managedChannelStream: Stream[IO, ManagedChannel] =
    ManagedChannelBuilder
      .forAddress("127.0.0.1", 9999)
      .usePlaintext()
      .stream[IO]

  def runProgram(helloStub: GreeterFs2Grpc[IO]): IO[Unit] = {
    for {
      response <- helloStub.sayHello(HelloRequest("John Doe"), new Metadata())
      _ <- IO(println(response.message))
    } yield ()
  }

  /**
    * #todo experiment with https://github.com/fiadliel/fs2-grpc/blob/88dbc747e7f093b3f7344cd365bc60d8f2d52d5c/java-runtime/src/test/scala/client/ClientSuite.scala#L194-L218
    */
  def runProgramStream(helloStub: GreeterFs2Grpc[IO]): Stream[IO, Unit] = {

    val hellos = Stream.eval(IO(HelloRequest("Mr John Doe"))) ++ Stream.eval(IO(HelloRequest("Anastasia")))
    for {
      responses <- helloStub.sayHelloStream(hellos, new Metadata())
      _ ← Stream.eval(IO(println(responses.message)))
    } yield ()
  }

  override def stream(
      args: List[String],
      requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    for {
      managedChannel <- managedChannelStream
      helloStub = GreeterFs2Grpc.stub[IO](managedChannel)
      //_ <- Stream.eval(runProgram(helloStub))
      _ <- runProgramStream(helloStub)
    } yield StreamApp.ExitCode.Success
  }
}
