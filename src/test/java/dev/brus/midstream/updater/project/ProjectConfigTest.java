package dev.brus.midstream.updater.project;

import java.io.File;
import java.net.URI;
import java.util.Objects;

import dev.brus.downstream.updater.issue.IssueCustomerPriority;
import dev.brus.downstream.updater.issue.IssueSecurityImpact;
import dev.brus.downstream.updater.project.Project;
import dev.brus.downstream.updater.project.ProjectConfig;
import dev.brus.downstream.updater.project.ProjectStream;
import dev.brus.downstream.updater.util.CommandExecutor;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProjectConfigTest {

   private final static String TEST_USER_NAME = "test";
   private final static String TEST_USER_EMAIL = "test@user.com";

   private final static String TEST_PROJECT_CONFIG = "test-project-config.yaml";

   @Rule
   public TemporaryFolder testFolder = new TemporaryFolder();

   @Test
   public void testProjectStreams() throws Exception {
      File repoDir = testFolder.newFolder("repo");
      ProjectConfig projectConfig = loadProjectConfig(repoDir);

      Assert.assertNotNull(projectConfig.getProject());
      Assert.assertEquals("AMQ Broker", projectConfig.getProject().getName());
      ProjectStream projectStream_78 = projectConfig.getProject().getStream("7.8");
      Assert.assertEquals(ProjectStream.Mode.VIEWING, projectStream_78.getMode());
      Assert.assertEquals(true, projectStream_78.getDownstreamIssuesRequired());
      Assert.assertEquals(IssueCustomerPriority.NONE.name(), projectStream_78.getDownstreamIssuesCustomerPriority());
      Assert.assertEquals(IssueSecurityImpact.IMPORTANT.name(), projectStream_78.getDownstreamIssuesSecurityImpact());
      Assert.assertEquals(ProjectStream.Mode.VIEWING, projectConfig.getProject().getStream("7.8").getMode());
      Assert.assertEquals(ProjectStream.Mode.VIEWING, projectConfig.getProject().getStream("7.10").getMode());
      Assert.assertEquals(ProjectStream.Mode.MANAGING, projectConfig.getProject().getStream("7.11").getMode());
   }

   @Test
   public void testExcludedUpstreamIssue() throws Exception {
      File repoDir = testFolder.newFolder("project-configs");
      ProjectConfig projectConfig = loadProjectConfig(repoDir);

      Assert.assertNotNull(projectConfig.getProject());
      Assert.assertEquals("AMQ Broker", projectConfig.getProject().getName());

      projectConfig.addExcludedUpstreamIssue("UP-1234", null, "7.10");
      // Reset to see remote pushes
      CommandExecutor.execute("git reset --hard", repoDir);
      testExcludedUpstreamIssue(projectConfig, "7.10", "UP-1234", null);

      CommandExecutor.execute("git commit --allow-empty --message 'Other commit'", repoDir);

      projectConfig.addExcludedUpstreamIssue("UP-1234", null, "7.11");
      // Reset to see remote pushes
      CommandExecutor.execute("git reset --hard", repoDir);
      testExcludedUpstreamIssue(projectConfig, "7.11", "UP-1234", null);

      testExcludedUpstreamIssue(projectConfig, "7.10", "UP-1234", null);
   }

   @Test
   public void testExcludedUpstreamIssueUntil() throws Exception {
      File repoDir = testFolder.newFolder("project-configs");
      ProjectConfig projectConfig = loadProjectConfig(repoDir);

      Assert.assertNotNull(projectConfig.getProject());
      Assert.assertEquals("AMQ Broker", projectConfig.getProject().getName());

      projectConfig.addExcludedUpstreamIssue("UP-1234", "7.10.1.CR1", "7.10");
      // Reset to see remote pushes
      CommandExecutor.execute("git reset --hard", repoDir);
      testExcludedUpstreamIssue(projectConfig, "7.10", "UP-1234", "7.10.1.CR1");
   }

   private ProjectConfig loadProjectConfig(File repoDir) throws Exception {
      String branch = "main";
      FileUtils.copyFileToDirectory(new File("src/test/resources/" + TEST_PROJECT_CONFIG), repoDir);
      CommandExecutor.execute("git init --initial-branch " + branch, repoDir);
      CommandExecutor.execute("git config user.name '" + TEST_USER_NAME + "'", repoDir);
      CommandExecutor.execute("git config user.email '" + TEST_USER_EMAIL + "'", repoDir);
      CommandExecutor.execute("git add --all", repoDir);
      CommandExecutor.execute("git commit --message 'Initial commit'", repoDir);


      File targetDir = testFolder.newFolder("target");
      String repo = "file://" + repoDir.getAbsolutePath();
      ProjectConfig projectConfig = new ProjectConfig(repo, null, branch, TEST_PROJECT_CONFIG, targetDir);
      projectConfig.setRepositoryUserName(TEST_USER_NAME);
      projectConfig.setRepositoryUserEmail(TEST_USER_EMAIL);

      projectConfig.load();

      return projectConfig;
   }

   private void testExcludedUpstreamIssue(ProjectConfig projectConfig, String projectStreamName, String excludedUpstreamIssue, String exclusionUntil) throws Exception {
      File repoDir = new File(URI.create(projectConfig.getRepository()));

      Assert.assertTrue(projectConfig.getProject().getStream(projectStreamName).getExcludedUpstreamIssues().
         stream().anyMatch(issue -> Objects.equals(issue.getKey(), excludedUpstreamIssue) && Objects.equals(issue.getUntil(), exclusionUntil)));

      String gitLog = CommandExecutor.execute("git log", repoDir);
      Assert.assertTrue(gitLog.contains(excludedUpstreamIssue));
      if (exclusionUntil != null) {
         Assert.assertTrue(gitLog.contains(exclusionUntil));
      }

      Project originProject = Project.load(new File(repoDir, TEST_PROJECT_CONFIG));
      Assert.assertTrue(originProject.getStream(projectStreamName).getExcludedUpstreamIssues().
         stream().anyMatch(issue -> Objects.equals(issue.getKey(), excludedUpstreamIssue) && Objects.equals(issue.getUntil(), exclusionUntil)));

      Assert.assertTrue(CommandExecutor.execute("git log", projectConfig.getGitRepository().getDirectory()).contains(excludedUpstreamIssue));
      Project targetProject = Project.load(new File(projectConfig.getGitRepository().getDirectory(), TEST_PROJECT_CONFIG));
      Assert.assertTrue(targetProject.getStream(projectStreamName).getExcludedUpstreamIssues().
         stream().anyMatch(issue -> Objects.equals(issue.getKey(), excludedUpstreamIssue) && Objects.equals(issue.getUntil(), exclusionUntil)));
   }

}
