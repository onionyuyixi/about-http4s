package officialcases

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import org.http4s.client.{Client, JavaNetClientBuilder}
import org.http4s.ember.client.EmberClientBuilder

object ClientDemo extends App {


  val clientResource: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build

  val baidu: String = clientResource.use {
    client => client.expect[String]("https://www.baidu.com")
  }.unsafeRunSync()

  println(s"invoke baidu result is $baidu")

  val google: String = clientResource.use {
    client => client.expect[String]("https://www.google.com")
  }.unsafeRunSync()

  println(s"invoke google result is $google")

  val javaClient  = JavaNetClientBuilder[IO].create
  val jd = javaClient.expect[String]("https://global.jd.com/").unsafeRunSync()
  println("------------------------------------------------")
  println(s"jd is $jd")


}
