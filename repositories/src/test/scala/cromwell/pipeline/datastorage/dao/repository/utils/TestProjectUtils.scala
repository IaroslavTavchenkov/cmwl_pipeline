package cromwell.pipeline.datastorage.dao.repository.utils

import java.util.UUID

import cromwell.pipeline.datastorage.dto._
import cromwell.pipeline.model.wrapper.UserId

import scala.util.Random

object TestProjectUtils {

  private def randomInt: Int = Random.nextInt()
  private def randomUuidStr: String = UUID.randomUUID().toString
  def getDummyProjectId: ProjectId = ProjectId(randomUuidStr)
  def getDummyRepository: Repository = Repository(s"repo-$randomUuidStr")
  def getDummyProject(
    projectId: ProjectId = getDummyProjectId,
    ownerId: UserId = TestUserUtils.getDummyUserId,
    name: String = s"project-$randomUuidStr",
    repository: Option[Repository] = Some(getDummyRepository),
    active: Boolean = true,
    visibility: Visibility = Private
  ): Project = Project(projectId, ownerId, name, active, repository, visibility)
  def getDummyCommit(id: String = randomUuidStr): Commit = Commit(id)
  def getDummyPipeLineVersion(
    v1: Int = 1 + Random.nextInt(12),
    v2: Int = 1 + Random.nextInt(12),
    v3: Int = 1 + Random.nextInt(12)
  ): PipelineVersion =
    PipelineVersion(v1, v2, v3)
  def getDummyGitLabVersion(
    version: PipelineVersion = getDummyPipeLineVersion(),
  def getDummyCommit(
    id: String = randomUuidStr
  ): Commit = Commit(id)
  def getDummyVersion(
    name: String = s"name-$randomUuidStr",
    message: String = s"message-$randomUuidStr",
    target: String = s"target-$randomUuidStr",
    commit: Commit = getDummyCommit()
  ): GitLabVersion = GitLabVersion(version, message, target, commit)
  ): Version = Version(name, message, target, commit)
  def getDummyFileCommit(
    commit_id: String = s"$randomUuidStr"
  ): FileCommit = FileCommit(commit_id)
}
