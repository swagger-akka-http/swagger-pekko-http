/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.swagger.pekko

import org.apache.pekko.http.scaladsl.model.{HttpEntity, MediaTypes}
import org.apache.pekko.http.scaladsl.server.{Directives, PathMatchers, Route}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.swagger.pekko.model.{Info, asScala}
import com.github.swagger.scala.converter.SwaggerScalaModelConverter
import io.swagger.v3.core.util.{Json31, Yaml, Yaml31}
import io.swagger.v3.jaxrs2.Reader
import io.swagger.v3.oas.integration.SwaggerConfiguration
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.{Components, ExternalDocumentation, OpenAPI, SpecVersion}
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable.{ListBuffer, Map => MutableMap}
import scala.util.control.NonFatal

object SwaggerHttpService {

  def readerConfig = new SwaggerConfiguration

  def removeInitialSlashIfNecessary(path: String): String =
    if(path.startsWith("/")) removeInitialSlashIfNecessary(path.substring(1)) else path
  def removeTrailingSlashIfNecessary(path: String): String =
    if(path.endsWith("/")) removeTrailingSlashIfNecessary(path.substring(0, path.length)) else path
  def prependSlashIfNecessary(path: String): String  = if(path.startsWith("/")) path else s"/$path"

  private[pekko] def apiDocsBase(path: String) = PathMatchers.separateOnSlashes(removeInitialSlashIfNecessary(path))
  private[pekko] val logger = LoggerFactory.getLogger(classOf[SwaggerHttpService])

  /**
   * The Jackson mappers used to write the generated document. They are built from swagger-scala-module's
   * [[SwaggerScalaModelConverter.createObjectMapper]] rather than swagger-core's global `Json.mapper()`: the module no
   * longer registers its Scala module on that mapper, and its own copy also carries any customizer registered via
   * `SwaggerScalaModelConverter.setObjectMapperCustomizer`, so Scala values that end up in the document (vendor
   * extensions, examples, defaults) serialize the same way the model was introspected. The YAML mappers are the same
   * mappers with swagger-core's YAML factory, which is the only difference between `Json.mapper()` and `Yaml.mapper()`.
   *
   * `createObjectMapper()` is based on `Json.mapper()`, whose mixins and serializers are OpenAPI 3.0 specific, so the
   * 3.1 mappers add the Scala module to `Json31.mapper()` themselves and do not see the customizer.
   */
  private lazy val jsonMapper: ObjectMapper = SwaggerScalaModelConverter.createObjectMapper()
  private lazy val json31Mapper: ObjectMapper = Json31.mapper().copy().registerModule(DefaultScalaModule)
  private lazy val yamlMapper: ObjectMapper = jsonMapper.copyWith(Yaml.mapper().getFactory.copy())
  private lazy val yaml31Mapper: ObjectMapper = json31Mapper.copyWith(Yaml31.mapper().getFactory.copy())

  private[pekko] def objectMapper(specVersion: SpecVersion): ObjectMapper =
    if (specVersion == SpecVersion.V31) json31Mapper else jsonMapper
  private[pekko] def yamlObjectMapper(specVersion: SpecVersion): ObjectMapper =
    if (specVersion == SpecVersion.V31) yaml31Mapper else yamlMapper

  /**
   * swagger-core's `Reader` deep-copies the configured `OpenAPI` through its global `Json.mapper()`, which knows
   * nothing about Scala types, so values that reach it from Scala are first turned into Jackson trees with the
   * Scala-aware mapper. A tree serializes the same way with any mapper.
   */
  private[pekko] def toJsonNode(specVersion: SpecVersion, value: AnyRef): JsonNode =
    objectMapper(specVersion).valueToTree[JsonNode](value)
}

trait SwaggerGenerator {
  import SwaggerHttpService._
  def apiClasses: Set[Class[_]]

  /**
   * @return the host (and possibly, port) for the API (eg `api.example.com` or `localhost:8080`)
   *         - used in conjunction with the [[schemes]] - this is ignored if you have non-empty [[serverURLs]]
   */
  def host: String = ""

  /**
   * @return list of URL schemes (default is `["http"]`) - this is ignored if you have non-empty [[serverURLs]]
   */
  def schemes: List[String] = List("http")

  /**
   * @return list of URLs (as strings) - if this list is empty, then the values are derived using the [[SwaggerGenerator.host]] and [[schemes]]
   */
  def serverURLs: Seq[String] = Seq.empty

  def basePath: String = ""
  def apiDocsPath: String = "api-docs"
  def info: Info = Info()
  def components: Option[Components] = None
  def security: List[SecurityRequirement] = List.empty
  def securitySchemes: Map[String, SecurityScheme] = Map.empty
  def externalDocs: Option[ExternalDocumentation] = None
  def vendorExtensions: Map[String, Object] = Map.empty
  def unwantedDefinitions: Seq[String] = Seq.empty
  def specVersion: SpecVersion = SpecVersion.V30

  def swaggerConfig: OpenAPI = {
    val swagger = new OpenAPI()
    val sv = specVersion
    swagger.setSpecVersion(sv)
    val version = if (sv == SpecVersion.V31) "3.1.0" else "3.0.1"
    swagger.setOpenapi(version)
    import model._
    swagger.setInfo(info)
    components.foreach { c => swagger.setComponents(c) }

    val path = removeInitialSlashIfNecessary(basePath)
    serverURLs match {
      case Seq() => {
        val hostPath = if (StringUtils.isNotBlank(path)) {
          s"${removeTrailingSlashIfNecessary(host)}/${path}/"
        } else {
          host
        }
        schemes.foreach { scheme =>
          swagger.addServersItem(new Server().url(s"${scheme.toLowerCase}://$hostPath"))
        }
        if (schemes.isEmpty && StringUtils.isNotBlank(hostPath)) {
          swagger.addServersItem(new Server().url(hostPath))
        }
      }
      case urlSeq => {
        urlSeq.foreach { url =>
          val urlPath = if (StringUtils.isNotBlank(path)) {
            s"${removeTrailingSlashIfNecessary(url)}/${path}/"
          } else {
            url
          }
          swagger.addServersItem(new Server().url(urlPath))
        }
      }
    }

    securitySchemes.foreach { case (k: String, v: SecurityScheme) => swagger.schemaRequirement(k, v) }
    swagger.setSecurity(asJavaMutableList(security))
    swagger.extensions(asJavaMutableMap(vendorExtensions.map { case (k, v) => k -> toJsonNode(specVersion, v) }))

    externalDocs.foreach { ed => swagger.setExternalDocs(ed) }
    swagger
  }

  def reader = {
    val config = readerConfig
    if (specVersion == SpecVersion.V31) {
      config.setOpenAPI31(true)
    }
    new Reader(config.openAPI(swaggerConfig))
  }

  def generateSwaggerJson: String = {
    try {
      objectMapper(specVersion).writerWithDefaultPrettyPrinter().writeValueAsString(filteredSwagger)
    } catch {
      case NonFatal(t) => {
        logger.error("Issue with creating swagger.json", t)
        throw t
      }
    }
  }

  def generateSwaggerYaml: String = {
    try {
      yamlObjectMapper(specVersion).writerWithDefaultPrettyPrinter().writeValueAsString(filteredSwagger)
    } catch {
      case NonFatal(t) => {
        logger.error("Issue with creating swagger.yaml", t)
        throw t
      }
    }
  }

  private[pekko] def asJavaMutableList[T](list: List[T]) = {
    (new ListBuffer[T] ++ list).asJava
  }

  private[pekko] def asJavaMutableMap[K, V](map: Map[K, V]) = {
    (MutableMap.empty[K, V] ++ map).asJava
  }

  private[pekko] def filteredSwagger: OpenAPI = {
    val swagger: OpenAPI = reader.read(apiClasses.asJava)
    Option(swagger.getComponents) match {
      case Some(components) => {
        if (!unwantedDefinitions.isEmpty) {
          val filteredSchemas = asJavaMutableMap(asScala(components.getSchemas).filterKeys(
            definitionName => !unwantedDefinitions.contains(definitionName)).toMap)
          components.setSchemas(filteredSchemas)
        }
      }
      case _ =>
    }
    swagger
  }
}

trait SwaggerHttpService extends Directives with SwaggerGenerator {
  import SwaggerHttpService._
  def routes: Route = {
    val base = apiDocsBase(apiDocsPath)
    path(base / "swagger.json") {
      get {
        complete(HttpEntity(MediaTypes.`application/json`, generateSwaggerJson))
      }
    } ~
    path(base / "swagger.yaml") {
      get {
        complete(HttpEntity(CustomMediaTypes.`text/vnd.yaml`, generateSwaggerYaml))
      }
    }
  }
}
