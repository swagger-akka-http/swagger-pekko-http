package com.github.swagger.pekko.samples

import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.stream.ActorMaterializer
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.ToResponseMarshallable.apply
import org.apache.pekko.http.scaladsl.server.Directive.addByNameNullaryApply
import org.apache.pekko.http.scaladsl.server.Directive.addDirectiveApply
import jakarta.ws.rs.Path
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse

//@Api(value = "/dict", description = "This is a dictionary api.")
@Path("/dict")
trait DictHttpService
    extends Directives
    with ModelFormats {
  implicit val actorSystem: ActorSystem = ActorSystem("mysystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val me = DictEntry("", "", None)

  val yoyo = as[DictEntry]

  var dict: Map[String, String] = Map[String, String]()

  @Operation(summary = "Add dictionary entry.",
    description = "Will add new entry to the dictionary, indexed by key, with an optional expiration value",
    method = "POST",
    requestBody = new RequestBody(required = true,
      description = "Key/Value pair of dictionary entry, with optional expiration time.",
      content = Array(new Content(schema = new Schema(implementation = classOf[DictEntry])))),
    responses = Array(new ApiResponse(responseCode = "400", description = "Client Error"))
  )
  def createRoute = post {
    path("/dict") {
      entity(as[DictEntry]) { e ⇒
        dict += e.key -> e.value
        complete("ok")
      }
    }
  }

  @Operation(summary = "Find entry by key.",
    description = "Will look up the dictionary entry for the provided key.",
    method = "GET",
    parameters = Array(new Parameter(name = "key", in = ParameterIn.PATH, required = true,
      description = "Keyword for the dictionary entry.")),
    responses = Array(
      new ApiResponse(responseCode = "200",
        content = Array(new Content(schema = new Schema(implementation = classOf[DictEntry])))),
      new ApiResponse(responseCode = "404", description = "Dictionary does not exist."))
  )
  def readRoute = get {
    path("/dict" / Segment) { key ⇒
      complete(dict(key))
    }
  }

}
