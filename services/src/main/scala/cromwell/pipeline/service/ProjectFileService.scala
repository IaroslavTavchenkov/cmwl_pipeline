package cromwell.pipeline.service

import java.nio.file.Path

import cromwell.pipeline.datastorage.dto._
import cromwell.pipeline.womtool.WomToolAPI

import scala.concurrent.{ ExecutionContext, Future }

class ProjectFileService(womTool: WomToolAPI, projectVersioning: ProjectVersioning[VersioningException])(
  implicit executionContext: ExecutionContext
) {

  def getFile(
    projectId: ProjectId,
    path: Path,
    version: Option[PipelineVersion]
  ): Future[Either[VersioningException, ProjectFile]] =
    projectVersioning.getFile(projectId, path, version)

  def validateFile(fileContent: FileContent): Future[Either[ValidationError, Unit]] =
    Future(womTool.validate(fileContent.content)).map {
      case Left(value) => Left(ValidationError(value.toList))
      case Right(_)    => Right(())
    }

  def uploadFile(
    project: Project,
    projectFile: ProjectFile,
    version: Option[PipelineVersion]
  ): Future[Either[VersioningException, String]] =
    projectVersioning.updateFile(project, projectFile, version)
}
