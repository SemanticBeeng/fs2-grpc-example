import cats.effect._
import cats.syntax.flatMap._
import com.example.protos.hello._
import fs2._
import io.grpc._
import io.grpc.protobuf.services.ProtoReflectionService
import scala.concurrent.ExecutionContext.Implicits.global
import org.lyranthe.fs2_grpc.java_runtime.implicits._

class ExampleImplementation extends GreeterFs2Grpc[IO] {

  override def sayHello(request: HelloRequest,
                        clientHeaders: Metadata): IO[HelloReply] = {
    IO(HelloReply("Request name is: " + request.name))
  }

  override def sayHelloStream(
      request: Stream[IO, HelloRequest],
      clientHeaders: Metadata): Stream[IO, HelloReply] = {
    request.evalMap(req => IO(println(s"Responding to $req")) >> sayHello(req, clientHeaders))
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
