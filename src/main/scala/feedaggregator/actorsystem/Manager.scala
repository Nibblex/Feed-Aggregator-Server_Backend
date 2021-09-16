package feedaggregator.actorsystem

import feedaggregator.server.JsonFormats._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.Future
import scala.util.{Success,Failure}
import scala.xml.{NodeSeq, Text}

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter._
import java.time.LocalDateTime

object Manager {
  implicit val ec = scala.concurrent.ExecutionContext.global

  /* Messages */
  sealed trait Command
  final case class Feed(url: String,
                        maybeSince: Option[String],
                        replyTo: ActorRef[FeedResponse]) extends Command
  final case class Subscribe(username: String, 
                             url: String,
                             replyTo: ActorRef[SubscribeResponse]) extends Command
  final case class Feeds(username: String, 
                         maybeSince: Option[String],
                         replyTo: ActorRef[FeedsResponse]) extends Command
  final case class AddUser(username: String, 
                           replyTo: ActorRef[AddUserResponse]) extends Command

  /* Status codes */
  sealed trait Status
  final case object OK extends Status
  final case object NotFound extends Status
  final case object Conflict extends Status

  /* Responses */
  final case class FeedResponse(feedInfo: Option[FeedInfo],
                                status: Status)
  final case class SubscribeResponse(response: String, status: Status)
  final case class FeedsResponse(feedsInfo: List[FeedInfo],
                                 status: Status)
  final case class AddUserResponse(response: String, status: Status)

  private var users = scala.collection.mutable.Map[String, List[String]] ()

  def apply(): Behavior[Command] = protocol()

  private def protocol(): Behavior[Command] = {
    Behaviors.receiveMessage {
      case Feed(url, maybeSince, replyTo) =>
        asyncRequest(url).onComplete {
          case Success(xmlElem) =>
            fromXml(xmlElem, maybeSince).onComplete {
              case Success(feed) =>
                replyTo ! FeedResponse(Some(feed), OK)

              case Failure(_) =>
                replyTo ! FeedResponse(None, NotFound)
            }

          case Failure(_) =>
            replyTo ! FeedResponse(None, NotFound)
        }
        Behaviors.same

      case Subscribe(username, url, replyTo) =>
          val userContains = users.contains(username)
          val urlContains = users.get(username).map(_.contains(url)).getOrElse(false)

        asyncRequest(url).onComplete {
          case Success(_) if (userContains && !urlContains) =>
            replyTo ! SubscribeResponse(s"Url: ${url} subscribed.", OK)
            users(username) ::= url

          case Success(_) if (userContains && urlContains) =>
            replyTo ! SubscribeResponse(s"Url: ${url} has already been subscribed.", OK)

          case Success(_) if (!userContains) =>
            replyTo ! SubscribeResponse(s"User: ${username} does not exists.", NotFound)

          case Failure(_) =>
            replyTo ! SubscribeResponse(s"Url: ${url} does not exists.", NotFound)

        }
        Behaviors.same

      case Feeds(username, maybeSince, replyTo) =>
        users.get(username) match {
          case Some(urls) =>
            val futureFeeds = Future.sequence(urls.map(asyncRequest))
                              .map(_.map(fromXml(_, maybeSince)))
                              .flatMap(Future.sequence(_))
            futureFeeds.onComplete {
              case Success(feeds) =>
                replyTo ! FeedsResponse(feeds, if (feeds.isEmpty) NotFound else OK)

              case Failure(_) =>
                replyTo ! FeedsResponse(List(), NotFound)
            }
          
          case None =>
            replyTo ! FeedsResponse(List(), NotFound)
        }
        Behaviors.same

      case AddUser(username, replyTo) =>
        users.get(username) match {
          case Some(_) =>
            replyTo ! AddUserResponse(
              s"User: ${username} has already been registered.", Conflict)
          
          case None =>
            replyTo ! AddUserResponse(s"User: ${username} registered.", OK)
            users += (username -> List.empty)
        }
        Behaviors.same
    }
  }

  def fromXml(elem: xml.Elem,
              maybeSince: Option[String]): Future[FeedInfo] = {
    Future {
      val isRSS = (elem \ "title").isEmpty

      val a = if (isRSS) (elem \ "channel") else elem
      val b = if (isRSS) "description" else "subtitle"
      val c = if (isRSS) "item" else "entry"
      val d = if (isRSS) "description" else "summary"

      val title = (a \ "title").text // title
      val description_subtitle = (a \ b).headOption.map(_.text) // description/subtitle
      val items_entrys = (a \\ c).map(item_entry =>
                         FeedItem((item_entry \ "title").text, // title
                                  (if (isRSS) (item_entry \ "link").text
                                   else (item_entry \ "link" \"@href").text), // link
                                  (item_entry \ d).headOption.map(_.text), // description/summary
                                  parseDate(isRSS, item_entry)) // pubDate/updated
                         ).toList // items/entrys

      val filtered = maybeSince match {
        case Some(since) =>
          items_entrys.filter(x => filterByPubDate(x.pubDate, since))

        case None =>
          items_entrys
      }

      if (isRSS)
        FeedInfo(Left(FeedRSS(title, description_subtitle, filtered)))
      else
        FeedInfo(Right(FeedATOM(title, description_subtitle, filtered.map(_.toEntry))))
    }
  }

  def parseDate(isRSS: Boolean, item_entry: NodeSeq): String = {
    isRSS match {
      case true =>
        LocalDateTime.parse((item_entry \ "pubDate").text,
                            RFC_1123_DATE_TIME).toString // pubDate
      case false =>
        val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault())
        LocalDateTime.parse((item_entry \ "updated").text, formatter).toString; // updated
    }
  }

  def filterByPubDate(date: String, since: String): Boolean = {
    val parsedDate = LocalDateTime.parse(date, ISO_LOCAL_DATE_TIME)
    val parsedSince = LocalDateTime.parse(since, ISO_LOCAL_DATE_TIME)
    parsedDate.isAfter(parsedSince)
  }

  def asyncRequest(path: String): Future[xml.Elem] = {
    import dispatch._, Defaults._
    dispatch.Http.default(dispatch.url(path) OK dispatch.as.xml.Elem)
  }
}