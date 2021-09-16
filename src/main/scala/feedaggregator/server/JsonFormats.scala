package feedaggregator.server

import spray.json.DefaultJsonProtocol._


object JsonFormats {
  final case class Subscription(username: String, url: Option[String])

  final case class FeedItem(title: String,
                            link: String,
                            description: Option[String],
                            pubDate: String) {
    def toEntry = FeedEntry(title, link, description, pubDate)
  }
  final case class FeedRSS(title: String,
                           description: Option[String],
                           items: List[FeedItem])

  final case class FeedEntry(title: String,
                             link: String,
                             summary: Option[String],
                             updated: String)
  final case class FeedATOM(title: String,
                            subtitle: Option[String],
                            entrys: List[FeedEntry])

  final case class FeedInfo(feed: Either[FeedRSS, FeedATOM])

  implicit val subscription = jsonFormat2(Subscription)
  implicit val feedItem = jsonFormat4(FeedItem)
  implicit val feedRSS = jsonFormat3(FeedRSS)
  implicit val feedEntry = jsonFormat4(FeedEntry)
  implicit val feedATOM = jsonFormat3(FeedATOM)
  implicit val feedInfo = jsonFormat1(FeedInfo)
}