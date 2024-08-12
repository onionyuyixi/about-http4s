package others

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import java.time.format.DateTimeFormatter

class TimeApi extends Http4sDsl[IO]{


  private val printer  = TimerPrinter(DateTimeFormatter.RFC_1123_DATE_TIME)

  val service  =  HttpRoutes.of[IO]{
    case GET -> Root/"datetime"/country =>
      try {Ok(printer.now(country))}
      catch {
        case ex: IllegalArgumentException =>
          NotFound(ex.getMessage)
      }
  }


}
