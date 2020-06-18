package cromwell.pipeline.controller

import java.nio.file.Paths

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.Complete
import cromwell.pipeline.datastorage.dto.{ FileContent, PipelineVersion, ProjectId, ProjectUpdateFileRequest }
import cromwell.pipeline.datastorage.utils.auth.AccessTokenContent
import cromwell.pipeline.service.{ ProjectFileService, ProjectService }
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

class ProjectFileController(wdlService: ProjectFileService, projectService: ProjectService)(
  implicit val executionContext: ExecutionContext
) {
  val route: AccessTokenContent => Route = _ =>
    concat(
      path("files" / "validation") {
        post {
          entity(as[FileContent]) { request =>
            onComplete(wdlService.validateFile(request)) {
              case Success(Left(e)) => complete(StatusCodes.Conflict, e.errors)
              case Success(_)       => complete(StatusCodes.OK)
              case Failure(e)       => complete(StatusCodes.InternalServerError, e.getMessage)
            }
          }
        }
      },
      path("files") {
        concat(
          get {
            parameter('projectId.as[String], 'path.as[String], 'version.as[String]) {
              (projectId, path, version) =>
                onComplete(projectService.getProjectById(ProjectId(projectId))
                  .map(someProject =>
                    someProject
                      .map(project =>
                        wdlService.getFile(project, Paths.get(path), Some(PipelineVersion(version)))
                          .map(response => response)
                      )
                  )) {

                  case Success(Left(e)) => complete(StatusCodes.ImATeapot, e.getMessage)
                  case Success(_) => complete(StatusCodes.OK)
                  case Failure(e) => complete(StatusCodes.InternalServerError, e.getMessage)
                }
            }
          },
          post {
            entity(as[ProjectUpdateFileRequest]) { request =>
              onComplete(wdlService.uploadFile(request.project, request.projectFile, request.version)) {
                case Success(Left(e)) => complete(StatusCodes.ImATeapot, e.getMessage) // TODO: change status code
                case Success(_)       => complete(StatusCodes.OK)
                case Failure(e)       => complete(StatusCodes.InternalServerError, e.getMessage)
              }
            }
          }
        )
      }
    )
}
