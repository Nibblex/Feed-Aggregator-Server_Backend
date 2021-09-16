package feedaggregator.server

import feedaggregator.actorsystem.Manager._
import JsonFormats._

import akka.actor.typed.{ActorSystem, ActorRef}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.format.ResolverStyle
import language.postfixOps

import spray.json.DefaultJsonProtocol._


class Routes(managerActor: ActorRef[Command])(implicit val system: ActorSystem[_]) {
  private implicit val timeout = Timeout(5 seconds)

  /* Input validation methods */
  def isValidURL(url: String) = {
    Try {
      new URL(url)
    }.isSuccess
  }

  def isValidSince(maybeSince: Option[String]) = {
    Try {
      val fmt = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
      .withResolverStyle(ResolverStyle.STRICT)
      LocalDateTime.parse(maybeSince.get, fmt)
    }.isSuccess || maybeSince.isEmpty
  }

  /* Messages sending methods */
  def feed(url: String, maybeSince: Option[String]): Future[FeedResponse] =
    managerActor ? (Feed(url, maybeSince, _))

  def subscribe(username: String, url: String): Future[SubscribeResponse] =
    managerActor ? (Subscribe(username, url, _))

  def feeds(username: String, maybeSince: Option[String]): Future[FeedsResponse] =
    managerActor ? (Feeds(username, maybeSince, _))

  def addUser(username: String): Future[AddUserResponse] =
    managerActor ? (AddUser(username,_))

  /* Endpoints */
  val route: Route =
    path("feed") {
      (get & parameters('url,'since?)) { (url, maybeSince) =>
        if (isValidURL(url) && isValidSince(maybeSince)) {
          onSuccess(feed(url, maybeSince)) { response =>
            response match {
              case FeedResponse(feed, OK) =>
                complete(feed)
              case FeedResponse(_, NotFound) =>
                complete(StatusCodes.NotFound -> "Not found: `url` doesn't exist")
              case _ =>
                complete(StatusCodes.BadRequest)
            }
          }
        } else {
          complete(StatusCodes.BadRequest -> "Bad request: can't parse `url`/`since`")
        }
      }
    } ~
    (post & path("subscribe")) {
      entity(as[Subscription]) { subscription =>
        val username = subscription.username
        val url = subscription.url.get

        if (isValidURL(url)) {
          onSuccess(subscribe(username, url)) { response =>
            response match {
              case SubscribeResponse(_, OK) =>
                complete(StatusCodes.OK -> s"${response.response}")
              case SubscribeResponse(_, NotFound) =>
                complete(StatusCodes.NotFound -> s"${response.response}")
              case _ =>
                complete(StatusCodes.BadRequest)
            }
          }
        } else {
          complete(StatusCodes.BadRequest -> "Bad request: can't parse `url`")
        }
      }
    } ~
    path("feeds") {
      (get & parameter('username, 'since ?)) { (username, maybeSince) =>
        if (isValidSince(maybeSince)) {
          onSuccess(feeds(username, maybeSince)) { response =>
            response match {
              case FeedsResponse(feeds, OK) =>
                complete(feeds)
              case FeedsResponse(_, NotFound) =>
                complete(StatusCodes.NotFound ->
                  s"Not found: there are no feeds for `username` or `username` doesn't exist")
              case _ =>
                complete(StatusCodes.BadRequest)
            }
          }
        } else {
          complete(StatusCodes.BadRequest -> "Bad request: can't parse `since`")
        }
      }
    } ~
    (post & path("user")) {
      entity(as[Subscription]) { subscription =>
        val username = subscription.username

        onSuccess(addUser(username)) { response =>
          response match {
            case AddUserResponse(_, OK) =>
              complete(StatusCodes.OK -> s"${response.response}")
            case AddUserResponse(_, Conflict) =>
              complete(StatusCodes.Conflict -> s"${response.response}")
            case _ =>
                complete(StatusCodes.BadRequest)
          }
        }
      }
    }  
}