package dev.brus.midstream.updater.project;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;

import dev.brus.downstream.updater.project.ProjectConfig;
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
   public void testExcludedUpstreamIssue() throws Exception {
      String branch = "main";
      File repoDir = testFolder.newFolder("repo");
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

      Assert.assertNotNull(projectConfig.getProject());
      Assert.assertEquals("AMQ Broker", projectConfig.getProject().getName());

      testExcludedUpstreamIssue(projectConfig, "7.10", "UP-1234");
      testExcludedUpstreamIssue(projectConfig, "7.11", "UP-1234");
   }

   private void testExcludedUpstreamIssue(ProjectConfig projectConfig, String projectStreamName, String excludedUpstreamIssue) throws Exception {
      File repoDir = new File(URI.create(projectConfig.getRepository()));

      projectConfig.addExcludedUpstreamIssue(excludedUpstreamIssue, projectStreamName);
      projectConfig.getProject().getStream(projectStreamName).getExcludedUpstreamIssues().add(excludedUpstreamIssue);

      Assert.assertTrue(CommandExecutor.execute("git log", repoDir).contains(excludedUpstreamIssue));
      Assert.assertTrue(Files.readString(new File(projectConfig.getGitRepository().getDirectory(), TEST_PROJECT_CONFIG).toPath()).contains(excludedUpstreamIssue));

      CommandExecutor.execute("git reset --hard", repoDir);
      Assert.assertTrue(CommandExecutor.execute("git log", projectConfig.getGitRepository().getDirectory()).contains(excludedUpstreamIssue));
      Assert.assertTrue(Files.readString(new File(repoDir, TEST_PROJECT_CONFIG).toPath()).contains(excludedUpstreamIssue));
   }

}
