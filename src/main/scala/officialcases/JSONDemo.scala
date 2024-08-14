package officialcases

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.IpLiteralSyntax
import io.circe._
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import io.circe.literal._
import io.circe.syntax.EncoderOps
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{EntityDecoder, HttpRoutes, Method, Request}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

//object JSONDemo extends App {
//
//  def hello(name: String): Json =
//    json"""{"hello": $name}"""
//
//  println(hello("onion"))
//
//  println(Ok(hello("yuyixi")).unsafeRunSync())
//
//  val req = Request[IO](Method.POST, uri"/hello")
//    .withEntity(json"""{"name": "Alice"}""")
//
//  case class Hello(name:String)
//  case class User(name:String)
//
//  implicit val helloEncoder: Encoder[Hello] = Encoder.instance{
//    (hello:Hello) => json"""{"hello": ${hello.name}}"""
//  }
//
//  println(Hello("Alice").asJson)
//
//  implicit val userEncoder: Encoder[User] = Encoder.instance{
//    (user:User) => json"""{"hello": ${user.name}}"""
//  }
//
//  println(User("Pengpeng").asJson)
//
//  implicit val userDecoder: EntityDecoder[IO, User] = jsonOf[IO, User]
//  //User(Alice)
//  println(Ok("""{"name":"Alice"}""").flatMap(_.as[User]).unsafeRunSync())
//
//  // {
//  //  "name" : "Alice"
//  //}
//  println(Ok("""{"name":"Alice"}""").flatMap(_.as[Json]).unsafeRunSync())
//
//  // User(Bob)
//  println(Request[IO](Method.POST, uri"/hello")
//    .withEntity("""{"name":"Bob"}""")
//    .as[User].unsafeRunSync())
//
//}

object SimpleApp extends App {

  case class Hello(greeting: String)

  case class User(name: String)

  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  implicit val decoder: EntityDecoder[IO, User] = jsonOf[IO, User]

  val jsonApp = HttpRoutes.of[IO] {
    case req@GET -> Root / "hello" =>
      for {
        user <- req.as[User]
        resp <- Ok(Hello(user.name).asJson)
      } yield resp
  }.orNotFound

  val server = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8081")
    .withHttpApp(jsonApp)
    .build


  server.allocated.unsafeRunSync()


}