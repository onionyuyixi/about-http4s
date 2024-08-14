package officialcases

import cats.data.Kleisli
import cats.effect._
import cats.implicits.toSemigroupKOps
import com.comcast.ip4s.IpLiteralSyntax
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.{Header, HttpRoutes, Request, Response, Status}

object Middlewares {


  def myMiddle(service: HttpRoutes[IO], header: Header.ToRaw): HttpRoutes[IO] = Kleisli {
    (req: Request[IO]) =>
      service(req).map {
        case Status.Successful(resp) =>
          resp.putHeaders(header)
        case other => other
      }
  }

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "bad" => BadRequest()
    case _ => Ok()
  }

  val modifiedService: HttpRoutes[IO] = myMiddle(service = service, header = "userId" -> "adefef-wdefss-9092")


  object MyMiddleWare {

    def apply(service: HttpRoutes[IO], header: Header.ToRaw): HttpRoutes[IO] =
      service.map(addHeader(_, header))

    private def addHeader(resp: Response[IO], header: Header.ToRaw): Response[IO] =
      resp match {
        case Status.Successful(resp) =>
          resp.putHeaders(header)
        case other => other
      }

  }


  val service1: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "bad" / IntVar(s) => BadRequest()
    case _ => Ok()
  }
  val middlewareService = MyMiddleWare(service = service1, header = "fengzi" -> "true")


}

object TestMiddleWare extends IOApp.Simple {


  private val app: Kleisli[IO, Request[IO], Response[IO]] = Router("/1" -> Middlewares.modifiedService,
    "/2/" -> Middlewares.middlewareService).orNotFound

  override def run: IO[Unit] = EmberServerBuilder.default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8081")
    .withHttpApp(app)
    .build
    .use(_ => IO.never)
    .void
}
