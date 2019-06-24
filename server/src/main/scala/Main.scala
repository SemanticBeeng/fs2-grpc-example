import cats.effect._
import cats.syntax.flatMap._
import com.example.protos.hello._
import fs2._
import io.grpc._
import io.grpc.protobuf.services.ProtoReflectionService
import org.lyranthe.fs2_grpc.java_runtime.implicits._
//
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class ExampleImplementation extends GreeterFs2Grpc[IO] {

  implicit val timer: Timer[IO] = IO.timer(global)

  override def sayHello(request: HelloRequest,
                        clientHeaders: Metadata): IO[HelloReply] = {
    IO(HelloReply("Request name is: " + request.name))
  }

  override def sayHelloStream(
      request: Stream[IO, HelloRequest],
      clientHeaders: Metadata): Stream[IO, HelloReply] = {

    Stream.eval(IO(println(s"Responding to call."))) >>
    request.evalMap({req ⇒
        if (req.name.contains("Mr"))
          IO(println(s"Responding to Mr $req")) >> sayHello(req, clientHeaders)
        else for {
          _ ← IO(println(s"Responding to lady $req once"))
          _ ← sayHello(req, clientHeaders)
          _ ← IO(println(s"Pausing a little ..."))
          _ ← IO(println(s"Responding to lady $req again"))
          _ ← timer.sleep(1 second)
          r ← sayHello(req, clientHeaders)
          } yield r
      })
  }
}

object Main extends StreamApp[IO] {
  val helloService: ServerServiceDefinition =
    GreeterFs2Grpc.bindService(new ExampleImplementation)
  def stream(args: List[String], requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    ServerBuilder
      .forPort(9999)
      .addService(helloService)
      .addService(ProtoReflectionService.newInstance())
      .stream[IO]
      .evalMap(server => IO(server.start()))
      .evalMap(_ => IO.never)
  }
}
