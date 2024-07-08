import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.implicits._

import scala.concurrent.ExecutionContext
object GameApp extends IOApp{

  private val router = Router{
    "/"->new GameApi().routes
  }.orNotFound

  private def stream(args:List[String]) =
    BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(8080,"0.0.0.0")
      .withHttpApp(router)
      .serve


  override def run(args: List[String]): IO[ExitCode] =
    stream(args).compile.drain.as(ExitCode.Success)
}
