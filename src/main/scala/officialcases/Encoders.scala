package officialcases

import cats.data.EitherT
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxEitherId
import org.http4s.{DecodeFailure, EntityDecoder, Media, MediaType}
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`

object Encoders extends App {

  sealed trait Resp

  case class Audio(body: String) extends Resp

  case class Video(body: String) extends Resp


  val response = Ok("").map(_.withContentType(`Content-Type`(MediaType.audio.ogg)))

  val audioDec = EntityDecoder.decodeBy(MediaType.audio.ogg) { (m: Media[IO]) =>
    EitherT {
      m.as[String].map(s => Audio(s).asRight[DecodeFailure])
    }
  }

  val videoDec = EntityDecoder.decodeBy(MediaType.video.ogg) { (m: Media[IO]) =>
    EitherT {
      m.as[String].map(s => Video(s).asRight[DecodeFailure])
    }
  }

  val jsonDec = EntityDecoder.decodeBy(MediaType.application.json) { (m: Media[IO]) =>
    EitherT {
      m.as[String].map(s => Video(s).asRight[DecodeFailure])
    }
  }

  //  implicit val bothDec: EntityDecoder[IO, Resp] = audioDec.widen[Resp] orElse videoDec.widen[Resp]
  private val jsonResp = Ok("").map(_.withContentType(`Content-Type`(MediaType.application.json)))
  implicit val json: EntityDecoder[IO, Resp] = jsonDec.widen[Resp]
  println(jsonResp.flatMap(_.as[String]).unsafeRunSync())


}
