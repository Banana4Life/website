import controllers._
import play.api.cache.Cached
import play.api.cache.caffeine.CaffeineCacheComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.filters.HttpFiltersComponents
import play.filters.csrf.CSRFComponents
import service._

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

  // Services
  private val githubService = new GithubService(wsClient, defaultCacheApi.sync, configuration, executionContext)
  private val tumblrService = new TumblrService(configuration, wsClient, defaultCacheApi, executionContext)
  private val ldjamService = new LdjamService(configuration, defaultCacheApi, executionContext, wsClient)
  private val youtubeService = new YoutubeService(configuration, executionContext)
  private val twitchService = new TwitchService(configuration, wsClient, executionContext)
  private val searchIndexService = new SearchIndex

  // ActionBuilders
  private val cached = new Cached(defaultCacheApi)

  // Controllers
  private val errorHandler = new ErrorHandler(environment, configuration, devContext.map(_.sourceMapper), Some(router))
  private val blogController = new BlogController(cached, githubService, tumblrService, ldjamService, youtubeService, twitchService, searchIndexService, executionContext, controllerComponents)
  private val mainController = new MainController(cached, githubService, tumblrService, ldjamService, youtubeService, twitchService, searchIndexService, executionContext, controllerComponents)
  private val ld56C2Controller = new Ld56C2Controller(controllerComponents)(actorSystem, materializer)

  // The router
  override def router: Router = new _root_.router.Routes(
    errorHandler,
    mainController,
    blogController,
    assets,
    ld56C2Controller,
  )
}