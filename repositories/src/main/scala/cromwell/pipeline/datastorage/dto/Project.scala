package cromwell.pipeline.datastorage.dto

import java.nio.file.{ Path, Paths }

import cromwell.pipeline.datastorage.dto.PipelineVersion.PipelineVersionException
import play.api.libs.functional.syntax._
import play.api.libs.json._
import cromwell.pipeline.model.wrapper.UserId
import slick.lifted.MappedTo

final case class Project(
  projectId: ProjectId,
  ownerId: UserId,
  name: String,
  active: Boolean,
  repository: Option[Repository] = None,
  visibility: Visibility = Private
) {
  def withRepository(repositoryPath: Option[String]): Project =
    this.copy(repository = repositoryPath.map(Repository(_)))
}
object Project {
  implicit lazy val projectFormat: OFormat[Project] = Json.format[Project]
  implicit lazy val projectWrites: Writes[Project] = (project: Project) =>
    Json.obj(
      "projectId" -> project.projectId.value,
      "ownerId" -> project.ownerId,
      "name" -> project.name,
      "active" -> project.active,
      "repository" -> project.repository,
      "visibility" -> Visibility.toString(project.visibility),
      "path" -> project.projectId.value
    )
}

final case class ProjectId(value: String) extends MappedTo[String]

object ProjectId {
  implicit lazy val projectIdFormat: Format[ProjectId] = implicitly[Format[String]].inmap(ProjectId.apply, _.value)
}

final case class Repository(value: String) extends MappedTo[String]

object Repository {
  implicit lazy val repositoryFormat: Format[Repository] = implicitly[Format[String]].inmap(Repository.apply, _.value)
}

final case class ProjectAdditionRequest(name: String)

object ProjectAdditionRequest {
  implicit lazy val projectAdditionFormat: OFormat[ProjectAdditionRequest] = Json.format[ProjectAdditionRequest]
}

final case class ProjectDeleteRequest(projectId: ProjectId)

object ProjectDeleteRequest {
  implicit lazy val projectDeleteFormat: OFormat[ProjectDeleteRequest] = Json.format[ProjectDeleteRequest]
}

final case class ProjectUpdateRequest(projectId: ProjectId, name: String, repository: Option[Repository])

object ProjectUpdateRequest {
  implicit val updateRequestFormat: OFormat[ProjectUpdateRequest] = Json.format[ProjectUpdateRequest]
}

final case class GitLabVersion(name: PipelineVersion, message: String, target: String, commit: Commit)
object GitLabVersion {
  implicit val gitlabVersionFormat: OFormat[GitLabVersion] = Json.format[GitLabVersion]
}

final case class PipelineVersion private (versions: Int*) extends Ordered[PipelineVersion] {
  val name: String = s"v.${versions.mkString(".")}"

  override def compare(that: PipelineVersion): Int = {
    val comparableV =
      for ((thisV, thatV) <- this.versions.zip(that.versions))
        yield thisV.compare(thatV)
    comparableV.find(i => i != 0) match {
      case Some(value) => value
      case None        => this.versions.length.compare(that.versions.length)
    }
  }

  def changeVersion(index: Int, value: Int): PipelineVersion =
    if (index >= this.versions.length) throw PipelineVersionException("Out of bounds")
    else if (value <= 0) throw PipelineVersionException("Version must be positive value")
    else new PipelineVersion(this.versions.updated(index, value): _*)

  override def toString: String = this.name
}

object PipelineVersion {
  def apply(versionLine: String): PipelineVersion = {
    val regex = """^v\.((\d+\.)*\d+)$""".r
    versionLine match {
      case regex(versions, _) =>
        val result = versions.split("\\.").map(_.toInt)
        new PipelineVersion(result: _*)
      case _ =>
        throw PipelineVersionException(
          s"Format of version name: 'v.(int).(int).(int)' but got: $versionLine"
        )
    }
  }

  final case class PipelineVersionException(message: String) extends Exception(message)

  implicit val pipelineVersionFormat: Format[PipelineVersion] =
    implicitly[Format[String]].inmap(PipelineVersion.apply, _.name)
}

final case class Commit(id: String)
object Commit {
  implicit val commitFormat: OFormat[Commit] = Json.format[Commit]
}

final case class ProjectFile(path: Path, content: String)

object ProjectFile {
  implicit object ProjectFileFormat extends Format[ProjectFile] {
    override def reads(json: JsValue): JsResult[ProjectFile] =
      JsSuccess(ProjectFile(Paths.get((json \ "path").as[String]), (json \ "content").as[String]))

    override def writes(o: ProjectFile): JsValue = JsObject(
      Seq("path" -> JsString(o.path.toString), "content" -> JsString(o.content))
    )
  }
}

sealed trait Visibility
case object Private extends Visibility
case object Internal extends Visibility
case object Public extends Visibility

object Visibility {
  implicit lazy val visibilityFormat: Format[Visibility] =
    implicitly[Format[String]].inmap(Visibility.fromString, Visibility.toString)

  def fromString(s: String): Visibility = s match {
    case "private"  => Private
    case "internal" => Internal
    case "public"   => Public
  }

  def toString(visibility: Visibility): String = visibility match {
    case Private  => "private"
    case Internal => "internal"
    case Public   => "public"
  }

  def values = Seq(Private, Internal, Public)
}

final case class FileContent(content: String)

object FileContent {
  implicit lazy val validateFileRequestFormat: OFormat[FileContent] = Json.format[FileContent]
}

final case class ProjectUpdateFileRequest(project: Project, projectFile: ProjectFile, version: Option[PipelineVersion])

object ProjectUpdateFileRequest {
  implicit lazy val projectUpdateFileRequestFormat: OFormat[ProjectUpdateFileRequest] =
    ((JsPath \ "project").format[Project] ~ (JsPath \ "projectFile").format[ProjectFile] ~ (JsPath \ "version")
      .formatNullable[PipelineVersion])(
      ProjectUpdateFileRequest.apply,
      unlift(ProjectUpdateFileRequest.unapply)
    )
}
