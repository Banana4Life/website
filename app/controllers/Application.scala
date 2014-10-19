package controllers

import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(views.html.main("Banana4Life - Blog")(views.html.blog()))
  }

  def snippets = Action {
    Ok(views.html.main("Banana4Life - Snippets")(views.html.blog()))
  }

  def projects = Action {
    Ok(views.html.main("Banana4Life - Projects")(views.html.blog()))
  }

  def about = Action {
    Ok(views.html.main("Banana4Life - About")(views.html.blog()))
  }
}