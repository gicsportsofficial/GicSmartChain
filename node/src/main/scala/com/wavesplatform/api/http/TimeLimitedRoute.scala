package com.gicsports.api.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.{Directive1, ExceptionHandler, Route}
import com.google.common.util.concurrent.{ExecutionError, UncheckedExecutionException}
import com.gicsports.utils.Schedulers.ExecutorExt
import monix.execution.Scheduler

import scala.concurrent.ExecutionException

trait TimeLimitedRoute { self: ApiRoute =>
  def limitedScheduler: Scheduler

  def executeLimited[T](f: => T): Directive1[T] = {
    val handler = ExceptionHandler { case _: InterruptedException | _: ExecutionException | _: ExecutionError | _: UncheckedExecutionException =>
      complete(ApiError.CustomValidationError("The request took too long to complete"))
    }
    handleExceptions(handler) & onSuccess(limitedScheduler.executeCatchingInterruptedException(f))
  }

  def completeLimited(f: => ToResponseMarshallable): Route =
    executeLimited(f)(complete(_))
}
