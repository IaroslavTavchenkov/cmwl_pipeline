package cromwell.pipeline.service

import java.net.URLEncoder
import java.nio.file.Path

import cromwell.pipeline.datastorage.dto.File.UpdateFileRequest
import cromwell.pipeline.datastorage.dto.{ GitLabVersion, PipelineVersion, Project, ProjectFile, Repository }
import cromwell.pipeline.utils.{ GitLabConfig, HttpStatusCodes }
import play.api.libs.json.{ JsError, JsResult, JsSuccess, Json }

import scala.concurrent.{ ExecutionContext, Future }

class GitLabProjectVersioning(httpClient: HttpClient, config: GitLabConfig)
    extends ProjectVersioning[VersioningException] {

  override def updateFile(project: Project, projectFile: ProjectFile, version: Option[PipelineVersion])(
    implicit ec: ExecutionContext
  ): AsyncResult[String] = {
    val path = URLEncoder.encode(projectFile.path.toString, "UTF-8")
    val repositoryId: Repository = project.repository match {
      case Some(repository) => repository
      case None             => throw VersioningException(s"No repository for project: $project")
    }
    val fileUrl = s"${config.url}projects/${repositoryId.value}/repository/files/$path"

    getProjectVersions(project).flatMap(versions => {
      val latestVersion: Either[VersioningException, PipelineVersion] = (version, versions) match {
        case (Some(_), Left(error))                              => Left(error)
        case (Some(tagName), Right(v :: _)) if v.name <= tagName => Right(tagName)
        case (Some(tagName), Right(v :: _)) if v.name > tagName =>
          Left(VersioningException(s"Your version $tagName is out of date. Current version of project: ${v.name}"))
        case (Some(_), Right(_))   => Right(PipelineVersion(config.defaultFileVersion))
        case (None, Right(v :: _)) => Right(v.name)
        case (None, Right(Nil))    => Right(PipelineVersion(config.defaultFileVersion))
        case (None, Left(error))   => Left(error)
      }
      latestVersion match {
        case Left(error) =>
          Future.successful(Left(error))
        case Right(tagName) =>
          val payload =
            Json.stringify(Json.toJson(UpdateFileRequest(projectFile.content, tagName.toString, config.defaultBranch)))
          for {
            updateResult <- httpClient.put(fileUrl, payload = payload, headers = config.token)
            createResult <- httpClient.post(fileUrl, payload = payload, headers = config.token)
            tagResult <- createTag(repositoryId, tagName)
          } yield (updateResult, createResult, tagResult) match {
            case (Response(HttpStatusCodes.OK, _, _), Response(HttpStatusCodes.BadRequest, _, _), Right(_)) =>
              Right("Success update file")
            case (Response(HttpStatusCodes.BadRequest, _, _), Response(HttpStatusCodes.OK, _, _), Right(_)) =>
              Right("Success create file")
            case (_, _, Left(error))          => Left(error)
            case (_, Response(_, body, _), _) => Left(VersioningException(body))
          }
      }
    })
  }

  private def createTag(projectId: Repository, version: PipelineVersion)(
    implicit ec: ExecutionContext
  ): AsyncResult[String] = {
    val tagUrl = s"${config.url}projects/${projectId.value}/repository/tags"
    httpClient
      .post(
        tagUrl,
        params = Map("tag_name" -> version.name, "ref" -> config.defaultBranch),
        headers = config.token,
        payload = ""
      )
      .map {
        case Response(HttpStatusCodes.OK, _, _) => Right("Tag was added")
        case Response(_, body, _)               => Left(VersioningException(body))
      }
  }

  override def updateFiles(project: Project, projectFiles: ProjectFiles)(
    implicit ec: ExecutionContext
  ): AsyncResult[List[String]] = ???

  override def createRepository(project: Project)(implicit ec: ExecutionContext): AsyncResult[Project] =
    if (!project.active)
      Future.failed(VersioningException("Could not create a repository for deleted project."))
    else {
      val createRepoUrl: String = s"${config.url}projects"
      httpClient
        .post(url = createRepoUrl, headers = config.token, payload = Json.stringify(Json.toJson(project)))
        .map(
          resp =>
            if (resp.status != HttpStatusCodes.Created)
              Left(VersioningException(s"The repository was not created. Response status: ${resp.status}"))
            else Right(project.withRepository(Some(s"${config.idPath}${project.projectId.value}")))
        )
        .recover { case e: Throwable => Left(VersioningException(e.getMessage)) }
    }

  override def deleteFile(
    project: Project,
    path: Path,
    branchName: String = config.defaultBranch,
    commitMessage: String
  )(
    implicit ec: ExecutionContext
  ): AsyncResult[String] = {

    val filePath: String = URLEncoder.encode(path.toString, "UTF-8")
    val deleteMessage: String = s"$filePath file has been deleted from $branchName"

    httpClient
      .delete(
        s"${config.url}/projects/${project.repository}/repository/files/$filePath/raw",
        config.token
      )
      .map { resp =>
        resp.status match {
          case HttpStatusCodes.OK => Right(deleteMessage)
          case _                  => Left(VersioningException(s"Exception. Response status: ${resp.status}"))
        }
      }
      .recover { case e: Throwable => Left(VersioningException(e.getMessage)) }
  }

  override def getFiles(project: Project, path: Path)(implicit ec: ExecutionContext): AsyncResult[List[String]] = ???

  override def getProjectVersions(project: Project)(implicit ec: ExecutionContext): AsyncResult[Seq[GitLabVersion]] = {
    val versionsListUrl: String = s"${config.url}projects/${project.repository.get.value}/repository/tags"
    httpClient
      .get(url = versionsListUrl, headers = config.token)
      .map(
        resp =>
          if (resp.status != HttpStatusCodes.OK)
            Left(VersioningException(s"Could not take versions. Response status: ${resp.status}"))
          else {
            val parsedVersions: JsResult[Seq[GitLabVersion]] = Json.parse(resp.body).validate[List[GitLabVersion]]
            parsedVersions match {
              case JsSuccess(value, _) => Right(value)
              case JsError(errors)     => Left(VersioningException(s"Could not parse GitLab response. (errors: $errors)"))
            }
          }
      )
      .recover { case e: Throwable => Left(VersioningException(e.getMessage)) }
  }

  override def getFileVersions(project: Project, path: Path)(
    implicit ec: ExecutionContext
  ): AsyncResult[List[GitLabVersion]] = ???

  override def getFilesVersions(project: Project, path: Path)(
    implicit ec: ExecutionContext
  ): AsyncResult[List[GitLabVersion]] = ???

  override def getFileTree(project: Project, version: Option[PipelineVersion])(
    implicit ec: ExecutionContext
  ): AsyncResult[List[String]] = ???

  override def getFile(project: Project, path: Path, version: Option[PipelineVersion])(
    implicit ec: ExecutionContext
  ): AsyncResult[ProjectFile] = {
    val filePath: String = URLEncoder.encode(path.toString, "UTF-8")
    val fileVersion: String = version match {
      case Some(version) => version.name
      case None          => config.defaultFileVersion
    }

    httpClient
      .get(
        s"${config.url}/projects/${project.repository}/repository/files/$filePath/raw",
        Map("ref" -> fileVersion),
        config.token
      )
      .map(
        resp =>
          resp.status match {
            case HttpStatusCodes.OK => Right(ProjectFile(path, resp.body))
            case _                  => Left(VersioningException(s"Exception. Response status: ${resp.status}"))
          }
      )
      .recover { case e: Throwable => Left(VersioningException(e.getMessage)) }
  }
}
