/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import com.advancedtelematic.data.Codecs._
import com.advancedtelematic.data.DataType.{Ref, RefName}
import com.advancedtelematic.libats.auth.NamespaceDirectives.nsHeader
import com.advancedtelematic.libats.codecs.CirceCodecs.refinedEncoder
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object Requests {
  sealed case class ImageRequest(commit: Commit, refName: RefName, description: String, pullUri: String)
  object ImageRequest {
    implicit val imageRequestEncoder: Encoder[ImageRequest] = deriveEncoder
  }
}

class CoreHttpClient(baseUri: Uri, packagesUri: Uri, treeHubUri: Uri)
                    (implicit system: ActorSystem, mat: ActorMaterializer) extends Core {
  import HttpMethods._
  import Requests._

  private val http = akka.http.scaladsl.Http()

  def publishRef(ref: Ref, description: String)
                (implicit ec: ExecutionContext): Future[Unit] = {
    val fileContents = ImageRequest(ref.value, ref.name, description, treeHubUri.toString).asJson.noSpaces
    val bodyPart = BodyPart.Strict("file", HttpEntity(fileContents), Map("fileName" -> ref.name.get))
    val formattedRefName = ref.name.get.replaceFirst("^heads/", "").replace("/", "-")
    val uri = baseUri.withPath(packagesUri.path + s"/treehub-$formattedRefName/${ref.value.value}")
                     .withQuery(Query("description" -> description))
    val req = HttpRequest(method = PUT, uri = uri, entity = Multipart.FormData(bodyPart).toEntity())

    execHttp[Unit](req.addHeader(nsHeader(ref.namespace)))
  }

  private def execHttp[T](httpRequest: HttpRequest)
                         (implicit unmarshaller: Unmarshaller[ResponseEntity, T], ec: ExecutionContext,
                          ct: ClassTag[T]): Future[T] =
    http.singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case status if status.isSuccess() && ct.runtimeClass == classOf[Unit] =>
          Future.successful(()).asInstanceOf[Future[T]]
        case status if status.isSuccess() => unmarshaller(response.entity)
        case err => FastFuture.failed(new Exception(err.toString))
      }
    }
}
