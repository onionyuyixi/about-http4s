package officialcases

import cats.data.Kleisli
import cats.effect._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.dsl.request.Root
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server._

object HelloApi {
  val helloService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"hello $name")
  }
}

object Test extends IOApp {
  val routes: Kleisli[IO, Request[IO], Response[IO]] = Router("/" -> HelloApi.helloService).orNotFound
  val server: Resource[IO, Server] = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(routes)
    .build

  override def run(args: List[String]): IO[ExitCode] =
    server.use(_ => IO.never).as(ExitCode.Success)
}
