package com.gicsports.api.http

import akka.NotUsed
import akka.http.scaladsl.marshalling.{ToResponseMarshallable, ToResponseMarshaller}
import akka.http.scaladsl.server.Directives.{complete, handleExceptions}
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.scaladsl.Source
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

class RouteTimeout(timeout: FiniteDuration)(implicit sc: Scheduler) extends ApiMarshallers {
  private val handler = ExceptionHandler { case _: TimeoutException =>
    complete(ApiError.ServerRequestTimeout)
  }

  def executeToFuture[T](task: Task[T])(implicit m: ToResponseMarshaller[T]): Route =
    execute(task)(_.runToFuture(_))

  def executeStreamed[T, R](task: Task[Seq[T]])(f: T => R)(implicit m: ToResponseMarshaller[Source[R, NotUsed]]): Route =
    execute(task) { (task, sc) =>
      task
        .runToFuture(sc)
        .map(Source(_).map(f))(sc)
    }

  def execute[T](task: Task[T])(f: (Task[T], Scheduler) => ToResponseMarshallable): Route =
    handleExceptions(handler) & complete(f(task.timeout(timeout), sc))
}
