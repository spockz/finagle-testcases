package finagle.tests.retry

import java.util.concurrent.atomic.AtomicInteger

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.service.{RetryFilter, SimpleRetryPolicy, TimeoutFilter}
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Failure, IndividualRequestTimeoutException, RequestTimeoutException, Service}
import com.twitter.util._
import org.scalatest.{FlatSpec, Matchers}

class RestRetryPolicyIT extends FlatSpec with Matchers {

  behavior of "RestRetryPolicyFilter"

  it should "not retry requests if the global request was timed-out/cancelled" in {
    val counter = new AtomicInteger()
    val duration = Duration.fromSeconds(1)
    implicit val timer: Timer = DefaultTimer.twitter
    val response = Future.exception(new RequestTimeoutException(duration, "Timeout")).delayed(duration)(timer)

    val bareService: Service[Request, Response] = Service.mk { req =>
      counter.incrementAndGet()
      response
    }

    val service: Service[Request, Response] =
      new TimeoutFilter[Request, Response](Duration.fromMilliseconds(1), timer)
        .andThen(new RetryFilter[Request, Response](new SimpleRetryPolicy[(Request, Try[Response])]() {

          override def backoffAt(retry: Int): Duration = Duration.fromMilliseconds(10)

          override def shouldRetry(a: (Request, Try[Response])): Boolean = a match {
            case (_, Throw(f: Failure)) if f.isFlagged(Failure.Interrupted) =>
              false
            case _                                                          => true
          }
        }, timer, NullStatsReceiver))
        .andThen(bareService)

    val serviceResponse = service(Request()).delayed(Duration.fromSeconds(5))(timer)
    serviceResponse.raiseWithin(Duration.fromMilliseconds(1))
    an[IndividualRequestTimeoutException] should be thrownBy {
      Await.result(serviceResponse)
    }
    counter.get() should be(1)
  }
}