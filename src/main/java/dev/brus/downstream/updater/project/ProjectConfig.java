package dev.brus.downstream.updater.project;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import dev.brus.downstream.updater.git.GitRepository;
import dev.brus.downstream.updater.git.JGitRepository;
import dev.brus.downstream.updater.util.CommandExecutor;
import org.apache.commons.io.FilenameUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class ProjectConfig {
   private String repository;
   String repositoryAuthString;
   private String branch;
   private String path;
   private File targetDir;
   private GitRepository gitRepository = new JGitRepository();
   private Yaml yaml = new Yaml(new Constructor(Project.class, new LoaderOptions()));
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

   public Yaml getYaml() {
      return yaml;
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

      File projectConfigFile = getProjectConfigFile();
      try (InputStream projectConfigInputStream = new FileInputStream(projectConfigFile)) {
         project = yaml.load(projectConfigInputStream);
      }
   }

   public void addExcludedUpstreamIssue(String issueKey, String streamName) throws Exception {
      CommandExecutor.execute("yq -i '(.streams[] | select(.name == \"" + streamName
         + "\" ) | .excludedUpstreamIssues) += \"" + issueKey + "\" ' " + path,
         gitRepository.getDirectory(), null);

      gitRepository.add(path);
      gitRepository.commit("Exclude " + issueKey + " from " + project.getName() + " " + streamName);
      gitRepository.push("origin", branch);
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

   private File getProjectConfigFile() {
      return new File(gitRepository.getDirectory(), path);
   }
}
