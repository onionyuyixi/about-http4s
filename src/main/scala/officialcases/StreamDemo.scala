package officialcases


import cats.effect.kernel.Async
import cats.effect.{ExitCode, IO, IOApp, Resource}
import fs2.Stream
import fs2.io.stdout
import fs2.text.{lines, utf8Encode}
import io.circe.Json
import io.circe.jawn.CirceSupportParser
import org.http4s.{HttpRoutes, Method, Request}
import org.http4s.Method.GET
import org.http4s.client.oauth1.ProtocolParameter.{Nonce, Timestamp}
import org.http4s.client.{Client, oauth1}
import org.http4s.client.oauth1.{Consumer, ProtocolParameter, Token}
import org.http4s.dsl.io._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.http4sLiteralsSyntax
import org.typelevel.jawn.Facade
import org.typelevel.jawn.fs2.JsonStreamSyntax
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object StreamDemo {

  val seconds: Stream[IO, FiniteDuration] = Stream.awakeEvery[IO](1.second)

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "seconds" =>
      Ok(seconds.map(_.toString()))
  }

  val client: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build



}


class TWStream[F[_] : Async] {

  implicit val f: Facade[Json] = new CirceSupportParser(None, false).facade

  implicit val loggerFactory: LoggerFactory[F] = Slf4jFactory.create[F]

  def sign(consumerKey: String, consumerSecret: String,
           accessToken: String, accessSecret: String)
          (req: Request[F]): F[Request[F]] = {
    val consumer = ProtocolParameter.Consumer(consumerKey, consumerSecret)
    val token = ProtocolParameter.Token(accessToken, accessSecret)
    oauth1.signRequest(
      req,
      consumer,
      Some(token),
      realm = None,
      timestampGenerator = Timestamp.now,
      nonceGenerator = Nonce.now,
    )
  }

  def jsonStream(consumerKey: String, consumerSecret: String,
                 accessToken: String, accessSecret: String)
                (req: Request[F]): Stream[F, Json] =
    for {
      client <- Stream.resource(EmberClientBuilder.default[F].build)
      sr  <- Stream.eval(sign(consumerKey, consumerSecret, accessToken, accessSecret)(req))
      res <- client.stream(sr).flatMap(_.body.chunks.parseJsonStream)
    } yield res

  val stream: Stream[F, Unit] = {
    val req = Request[F](Method.GET, uri"https://global.jd.com/")
    val s   = jsonStream("<consumerKey>", "<consumerSecret>",
      "<accessToken>", "<accessSecret>")(req)
    s.map(_.spaces2).through(lines).through(utf8Encode).through(stdout)
  }

  def run: F[Unit] =
    stream.compile.drain
}

object TWStreamApp extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    new TWStream[IO].run.as(ExitCode.Success)
}
