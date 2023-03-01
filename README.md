![Build Status](https://github.com/swagger-akka-http/swagger-pekko-http/actions/workflows/ci.yml/badge.svg)
[![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/com.github.swagger-akka-http/swagger-pekko-http_2.13.svg)](https://oss.sonatype.org/content/repositories/snapshots/com/github/swagger-akka-http/swagger-akka-http_2.13/)
<!--
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/swagger-akka-http/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.swagger-akka-http/swagger-akka-http_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.swagger-akka-http/swagger-akka-http_2.13)
[![codecov.io](https://codecov.io/gh/swagger-akka-http/swagger-akka-http/coverage.svg?branch=main)](https://codecov.io/gh/swagger-akka-http/swagger-akka-http/branch/main)
-->
# swagger-pekko-http

Swagger-Pekko-Http brings [Swagger](https://swagger.io/swagger-core/) support for [Pekko-Http](https://github.com/apache/incubator-pekko-http) Apis. The included `SwaggerHttpService` route will inspect Scala types with Swagger annotations and build a swagger compliant endpoint for a [swagger compliant ui](https://petstore.swagger.io/).

This is a fork of [Swagger-Akka-Http](https://github.com/swagger-akka-http/swagger-akka-http).
If you are switching over from Swagger-Akka-Http and are using an old version, please upgrade first before switching to Swagger-Pekko-Http.

The [OpenAPI spec](https://swagger.io/specification/) is helpful for understanding the swagger api and resource declaration semantics behind swagger-core annotations.

## Getting Swagger-Pekko-Http

### Release Version

The jars are hosted on [sonatype](https://oss.sonatype.org) and mirrored to Maven Central. Snapshot releases are also hosted on sonatype. 

```sbt
libraryDependencies += "com.github.swagger-akka-http" %% "swagger-pekko-http" % "<release-version>"
```

## Examples

[pjfanning/swagger-pekko-http-sample](https://github.com/pjfanning/swagger-pekko-http-sample) is a simple sample using this project.

The `/test` directory includes an `HttpSwaggerServiceSpec` which uses `pekko-http-testkit` to test the API. It uses a `PetHttpService` and `UserHttpService` declared in the `/samples` folder. 

## SwaggerHttpService

The `SwaggerHttpService` is a trait extending Pekko-Http's `HttpService`. It will generate the appropriate Swagger json schema based on a set of inputs declaring your Api and the types you want to expose.

The `SwaggerHttpService` contains a `routes` property you can concatenate along with your existing pekko-http routes. This will expose an endpoint at `<baseUrl>/<specPath>/<resourcePath>` with the specified `apiVersion`, `swaggerVersion` and resource listing.

The service requires a set of `apiTypes` and `modelTypes` you want to expose via Swagger. These types include the appropriate Swagger annotations for describing your api. The `SwaggerHttpService` will inspect these annotations and build the appropriate Swagger response.

Here's an example `SwaggerHttpService` snippet which exposes [Swagger's PetStore](https://petstore.swagger.io/) resources, `Pet`, `User` and `Store`. The routes property can be concatenated to your other route definitions:

```scala
object SwaggerDocService extends SwaggerHttpService {
  override val apiClasses: Set[Class[_]] = Set(classOf[PetService], classOf[UserService], classOf[StoreService])
  override val host = "localhost:8080" //the url of your api, not swagger's json endpoint
  override val apiDocsPath = "api-docs" //where you want the swagger-json endpoint exposed
  override val info = Info() //provides license and other description details
}.routes
```

## Java DSL SwaggerGenerator

See [pjfanning/swagger-akka-http-sample-java](https://github.com/pjfanning/swagger-akka-http-sample-java) for an Akka based demo application.

```java
import com.github.swagger.pekko.javadsl.SwaggerGenerator;
class MySwaggerGenerator extends SwaggerGenerator {
  @Override
  public Set<Class<?>> apiClasses() {
    return Collections.singleton(PetService.class);
  }
  
  @Override
  public String host() {
    return "localhost:8080"; //the url of your api, not swagger's json endpoint
  }

  @Override
  public String apiDocsPath() {
    return "api-docs";  //where you want the swagger-json endpoint exposed
  }

  @Override
  public Info info() {
    return new io.swagger.models.Info();  //provides license and other description details
  }
}
```

## Adding Swagger Annotations

Apache Pekko Http routing works by concatenating various routes, built up by directives, to produce an api. The [routing dsl](https://doc.akka.io/docs/akka-http/current/scala/http/introduction.html#routing-dsl-for-http-servers) is an elegant way to describe an api and differs from the more common class and method approach of other frameworks. But because Swagger's annotation library requires classes, methods and fields to describe an Api, one may find it difficult to annotate a pekko-http routing application.

A simple solution is to break apart a pekko-http routing application into various resource traits, with methods for specific api operations, joined by route concatentation into a route property. These traits with can then be joined together by their own route properties into a complete api. Despite losing the completeness of an entire api the result is a more modular application with a succint resource list. The balance is up to the developer but for a reasonably-sized applicaiton organizing routes across various traits is probably a good idea.

With this structure you can apply `@Api` annotations to these individual traits and `@ApiOperation` annotations to methods.

You can also use jax-rs `@Path` annotations alongside `@ApiOperation`s if you need fine-grained control over path specifications or if you want to support multiple paths per operation. The functionality is the same as swagger-core.

### Resource Definitions

The swagger 2.0 annotations are very different from those used in swagger 1.5.

The general pattern for resource definitions and pekko-http routes:

* Place an individual resource in its own trait
* Define specific api operations with `def` methods which produce a route
* Annotate these methods with `@Operation`, `@Parameter` and `@ApiResponse` accordingly
* Concatenate operations together into a single routes property, wrapped with a path directive for that resource
* Concatenate all resource traits together on their routes property to produce the final route structure for your application.

Here's what Swagger's *pet* resource would look like:

```scala
trait PetHttpService extends HttpService {

  @Operation(summary = "Find a pet by ID",
    description = "Returns a pet based on ID",
    method = "GET",
    parameters = Array(
      new Parameter(name = "petId", in = ParameterIn.PATH, required = true, description = "ID of pet that needs to be fetched",
        content = Array(new Content(schema = new Schema(implementation = classOf[Int], allowableValues = Array("[1,100000]")))))
    ),
    responses = Array(
      new ApiResponse(responseCode = "400", description = "Invalid ID supplied"),
      new ApiResponse(responseCode = "404", description = "Pet not found")
    )
  )
  def petGetRoute = get { path("pet" / IntNumber) { petId =>
    complete(s"Hello, I'm pet ${petId}!")
    }
  }
}
```

### Schema Definitions

Schema definitions are fairly self-explanatory. You can use swagger annotations to try to adjust the model generated for a class.
Due to type erasure, the `Option[Boolean]` will normally treated as `Option[Any]` but the schema annotation corrects this.
This type erasure affects primitives like Int, Long, Boolean, etc.

```scala
case class ModelWOptionBooleanSchemaOverride(@Schema(implementation = classOf[Boolean]) optBoolean: Option[Boolean])
```

## Swagger UI

This library does not include [Swagger's UI](https://petstore.swagger.io/) only the API support for powering a UI. Adding such a UI to your pekko-http app is easy.

You can include the static files for the Swagger UI and expose using pekko-http's `getFromResource` and `getFromResourceDirectory` [support](https://doc.akka.io/docs/akka-http/current/scala/http/routing-dsl/directives/alphabetically.html).

To add a Swagger UI to your site, simply drop the static site files into the resources directory of your project. The following trait will expose a `swagger` route hosting files from the `resources/swagger/` directory: 

```scala
trait Site extends Directives {
  val site =
    path("swagger") { getFromResource("swagger/index.html") } ~
      getFromResourceDirectory("swagger")
}
```

You can then mix this trait with a new or existing Pekko-Http class with an `actorRefFactory` and concatenate the `site` route value to your existing route definitions.

## How Annotations are Mapped to Swagger

* [Swagger 2 Annotations Guide](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations)
* [Swagger 1.5 Annotations Guide](https://github.com/swagger-api/swagger-core/wiki/Annotations-1.5.X)
