package officialcases

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import cats.effect.std.{Console, Random}
import cats.effect.unsafe.implicits.global
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxParallelTraverse1, catsSyntaxTuple2Semigroupal}
import com.comcast.ip4s.IpLiteralSyntax
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io.{QueryParamDecoderMatcher, _}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.server.middleware._
import org.http4s.server.{ContextMiddleware, HttpMiddleware, Middleware, Router}
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import fs2.Stream._
import org.http4s.headers.Origin

import javax.crypto.SecretKey
import scala.concurrent.duration.DurationInt


object services {

  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "bad" => BadRequest()
    case GET -> Root / "ok" => Ok()
    case r@POST -> Root / "post" => r.as[Unit] >> Ok()
    case r@POST -> Root / "echo" => r.as[String].flatMap(Ok(_))
    case GET -> Root / "b" / "c" => Ok()
    case POST -> Root / "queryForm" :? NameQueryParamMatcher(name) => Ok(s"hello $name")
    case GET -> Root / "wait" => IO.sleep(10.millis) >> Ok()
    case GET -> Root / "boom" => IO.raiseError(new RuntimeException("boom!"))
    case r@POST -> Root / "reverse" => r.as[String].flatMap(s => Ok(s.reverse))
    case GET -> Root / "forever" => IO(
      Response[IO](headers = Headers("hello" -> "hi"))
        .withEntity(Stream.constant("a").covary[IO])
    )
    case r@GET -> Root / "doubleRead" => (r.as[String], r.as[String])
      .flatMapN((a, b) => Ok(s"$a == $b"))
    case GET -> Root / "random" => Random.scalaUtilRandom[IO]
      .flatMap(_.nextInt)
      .flatMap(random => Ok(random.toString))
  }

  val app: HttpApp[IO] = Router.of("/" -> service).orNotFound
  val server = EmberServerBuilder.default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8081")
    .withHttpApp(app)
    .build
  val okRequest: Request[IO] = Request[IO](Method.GET, uri"/ok")
  val badRequest: Request[IO] = Request[IO](Method.GET, uri"/bad")
  val postRequest: Request[IO] = Request[IO](Method.POST, uri"/post")
  val waitRequest: Request[IO] = Request[IO](Method.GET, uri"/wait")
  val boomRequest: Request[IO] = Request[IO](Method.GET, uri"/boom")
  val reverseRequest: Request[IO] = Request[IO](Method.POST, uri"/reverse")


}


object CacheService {

  val service: HttpApp[IO] = Caching.cache(
    lifetime = 3.hours,
    isPublic = Left(CacheDirective.public),
    methodToSetOn = _ == Method.GET,
    statusToSetOn = _.isSuccess,
    http = services.service
  ).orNotFound


}

object DateService {

  val service: HttpApp[IO] = Date.httpRoutes(services.service).orNotFound

}

object HeaderEchoService {

  // req的headers传递给了 response
  val service: HttpApp[IO] = HeaderEcho
    .httpRoutes(echoHeadersWhen = _ => true)(services.service)
    .orNotFound

}

// 为每个req添加reqId
object RequestIdService {

  val service = RequestId.httpRoutes(HttpRoutes.of[IO] {
    case req =>
      val reqId = req.headers.get(ci"X-Request-ID").fold("null")(_.head.value)
      // use request id to correlate logs with the request
      Console[IO].println(s"request received, cid=$reqId") *> Ok()
  }).orNotFound

}

// 表单数据
object UrlFormLifterService {

  val service = UrlFormLifter.httpRoutes(services.service)
  val client = Client.fromHttpApp(service.orNotFound)
  val formRequest = Request[IO](Method.POST, uri"/queryForm")
    .withEntity(UrlForm.single("name", "John"))

}

object HttpRedirectService {

  val service = HttpsRedirect(services.service).orNotFound
  val client = Client.fromHttpApp(service)
  val request = services.okRequest
    .putHeaders("Host" -> "baidu.com", "X-Forwarded-Proto" -> "https")

}

// uri转换 即增加映射关系
object TranslateUriService {

  val service = TranslateUri(prefix = "a")(services.service).orNotFound
  val client = Client.fromHttpApp(service)
  val request = Request[IO](method = GET, uri = uri"a/b/c")

}

// 插入一个中间观察变量 用于debug/metrics
object ContextService {

  def dropContext[A](middleWare: ContextMiddleware[IO, A]): HttpMiddleware[IO] =
    routes => middleWare(Kleisli((c: ContextRequest[IO, A]) => routes(c.req)))

  val service = ConcurrentRequests.route[IO](
    onIncrement = total => Console[IO].println(s"someone comes to town, total = $total"),
    onDecrement = total => Console[IO].println(s"someone leaves to town , total = $total"),
  ).map((middle: ContextMiddleware[IO, Long]) => dropContext(middle)(services.service).orNotFound)

  val client: IO[Client[IO]] = service.map(Client.fromHttpApp[IO])
}

// 数据大小监测 超过报异常
object EntityLimiterService {

  val service: HttpRoutes[IO] = EntityLimiter.httpRoutes(services.service, limit = 16)
  val client = Client.fromHttpApp(service.orNotFound)
  val smallReq = services.postRequest.withEntity("*" * 15)
  val bigReq = services.postRequest.withEntity("*" * 18)
}

// 最大可用并发数

object MaxActiveService {

  val service = MaxActiveRequests.forHttpApp[IO](maxActive = 2)
    .map(middleware => middleware(services.service.orNotFound))

  val client = service.map(Client.fromHttpApp[IO])

}

// 限流
object ThrottleService {

  // 10毫秒内 只能有一个req
  val service = Throttle.httpApp[IO](amount = 1, per = 10.milliseconds)(services.service.orNotFound)

  val client = service.map(Client.fromHttpApp[IO])

}

// 超时

object TimeoutService {

  val service = Timeout.httpApp[IO](timeout = 5.milliseconds)(services.service.orNotFound)
  val client = Client.fromHttpApp(service)

}


// 错误
object ErrorActionService {

  val service = ErrorAction.httpRoutes[IO](services.service, (req, err) =>
    Console[IO].println("Oops: error is " ++ err.getMessage)
  ).orNotFound

  val client = Client.fromHttpApp(service)

}

// 错误处理
object ErrorHandleService {

  val service = ErrorHandling.httpRoutes[IO](services.service).orNotFound

  val client = Client.fromHttpApp(service)

}

// bodyCache
object BodyCacheService {

  val service = BodyCache.httpRoutes(services.service).orNotFound

  val request = Request[IO](method = GET, uri = uri"/doubleRead")
    .withEntity(
      Stream.eval(
        Random.scalaUtilRandom[IO].flatMap(_.nextInt).map(_.toString)
      )
    )

  val client = Client.fromHttpApp(service)

}

// 传递变量
object ContextMiddleware1 {

  case class UserId(raw: String)

  // 自定义一个header
  implicit val userIdHeader: Header[UserId, Header.Single] =
    Header.createRendered(name_ = ci"X-UserId",
      value_ = userId => userId.raw,
      parse_ = s => Right(UserId(s)))

  // 创建一个跟header相关的context-middle-ware实例
  val middleware: ContextMiddleware[IO, UserId] = ContextMiddleware(Kleisli(
    (req: Request[IO]) => OptionT.fromOption[IO](req.headers.get[UserId])
  ))

  val ctxRoutes: ContextRoutes[UserId, IO] = ContextRoutes.of[UserId, IO] {
    case GET -> Root / "ok" as userId => Ok(s"hello ${userId.raw}")
  }


  val service = middleware(ctxRoutes).orNotFound
  val client = Client.fromHttpApp(service)
  val request = Request[IO](Method.GET, uri"/ok").putHeaders(UserId("Jack"))


}

object SimpleDemo extends App {

  def testHttpApp(request: Request[IO], httpApp: HttpApp[IO]) = {
    val headers = Client.fromHttpApp(httpApp)
      .run(request)
      .use(_.headers.pure[IO])
      .unsafeRunSync()
    println(headers)
  }

  testHttpApp(services.okRequest, CacheService.service)
  testHttpApp(services.badRequest, CacheService.service)
  testHttpApp(services.postRequest, CacheService.service)

  testHttpApp(services.okRequest, DateService.service)
  testHttpApp(services.badRequest, DateService.service)
  testHttpApp(services.postRequest, DateService.service)

  println(Client.fromHttpApp(HeaderEchoService.service)
    .run(services.okRequest.putHeaders("hello" -> "hi"))
    .use(_.headers.pure[IO])
    .unsafeRunSync())

  println(Client.fromHttpApp(RequestIdService.service)
    .run(services.okRequest)
    .use(resp =>
      (resp.headers, resp.attributes.lookup(RequestId.requestIdAttrKey)).pure[IO]
    )
    .unsafeRunSync())

  println(UrlFormLifterService.client.expect[String](UrlFormLifterService.formRequest).unsafeRunSync())

  println(HttpRedirectService.client
    .run(HttpRedirectService.request)
    .use(r => (r.headers, r.status).pure[IO])
    .unsafeRunSync())

  println(TranslateUriService.client
    .status(TranslateUriService.request)
    .unsafeRunSync())


  ContextService.client.flatMap {
    cl => List.fill(10)(services.waitRequest).parTraverse(req => cl.expect[Unit](req))
  }.void.unsafeRunSync()


  println(EntityLimiterService.client
    .status(EntityLimiterService.smallReq)
    .unsafeRunSync()
  )
  //  println(EntityLimiterService.client
  //    .status(EntityLimiterService.bigReq)
  //    .unsafeRunSync()
  //  )

  println(MaxActiveService.client.flatMap(cl =>
    List.fill(5)(services.waitRequest).parTraverse(req => cl.status(req))
  ).unsafeRunSync())

  //List(200 OK, 429 Too Many Requests, 429 Too Many Requests, 429 Too Many Requests, 429 Too Many Requests)
  println(ThrottleService.client.flatMap(cl =>
    List.fill(5)(services.okRequest).parTraverse(req => IO.sleep(20.milliseconds) >> cl.status(req))
  ).unsafeRunSync())

  println(TimeoutService.client.status(services.waitRequest).timed.unsafeRunSync())

  println(ErrorActionService.client.expect[Unit](services.boomRequest).attempt.unsafeRunSync())

  println(ErrorHandleService.client.status(services.boomRequest).unsafeRunSync())


  // 1762417765 == 1762417765
  println(BodyCacheService.client.expect[String](BodyCacheService.request).unsafeRunSync())
  // -2043870455 == 1345249311
  println(Client.fromHttpApp(services.service.orNotFound).expect[String](BodyCacheService.request).unsafeRunSync())

  println(ContextMiddleware1.client.expect[String](ContextMiddleware1.request).unsafeRunSync())

}

object CQRS extends App {
  val service = HttpRoutes.of[IO] {
    case _ => Ok()
  }
  val request = Request[IO](Method.GET, uri"/")

  println(service.orNotFound(request).unsafeRunSync())


  val corsService = CORS.policy.withAllowOriginAll(service).orNotFound(request).unsafeRunSync()
  println(corsService)

  val corsRequest = request.putHeaders("Origin" -> "https://somewhere.com")
  val corsService1 = CORS.policy.withAllowOriginAll(service).orNotFound(corsRequest).unsafeRunSync()
  println(corsService1)

  val googleGet = Request[IO](Method.GET, uri"/",
    headers = Headers("Origin" -> "https://google.com"))

  val yahooPut = Request[IO](Method.PUT, uri"/",
    headers = Headers("Origin" -> "https://yahoo.com"))

  val duckPost = Request[IO](Method.POST, uri"/",
    headers = Headers("Origin" -> "https://duckduckgo.com"))

  val corsMethodSvc = CORS.policy
    .withAllowOriginAll
    .withAllowMethodsIn(Set(Method.GET, Method.POST))
    .withAllowCredentials(false)
    .withMaxAge(1.day)
    .apply(service)

  val corsOriginSvc = CORS.policy
    .withAllowOriginHost(Set(
      Origin.Host(Uri.Scheme.https, Uri.RegName("yahoo.com"), None),
      Origin.Host(Uri.Scheme.https, Uri.RegName("duckduckgo.com"), None)
    ))
    .withAllowMethodsIn(Set(Method.GET))
    .withAllowCredentials(false)
    .withMaxAge(1.day)
    .apply(service)

  println(corsMethodSvc.orNotFound)
  println(corsOriginSvc.orNotFound)

  println(corsMethodSvc.orNotFound(googleGet).unsafeRunSync())
  println(corsMethodSvc.orNotFound(yahooPut).unsafeRunSync())
  println(corsMethodSvc.orNotFound(duckPost).unsafeRunSync())

  println(corsOriginSvc.orNotFound(googleGet).unsafeRunSync())
  println(corsOriginSvc.orNotFound(yahooPut).unsafeRunSync())
  println(corsOriginSvc.orNotFound(duckPost).unsafeRunSync())

}


object Csrf extends App {

  val service = HttpRoutes.of[IO] {
    case _ => Ok()
  }

  val request = Request[IO](Method.GET, uri"/")

  val cookiename = "csrf-cookie";
  val key: SecretKey = CSRF.generateSigningKey[IO].unsafeRunSync()
  val defaultOriginCheck: Request[IO] => Boolean =
    req => CSRF.defaultOriginCheck[IO](r = req, host = "localhost", sc = Uri.Scheme.http, port = None)

  val csrfBuilder = CSRF[IO, IO](key, defaultOriginCheck)

  val csrf = csrfBuilder
    .withCookieName(cookieName = cookiename)
    .withCookieDomain(Some("localhost"))
    .withCookiePath(Some("/"))
    .build
  val dummyRequest: Request[IO] =
    Request[IO](method = Method.GET).putHeaders("Origin" -> "http://localhost")


  private val value: Middleware[IO, Request[IO], Response[IO], Request[IO], Response[IO]] = csrf.validate()

  val resp = value(service.orNotFound)(dummyRequest).unsafeRunSync()

  println(resp.headers)
  private val cookie: ResponseCookie = resp.cookies.head
  println(cookie)

  val dummyPostRequest: Request[IO] = Request[IO](method = Method.POST)
      .putHeaders("Origin" -> "http://localhost", "X-Csrf-Token" -> cookie.content)
      .addCookie(RequestCookie(cookie.name, cookie.content))
  println(dummyPostRequest)

  val validateResp = csrf.validate()(service.orNotFound)(dummyPostRequest).unsafeRunSync()
  println(validateResp)
  println(validateResp.headers)


}