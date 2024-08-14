package officialcases

import cats.data.Kleisli
import cats.effect._
import cats.implicits.{toBifunctorOps, toSemigroupKOps}
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.impl.MatrixVar
import org.http4s.dsl.io._
import org.http4s.dsl.request.Root
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server._

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, Year}
import scala.util.Try

object ParamApi {
  val paramService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"hello $name")
    case GET -> "rest" /: rest =>
      Ok(s"Hello , rest is ${rest.segments.mkString(" and ")}")
    case GET -> IntVar(anInt) /: rest =>
      Ok(s"Hello ,num is $anInt,rest is ${rest.segments.mkString(" and ")}")
    case GET -> Root / file ~ "json" =>
      Ok(s"the filename is $file ,json file ")
  }
}

object LocalDateVarApi {

  def unapply(str: String): Option[LocalDate] = {
    if (str.nonEmpty) {
      Try(LocalDate.parse(str)).toOption
    } else
      None
  }

  private def getTemperatureForest(date: LocalDate) = IO(42.23)

  val request: Request[IO] = Request[IO](Method.GET, uri"/weather/temperature/2016-11-05")

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "weather" / "temperature" / LocalDateVarApi(localDate) =>
      Ok(getTemperatureForest(localDate).map(s" the temperature on $localDate will be " + _))
  }

}

object MatrixVarApi {

  object FullNameExtractor extends MatrixVar("name", List("first", "last", "id"))

  val service = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / FullNameExtractor(first, last, IntVar(id)) / "greeting" =>
      Ok(s"hello , $last $first , the id is $id")
  }

}

object QueryParamApi {

  private object CountryParamMatcher extends QueryParamDecoderMatcher[String]("country")

  object OptionalYearQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Year]("year")

  object OptionalMultiColorQueryParam extends OptionalMultiQueryParamDecoderMatcher[String]("maybeColors")

  implicit val yearDecoder: QueryParamDecoder[Year] = QueryParamDecoder[Int].map(Year.of)

  private object YearQueryMatcher extends QueryParamDecoderMatcher[Year]("year")

  private def getAverageTemperatureForCountryAndYear(country: String, year: Year): IO[Double] = IO(23.22)

  val service = HttpRoutes.of[IO] {
    case GET -> Root / "weather" / "temperature" :? CountryParamMatcher(country) +& YearQueryMatcher(year) =>
      Ok(getAverageTemperatureForCountryAndYear(country, year)
        .map(s"$country average temperature in $year is " + _))
  }

  implicit val isoInstantCodec: QueryParamCodec[Instant] = QueryParamCodec.instantQueryParamCodec(DateTimeFormatter.ISO_INSTANT)

  object ISOInstantParamMatcher extends QueryParamDecoderMatcher[Instant]("timestamp")


}


object ErrorParamApi {

  implicit val yearQueryParamDecoder: QueryParamDecoder[Year] = QueryParamDecoder[Int]
    .emap(i => Try(Year.of(i))
      .toEither
      .leftMap(t => ParseFailure(t.getMessage, t.getMessage)))

  private object YearQueryMatcher extends ValidatingQueryParamDecoderMatcher[Year]("year")

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "temperature" :? YearQueryMatcher(yearValidate) =>
      yearValidate.fold(_ => BadRequest("unable to parse argument year"),
        year => Ok(s"i get year $year")
      )
  }

  object LongParamMatcher extends OptionalValidatingQueryParamDecoderMatcher[Long]("long")
  val routes1: HttpRoutes[IO] = HttpRoutes.of[IO]{
    case GET -> Root / "number" :? LongParamMatcher(maybeNumber) =>
      val _: Option[cats.data.ValidatedNel[org.http4s.ParseFailure, Long]] = maybeNumber
      maybeNumber match {
        case Some(n) =>
          n.fold(
            _ => BadRequest("unable to parse argument 'long'"),
            _ => Ok(n.toString)
          )
        case None => BadRequest("missing number")
      }
  }


}


object TestParamApi extends IOApp {
  val services = ParamApi.paramService <+>
    LocalDateVarApi.service <+>
    MatrixVarApi.service <+>
    QueryParamApi.service <+>
    ErrorParamApi.routes<+>
    ErrorParamApi.routes1
  val routes: Kleisli[IO, Request[IO], Response[IO]] = Router("/" -> services).orNotFound
  val server: Resource[IO, Server] = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8081")
    .withHttpApp(routes)
    .build

  override def run(args: List[String]): IO[ExitCode] =
    server.use(_ => IO.never).as(ExitCode.Success)
}
