package cromwell.pipeline.controller

import java.net.URLEncoder
import java.nio.file.{Path, Paths}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cromwell.pipeline.datastorage.dao.repository.utils.{TestProjectUtils, TestUserUtils}
import cromwell.pipeline.datastorage.dto._
import cromwell.pipeline.datastorage.utils.auth.AccessTokenContent
import cromwell.pipeline.service.{ProjectFileService, ProjectService, VersioningException}
import cromwell.pipeline.utils.{ApplicationConfig, GitLabConfig}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
import org.mockito.Mockito.when
import org.scalatest.{AsyncWordSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

class ProjectFileControllerTest extends AsyncWordSpec with Matchers with ScalatestRouteTest with MockitoSugar {
  private val projectFileService: ProjectFileService = mock[ProjectFileService]
  private val projectService: ProjectService = mock[ProjectService]
  private val projectFileController = new ProjectFileController(projectFileService, projectService)
  private val gitLabConfig: GitLabConfig = ApplicationConfig.load().gitLabConfig

  "ProjectFileController" when {
    "validate file" should {
      val accessToken = AccessTokenContent(TestUserUtils.getDummyUserId)
      val content = FileContent("task hello {}")

      "return OK response to valid file" taggedAs Controller in {
        when(projectFileService.validateFile(content)).thenReturn(Future.successful(Right(())))
        Post("/files/validation", content) ~> projectFileController.route(accessToken) ~> check {
          status shouldBe StatusCodes.OK
        }
      }

      "return error response to invalid file" taggedAs Controller in {
        when(projectFileService.validateFile(content))
          .thenReturn(Future.successful(Left(ValidationError(List("Miss close bracket")))))
        Post("/files/validation", content) ~> projectFileController.route(accessToken) ~> check {
          status shouldBe StatusCodes.Conflict
          entityAs[List[String]] shouldBe List("Miss close bracket")
        }
      }
    }

    "upload file" should {
      val version = PipelineVersion("v0.0.2")
      val accessToken = AccessTokenContent(TestUserUtils.getDummyUserId)
      val project = TestProjectUtils.getDummyProject()
      val projectFile = ProjectFile(Paths.get("folder/test.txt"), "file context")
      val request = ProjectUpdateFileRequest(project, projectFile, Some(version))
      val content = FileContent(projectFile.content)

      "return OK response for valid request with a valid file" taggedAs Controller in {
        when(projectFileService.validateFile(content)).thenReturn(Future.successful(Right(())))
        when(projectFileService.uploadFile(project, projectFile, Some(version)))
          .thenReturn(Future.successful(Right("Success")))
        Post("/files", request) ~> projectFileController.route(accessToken) ~> check {
          status shouldBe StatusCodes.OK
        }
      }

      "return Precondition File response for valid request with an invalid file" taggedAs Controller in {
        when(projectFileService.validateFile(content))
          .thenReturn(Future.successful(Left(ValidationError(List("Miss close bracket")))))
        when(projectFileService.uploadFile(project, projectFile, Some(version)))
          .thenReturn(Future.successful(Right("Success")))
        Post("/files", request) ~> projectFileController.route(accessToken) ~> check {
          status shouldBe StatusCodes.Created
        }
      }

      "return InternalServerError for bad request" taggedAs Controller in {
        when(projectFileService.uploadFile(project, projectFile, Some(version)))
          .thenReturn(Future.successful(Left(VersioningException("Bad request"))))
        Post("/files", request) ~> projectFileController.route(accessToken) ~> check {
          status shouldBe StatusCodes.UnprocessableEntity
          entityAs[String] shouldBe "Bad request"
        }
      }
    }

    "get file" should {
      val version = PipelineVersion("v.0.0.2")
      val accessToken = AccessTokenContent(TestUserUtils.getDummyUserId)
      val project = TestProjectUtils.getDummyProject()
      val projectId = TestProjectUtils.getDummyProject().projectId
      val projectFile = ProjectFile(Paths.get("folder/test.txt"), "file context")

      "return OK response for valid request" taggedAs Controller in {
        when(projectFileService.getFile(project, projectFile.path, Some(version)))
          .thenReturn(Future.successful(Right(projectFile)))
        Get(s"/files/try?$projectId&$projectFile") ~> projectFileController.route(accessToken) ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    "delete file" should {
      val accessToken = AccessTokenContent(TestUserUtils.getDummyUserId)
      val project = TestProjectUtils.getDummyProject()
//      val projectId = TestProjectUtils.getDummyProject().projectId
      val path: Path = Paths.get("test.md")
      val branchName: String = gitLabConfig.defaultBranch
      val commitMessage: String = s"$path file has been deleted from $branchName"

      "return OK response for valid request" taggedAs Controller in {
        when(projectFileService.deleteFile(project, path, branchName, commitMessage)).
          thenReturn(Future.successful(Right("Success")))
        Delete(s"${gitLabConfig.url}/projects/${activeProject.repository}/repository/files/${URLEncoder
          .encode(path.toString, "UTF-8")}/raw") ~> projectFileController.route(accessToken) ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }
  }
}
