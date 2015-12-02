package app.spray

import akka.actor.Actor
import spray.http.StatusCodes._
import spray.routing._
import spray.http._
import MediaTypes._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}


case class MyCookieValidationRejection(ck: HttpCookie) extends Rejection


// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

  implicit val myRejectionHandler = RejectionHandler {
    case MalformedQueryParamRejection(name, msg, _) :: _ =>
      respondWithMediaType(`application/json`)(
        complete(BadRequest, s"""{"error":{"message":"The query parameter $name was malformed: "$msg"}}""")
      )
    case MyCookieValidationRejection(ck) :: _ =>
      respondWithMediaType(`application/json`)(
        complete(BadRequest, s"""{"error": {"message":"My cookie value was malformed:{name: ${ck.name}; value: ${ck.content}"}}""")
      )
    case MissingCookieRejection(cookieName) :: _ ⇒
      respondWithMediaType(`application/json`)(
        complete(BadRequest, s"""{"error": {"message":"Request is missing required cookie: $cookieName"}}""")
      )
  }

  val myRoute =
    path("") {
      get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html>
              <body>
                <h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
              </body>
            </html>
          }
        }
      }
    } ~
    path("session") {
      get {
        cookie("session_id") { session_id =>
          respondWithMediaType(`text/html`) {
            // XML is marshalled to `text/xml` by default, so we simply override here
            complete {
              session_id match {
                case HttpCookie(_,_,_,_,_,_,_,_,_) =>
                  <html>
                    <body>
                      <h1>Session OK!</h1>
                    </body>
                  </html>
                case _ =>
                  <html>
                    <body>
                      <h1>Session None</h1>
                    </body>
                  </html>
              }
            }
          }
        }
      }
    } ~
    path("session2") {
      get {
        cookie("session_id") { session_id =>
          Option(session_id) match
          {
            case Some(_) =>
                respondWithMediaType(`text/html`) {
                  complete {
                    <html>
                      <body>
                        <h1>
                          Session OK! {session_id}
                        </h1>
                      </body>
                    </html>
                  }
                }
            case _ =>  reject(MyCookieValidationRejection(session_id))
          }
        }
      }
    }
}