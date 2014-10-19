package controllers

import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(views.html.test())
  }

  def snippets = TODO

  def projects = TODO

  def about = TODO

}
