package feedaggregator.server

import feedaggregator.actorsystem.Manager

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.io.StdIn


object FeedAggregatorServer {
  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

    val bindingFuture = Http().bindAndHandle(routes, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  def main(args: Array[String]): Unit = {
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val managerActor = context.spawn(Manager(), "Manager")
      val routes = new Routes(managerActor)(context.system)

      startHttpServer(routes.route, context.system)
      Behaviors.empty
    }

    val system = ActorSystem[Nothing](rootBehavior, "FeedAggregatorServer")
  }
}