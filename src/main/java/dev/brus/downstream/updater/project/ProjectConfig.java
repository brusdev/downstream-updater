package dev.brus.downstream.updater.project;

import java.io.File;
import java.io.IOException;

import dev.brus.downstream.updater.git.GitRepository;
import dev.brus.downstream.updater.git.JGitRepository;
import dev.brus.downstream.updater.util.CommandExecutor;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectConfig {
   private final static Logger logger = LoggerFactory.getLogger(ProjectConfig.class);
   private String repository;
   String repositoryAuthString;
   private String branch;
   private String path;
   private File targetDir;
   private GitRepository gitRepository = new JGitRepository();
   private Project project;

   public String getRepository() {
      return repository;
   }

   public String getRepositoryUserName() {
      return gitRepository.getUserName();
   }

   public ProjectConfig setRepositoryUserName(String userName) {
      gitRepository.setUserName(userName);
      return this;
   }

   public String getRepositoryUserEmail() {
      return gitRepository.getUserEmail();
   }

   public ProjectConfig setRepositoryUserEmail(String userEmail) {
      gitRepository.setUserEmail(userEmail);
      return this;
   }

   public String getRepositoryAuthString() {
      return repositoryAuthString;
   }

   public String getBranch() {
      return branch;
   }

   public String getPath() {
      return path;
   }

   public File getTargetDir() {
      return targetDir;
   }

   public GitRepository getGitRepository() {
      return gitRepository;
   }

   public Project getProject() {
      return project;
   }

   public ProjectConfig(String repository, String repositoryAuthString, String branch, String path, File targetDir) {
      this.repository = repository;
      this.branch = branch;
      this.path = path;
      this.targetDir = targetDir;

      this.gitRepository.getRemoteAuthStrings().put("origin", repositoryAuthString);
   }

   public void load() throws Exception {
      loadRepository();

      project = Project.load(new File(gitRepository.getDirectory(), path));
   }

   public void addExcludedUpstreamIssue(String issueKey, String end, String streamName, int retries) throws Exception {
      while (true) {
         try {
            retries--;
            addExcludedUpstreamIssue(issueKey, end, streamName);
            break;
         } catch (Exception e) {
            logger.debug("Failed to exclude upstream issue " + issueKey + ": " + e);
            if (retries == 0) {
               throw new IOException("Failed to exclude upstream issue " + issueKey + ". Maximum retries reached.", e);
            } else {
               Thread.sleep((long)(3000 * Math.random()));
            }
         }
      }
   }

   public void addExcludedUpstreamIssue(String issueKey, String end, String streamName) throws Exception {
      load();

      ProjectStream projectStream = project.getStream(streamName);
      ProjectStreamIssue issue = new ProjectStreamIssue();
      issue.setKey(issueKey);
      issue.setEnd(end);

      String issueJson = "{\"key\":\"" + issueKey + "\"" +
         (end != null ? ",\"end\":\"" + end + "\"}" : "}");

      CommandExecutor.execute("yq -i '(.streams[] | select(.name == \"" + streamName
         + "\" ) | .excludedUpstreamIssues) += [" + issueJson + "] ' " + path,
         gitRepository.getDirectory(), null);

      gitRepository.add(path);
      gitRepository.commit("Exclude " + issueKey + " from " + project.getName() + " " +
         (end != null ? "until " + end + " " : "") + streamName);
      gitRepository.push("origin", branch);

      projectStream.getExcludedUpstreamIssues().add(issue);
   }

   private void loadRepository() throws Exception {
      String repositoryBaseName = FilenameUtils.getBaseName(repository);
      File repoDir = new File(targetDir, repositoryBaseName + "-repo");

      if (repoDir.exists()) {
         gitRepository.open(repoDir);
         gitRepository.fetch("origin");
      } else {
         gitRepository.clone(repository, repoDir);
      }

      gitRepository.resetHard();

      if (gitRepository.branchExists(branch)) {
         // To support tags
         if (!gitRepository.branchExists("origin/" + branch)) {
            gitRepository.branchCreate("origin/" + branch, branch);
         }
         gitRepository.checkout("origin/" + branch);
         gitRepository.branchDelete(branch);
      }
      gitRepository.branchCreate(branch, "origin/" + branch);
      gitRepository.checkout(branch);
   }
}
