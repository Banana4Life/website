import controllers.ld56.Ld56C2Controller
import controllers._
import play.api.cache.Cached
import play.api.cache.caffeine.CaffeineCacheComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.filters.HttpFiltersComponents
import play.filters.csrf.CSRFComponents
import service._

import scala.collection.mutable

class Banana4Loader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    new Banana4Components(context).application
  }
}

class Banana4Components(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents
    with CSRFComponents
    with CaffeineCacheComponents
    with AhcWSComponents {

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

    routes.map(_.routes).reduce(_.orElse(_))
  }

  // The router
  override def router: Router = Router.from(compositeRoutes)
}