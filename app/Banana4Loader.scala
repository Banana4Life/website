import controllers.ld56.Ld56C2Controller
import controllers.*
import controllers.ld58.Ld58Controller
import play.api.cache.Cached
import play.api.cache.caffeine.CaffeineCacheComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.filters.HttpFiltersComponents
import play.filters.cors.{CORSConfig, CORSFilter}
import play.filters.csrf.CSRFComponents
import service.*

import scala.collection.mutable

class Banana4Loader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    new Banana4Components(context).application
  }
}

private val corsConfig = CORSConfig(
  allowedOrigins = CORSConfig.Origins.All,
  supportsCredentials = false,
)

class Banana4Components(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents
    with CSRFComponents
    with CaffeineCacheComponents
    with AhcWSComponents {
  private val corsFilter = CORSFilter(corsConfig, httpErrorHandler, Seq("/ld"))

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters :+ corsFilter

  // ActionBuilders
  private val cached = new Cached(defaultCacheApi)

  // Dynamic Routing
  private val compositeRoutes = {
    val routes = mutable.ArrayBuffer[Router]()
    val errorHandler = new ErrorHandler(environment, configuration, devContext.map(_.sourceMapper), Some(router))
    if (configuration.get[Boolean]("features.showWebsite")) {

      // Services
      val githubService = new GithubService(wsClient, defaultCacheApi.sync, configuration, executionContext)
      val tumblrService = new TumblrService(configuration, wsClient, defaultCacheApi, executionContext)
      val ldjamService = new LdjamService(configuration, defaultCacheApi, executionContext, wsClient)
      val twitchService = new TwitchService(configuration, wsClient, executionContext)
      val searchIndexService = new SearchIndex
      // Controllers
      val blogController = new BlogController(tumblrService, ldjamService, executionContext, controllerComponents)
      val mainController = new MainController(cached, githubService, tumblrService, ldjamService, twitchService, searchIndexService, executionContext, controllerComponents)
      // Routes
      val mainRoutes = new _root_.main.Routes(errorHandler, mainController, blogController, assets)
      routes += mainRoutes
    }

    val enabledGames = configuration.get[Seq[String]]("features.runGames")
    if (enabledGames.contains("LD56")) {
      // Controllers
      val ld56C2Controller = new Ld56C2Controller(controllerComponents)(using actorSystem, materializer)
      // Routes
      val ld56Routes = new _root_.ld56.Routes(errorHandler, ld56C2Controller)
      routes += ld56Routes
    }
    
    if (enabledGames.contains("LD58")) {
      // Controllers
      val ldjamService = new LdjamService(configuration, defaultCacheApi, executionContext, wsClient)
      val ld58Controller = new Ld58Controller(controllerComponents, ldjamService, executionContext)(using actorSystem, materializer)
      // Routes
      val ld58Routes = new _root_.ld58.Routes(errorHandler, ld58Controller)
      routes += ld58Routes
    }

    routes.map(_.routes).reduce(_.orElse(_))
  }

  // The router
  override def router: Router = Router.from(compositeRoutes)
}