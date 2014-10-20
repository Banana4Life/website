package controllers

import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(views.html.blog())
  }

  def snippets = Action {
    Ok(views.html.blog())
  }

  def projects = Action {
    Ok(views.html.blog())
  }

  def about = Action {
    Ok(views.html.blog())
  }
}
