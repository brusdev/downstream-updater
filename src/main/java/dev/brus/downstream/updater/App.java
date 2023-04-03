package dev.brus.downstream.updater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.brus.downstream.updater.git.GitCommit;
import dev.brus.downstream.updater.git.GitRepository;
import dev.brus.downstream.updater.git.JGitRepository;
import dev.brus.downstream.updater.issue.DownstreamIssueManager;
import dev.brus.downstream.updater.issue.Issue;
import dev.brus.downstream.updater.issue.IssueCustomerPriority;
import dev.brus.downstream.updater.issue.IssueManager;
import dev.brus.downstream.updater.issue.IssueManagerFactory;
import dev.brus.downstream.updater.issue.IssueSecurityImpact;
import dev.brus.downstream.updater.project.Project;
import dev.brus.downstream.updater.project.ProjectConfig;
import dev.brus.downstream.updater.project.ProjectStream;
import dev.brus.downstream.updater.user.User;
import dev.brus.downstream.updater.user.UserResolver;
import dev.brus.downstream.updater.util.CommandLine;
import dev.brus.downstream.updater.util.CommandLineParser;
import dev.brus.downstream.updater.util.ReleaseVersion;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hello world!
 */
public class App {
   private final static Logger logger = LoggerFactory.getLogger(App.class);

   private final static Pattern cherryPickedCommitPattern = Pattern.compile("cherry picked from commit ([0-9a-f]{40})");
   private final static Pattern revertedCommitPattern = Pattern.compile("This reverts commit ([0-9a-f]{40})");
   private final static Pattern prepareReleaseCommitPattern = Pattern.compile("Prepare release (.*)");

   private static final String PROJECT_CONFIG_REPOSITORY_OPTION = "project-config-repository";
   private static final String PROJECT_CONFIG_REPOSITORY_AUTH_STRING_OPTION = "project-config-repository-auth-string";
   private static final String PROJECT_CONFIG_BRANCH_OPTION = "project-config-branch";
   private static final String PROJECT_CONFIG_PATH_OPTION = "project-config-path";
   private static final String PROJECT_STREAM_NAME_OPTION = "project-stream-name";
   private static final String COMMITS_OPTION = "commits";
   private static final String CONFIRMED_COMMITS_OPTION = "confirmed-commits";
   private static final String PAYLOAD_OPTION = "payload";
   private static final String CONFIRMED_UPSTREAM_ISSUES_OPTION = "confirmed-upstream-issues";
   private static final String EXCLUDED_UPSTREAM_ISSUES_OPTION = "excluded-upstream-issues";
   private static final String CONFIRMED_DOWNSTREAM_ISSUES_OPTION = "confirmed-downstream-issues";
   private static final String EXCLUDED_DOWNSTREAM_ISSUES_OPTION = "excluded-downstream-issues";
   private static final String UPSTREAM_REPOSITORY_OPTION = "upstream-repository";
   private static final String UPSTREAM_REPOSITORY_AUTH_STRING_OPTION = "upstream-repository-auth-string";
   private static final String UPSTREAM_BRANCH_OPTION = "upstream-branch";
   private static final String UPSTREAM_ISSUES_SERVER_URL_OPTION = "upstream-issues-server-url";
   private static final String UPSTREAM_ISSUES_AUTH_STRING_OPTION = "upstream-issues-auth-string";
   private static final String UPSTREAM_ISSUES_PROJECT_KEY_OPTION = "upstream-issues-project-key";
   private static final String DOWNSTREAM_REPOSITORY_OPTION = "downstream-repository";
   private static final String DOWNSTREAM_REPOSITORY_AUTH_STRING_OPTION = "downstream-repository-auth-string";
   private static final String DOWNSTREAM_BRANCH_OPTION = "downstream-branch";
   private static final String DOWNSTREAM_ISSUES_SERVER_URL_OPTION = "downstream-issues-server-url";
   private static final String DOWNSTREAM_ISSUES_AUTH_STRING_OPTION = "downstream-issues-auth-string";
   private static final String DOWNSTREAM_ISSUES_PROJECT_KEY_OPTION = "downstream-issues-project-key";
   private static final String DOWNSTREAM_ISSUES_CUSTOMER_PRIORITY = "downstream-issues-customer-priority";
   private static final String DOWNSTREAM_ISSUES_SECURITY_IMPACT = "downstream-issues-security-impact";
   private static final String RELEASE_OPTION = "release";
   private static final String TARGET_RELEASE_FORMAT_OPTION = "target-release-format";
   private static final String ASSIGNEE_OPTION = "assignee";
   private static final String CHECK_INCOMPLETE_COMMITS_OPTION = "check-incomplete-commits";
   private static final String CHECK_COMMAND_OPTION = "check-command";
   private static final String CHECK_TESTS_COMMAND_OPTION = "check-tests-command";
   private static final String DRY_RUN_OPTION = "dry-run";

   private static final String DEFAULT_USER_NAME = "rh-messaging-ci";
   private static final String DEFAULT_USER_EMAIL = "messaging-infra@redhat.com";


   public static void main(String[] args) throws Exception {
      // Initialize target directory
      File targetDir = new File("target");
      if (!targetDir.exists()) {
         if (!targetDir.mkdir()) {
            throw new RuntimeException("Error creating target directory");
         }
      }

      // Parse arguments
      CommandLineParser parser = new CommandLineParser();
      parser.addOption(null, PROJECT_CONFIG_REPOSITORY_OPTION, true, true, false, "the project config repository, i.e. https://gitlab.cee.redhat.com/amq/project-configs.git");
      parser.addOption(null, PROJECT_CONFIG_REPOSITORY_AUTH_STRING_OPTION, true, true, false, "the auth string to access project config repository");
      parser.addOption(null, PROJECT_CONFIG_BRANCH_OPTION, true, true, false, "the project config branch, i.e. main");
      parser.addOption(null, PROJECT_CONFIG_PATH_OPTION, true, true, false, "the project config path, i.e. amq-broker-distribution.yaml");
      parser.addOption(null, PROJECT_STREAM_NAME_OPTION, true, true, false, "the project stream name, i.e. 7.10");

      parser.addOption(null, ASSIGNEE_OPTION, false, true, false, "the default assignee, i.e. dbruscin");
      parser.addOption(null, RELEASE_OPTION, false, true, false, "the release, i.e. 7.11.0.CR1");
      parser.addOption(null, TARGET_RELEASE_FORMAT_OPTION, false, true, false, "the target release format, i.e. AMQ %d.%d.%d.GA");
      parser.addOption(null, UPSTREAM_REPOSITORY_OPTION, false, true, false, "the upstream repository to cherry-pick from, i.e. https://github.com/apache/activemq-artemis.git");
      parser.addOption(null, UPSTREAM_REPOSITORY_AUTH_STRING_OPTION, false, true, false, "the auth string to access upstream repository");
      parser.addOption(null, UPSTREAM_BRANCH_OPTION, false, true, false, "the upstream branch to cherry-pick from, i.e. main");
      parser.addOption(null, DOWNSTREAM_REPOSITORY_OPTION, false, true, false, "the downstream repository to cherry-pick to, i.e. https://github.com/rh-messaging/activemq-artemis.git");
      parser.addOption(null, DOWNSTREAM_REPOSITORY_AUTH_STRING_OPTION, false, true, false, "the auth string to access downstream repository");
      parser.addOption(null, DOWNSTREAM_BRANCH_OPTION, false, true, false, "the downstream branch to cherry-pick to, i.e. 2.16.0.jbossorg-x");

      parser.addOption(null, COMMITS_OPTION, false, true, false, "the commits");
      parser.addOption(null, CONFIRMED_COMMITS_OPTION, false, true, false, "the confirmed commits");
      parser.addOption(null, PAYLOAD_OPTION, false, true, false, "the commits");
      parser.addOption(null, CONFIRMED_DOWNSTREAM_ISSUES_OPTION, false, true, true, "the confirmed downstream issues, commits related to other downstream issues with a different target release will be skipped");
      parser.addOption(null, EXCLUDED_DOWNSTREAM_ISSUES_OPTION, false, true, true, "the excluded downstream issues, commits related to other downstream issues with a different target release will be skipped");
      parser.addOption(null, CONFIRMED_UPSTREAM_ISSUES_OPTION, false, true, true, "the confirmed upstream issues, commits related to other upstream issues without a downstream issue will be skipped");
      parser.addOption(null, EXCLUDED_UPSTREAM_ISSUES_OPTION, false, true, true, "the excluded upstream issues, commits related to other upstream issues without a downstream issue will be skipped");

      parser.addOption(null, UPSTREAM_ISSUES_SERVER_URL_OPTION, false, true, false, "the server URL to access upstream issues, i.e. https://issues.apache.org/jira/rest/api/2");
      parser.addOption(null, UPSTREAM_ISSUES_AUTH_STRING_OPTION, false, true, false, "the auth string to access upstream issues, i.e. \"Bearer ...\"");
      parser.addOption(null, UPSTREAM_ISSUES_PROJECT_KEY_OPTION, false, true, false, "the project key to access upstream issues, i.e. ARTEMIS");
      parser.addOption(null, DOWNSTREAM_ISSUES_SERVER_URL_OPTION, false, true, false, "the server URL to access downstream issues, i.e. https://issues.redhat.com/rest/api/2");
      parser.addOption(null, DOWNSTREAM_ISSUES_AUTH_STRING_OPTION, false, true, false, "the auth string to access downstream issues, i.e. \"Bearer ...\"");
      parser.addOption(null, DOWNSTREAM_ISSUES_PROJECT_KEY_OPTION, false, true, false, "the project key to access downstream issues, i.e. ENTMQBR");
      parser.addOption(null, DOWNSTREAM_ISSUES_CUSTOMER_PRIORITY, false, true, false, "the customer priority to filter downstream issues, i.e. HIGH");
      parser.addOption(null, DOWNSTREAM_ISSUES_SECURITY_IMPACT, false, true, false, "the security impact to filter downstream issues, i.e. IMPORTANT");

      parser.addOption(null, CHECK_INCOMPLETE_COMMITS_OPTION, false, false, true, "check tasks of cherry-picked commits");
      parser.addOption(null, CHECK_COMMAND_OPTION, false, true, true, "command to check cherry-picked commits");
      parser.addOption(null, CHECK_TESTS_COMMAND_OPTION, false, true, true, "command to test cherry-picked commits with tests");
      parser.addOption(null, DRY_RUN_OPTION, false, false, false, "dry run");

      CommandLine line;

      try {
         line = parser.parse(args);
      } catch (Exception e) {
         throw new RuntimeException("Error on parsing arguments", e);
      }

      String projectConfigRepository = line.getOptionValue(PROJECT_CONFIG_REPOSITORY_OPTION);
      String projectConfigRepositoryAuthString = line.getOptionValue(PROJECT_CONFIG_REPOSITORY_AUTH_STRING_OPTION);
      String projectConfigBranch = line.getOptionValue(PROJECT_CONFIG_BRANCH_OPTION);
      String projectConfigPath = line.getOptionValue(PROJECT_CONFIG_PATH_OPTION);
      String projectStreamName = line.getOptionValue(PROJECT_STREAM_NAME_OPTION);

      ProjectConfig projectConfig = new ProjectConfig(projectConfigRepository,
         projectConfigRepositoryAuthString, projectConfigBranch, projectConfigPath, targetDir);
      projectConfig.setRepositoryUserName(DEFAULT_USER_NAME);
      projectConfig.setRepositoryUserEmail(DEFAULT_USER_EMAIL);
      projectConfig.load();

      Project project = projectConfig.getProject();
      ProjectStream projectStream = project.getStream(projectStreamName);

      String assignee = line.getOptionValue(ASSIGNEE_OPTION, projectStream.getAssignee());

      String release = line.getOptionValue(RELEASE_OPTION, projectStream.getRelease());
      ReleaseVersion candidateReleaseVersion = ReleaseVersion.fromString(release);

      String targetReleaseFormat = line.getOptionValue(TARGET_RELEASE_FORMAT_OPTION, project.getTargetReleaseFormat());

      String upstreamRepository = line.getOptionValue(UPSTREAM_REPOSITORY_OPTION, project.getUpstreamRepository());
      String upstreamRepositoryAuthString = line.getOptionValue(UPSTREAM_REPOSITORY_AUTH_STRING_OPTION);
      String upstreamBranch = line.getOptionValue(UPSTREAM_BRANCH_OPTION, projectStream.getUpstreamBranch());

      String downstreamRepository = line.getOptionValue(DOWNSTREAM_REPOSITORY_OPTION, project.getDownstreamRepository());
      String downstreamRepositoryAuthString = line.getOptionValue(DOWNSTREAM_REPOSITORY_AUTH_STRING_OPTION);
      String downstreamBranch = line.getOptionValue(DOWNSTREAM_BRANCH_OPTION, projectStream.getDownstreamBranch());

      String downstreamIssuesServerURL = line.getOptionValue(DOWNSTREAM_ISSUES_SERVER_URL_OPTION, project.getDownstreamIssuesServer());
      String downstreamIssuesAuthString = line.getOptionValue(DOWNSTREAM_ISSUES_AUTH_STRING_OPTION);
      String downstreamIssuesProjectKey = line.getOptionValue(DOWNSTREAM_ISSUES_PROJECT_KEY_OPTION, project.getDownstreamIssuesProjectKey());

      IssueCustomerPriority downstreamIssuesCustomerPriority = IssueCustomerPriority.fromName(
         line.getOptionValue(DOWNSTREAM_ISSUES_CUSTOMER_PRIORITY, IssueCustomerPriority.NONE.name()));

      IssueSecurityImpact downstreamIssuesSecurityImpact = IssueSecurityImpact.fromName(
         line.getOptionValue(DOWNSTREAM_ISSUES_SECURITY_IMPACT, IssueSecurityImpact.NONE.name()));

      String upstreamIssuesServerURL = line.getOptionValue(UPSTREAM_ISSUES_SERVER_URL_OPTION, project.getUpstreamIssuesServer());
      String upstreamIssuesAuthString = line.getOptionValue(UPSTREAM_ISSUES_AUTH_STRING_OPTION);
      String upstreamIssuesProjectKey = line.getOptionValue(UPSTREAM_ISSUES_PROJECT_KEY_OPTION, project.getUpstreamIssuesProjectKey());

      String commitsFilename = line.getOptionValue(COMMITS_OPTION);

      String confirmedCommitsFilename = line.getOptionValue(CONFIRMED_COMMITS_OPTION, "confirmed-commits.json");

      String payloadFilename = line.getOptionValue(PAYLOAD_OPTION);

      String confirmedDownstreamIssueKeys = line.getOptionValue(CONFIRMED_DOWNSTREAM_ISSUES_OPTION);

      String excludedDownstreamIssueKeys = line.getOptionValue(EXCLUDED_DOWNSTREAM_ISSUES_OPTION, String.join(",", projectStream.getExcludedDownstreamIssues()));

      String confirmedUpstreamIssueKeys = line.getOptionValue(CONFIRMED_UPSTREAM_ISSUES_OPTION);

      String excludedUpstreamIssueKeys = line.getOptionValue(EXCLUDED_UPSTREAM_ISSUES_OPTION, String.join(",", projectStream.getExcludedUpstreamIssues()));

      boolean checkIncompleteCommits = Boolean.parseBoolean(line.getOptionValue(CHECK_INCOMPLETE_COMMITS_OPTION, "true"));

      boolean dryRun = line.hasOption(DRY_RUN_OPTION);

      String checkCommand = line.getOptionValue(CHECK_COMMAND_OPTION, project.getCheckCommand());

      String checkTestsCommand = line.getOptionValue(CHECK_TESTS_COMMAND_OPTION, project.getCheckTestCommand());

      // Initialize git
      GitRepository gitRepository = new JGitRepository();
      gitRepository.setUserName(DEFAULT_USER_NAME);
      gitRepository.setUserEmail(DEFAULT_USER_EMAIL);
      gitRepository.getRemoteAuthStrings().put("origin", downstreamRepositoryAuthString);
      gitRepository.getRemoteAuthStrings().put("upstream", upstreamRepositoryAuthString);
      String downstreamRepositoryBaseName = FilenameUtils.getBaseName(downstreamRepository);
      File repoDir = new File(targetDir, downstreamRepositoryBaseName + "-repo");

      if (repoDir.exists()) {
         gitRepository.open(repoDir);
         gitRepository.fetch("origin");
         gitRepository.fetch("upstream");
      } else {
         gitRepository.clone(downstreamRepository, repoDir);
         gitRepository.remoteAdd("upstream", upstreamRepository);
         gitRepository.fetch("upstream");
      }

      if (gitRepository.branchExists(downstreamBranch)) {
         gitRepository.checkout("upstream/" + upstreamBranch);
         // To support tags
         if (!gitRepository.branchExists("origin/" + downstreamBranch)) {
            gitRepository.branchCreate("origin/" + downstreamBranch, downstreamBranch);
         }
         gitRepository.branchDelete(downstreamBranch);
      }
      gitRepository.branchCreate(downstreamBranch, "origin/" + downstreamBranch);
      gitRepository.checkout(downstreamBranch);


      // Initialize gson
      Gson gson = new GsonBuilder().setPrettyPrinting().create();


      //Load users
      User[] usersArray;
      File usersFile = new File(targetDir, "users.json");
      if (usersFile.exists()) {
         usersArray = gson.fromJson(FileUtils.readFileToString(usersFile, Charset.defaultCharset()), User[].class);
      } else {
         usersArray = new User[0];
      }

      //Initialize UserResolver
      UserResolver userResolver = new UserResolver(usersArray);
      userResolver.setDefaultUser(userResolver.getUserFromUsername(assignee));


      //Initialize IssueManagerFactory
      IssueManagerFactory issueManagerFactory = new IssueManagerFactory();


      // Load upstream issues
      File upstreamIssuesFile = new File(targetDir, downstreamRepositoryBaseName + "-upstream-issues.json");
      IssueManager upstreamIssueManager = issueManagerFactory.getIssueManager(
         upstreamIssuesServerURL, upstreamIssuesAuthString, upstreamIssuesProjectKey);
      if (upstreamIssuesFile.exists()) {
         upstreamIssueManager.loadIssues( upstreamIssuesFile);

         for (Issue issue : upstreamIssueManager.getIssues()) {
            issue.getIssues().clear();
         }
      } else {
         upstreamIssueManager.loadIssues();
      }


      // Load downstream issues
      File downstreamIssuesFile = new File(targetDir, downstreamRepositoryBaseName + "-downstream-issues.json");
      DownstreamIssueManager downstreamIssueManager = issueManagerFactory.getDownstreamIssueManager(
         downstreamIssuesServerURL, downstreamIssuesAuthString, downstreamIssuesProjectKey, upstreamIssueManager);
      if (downstreamIssuesFile.exists()) {
         downstreamIssueManager.loadIssues(downstreamIssuesFile);
      } else {
         downstreamIssueManager.loadIssues();
      }

      // Link upstream issues
      for (Issue issue : downstreamIssueManager.getIssues()) {
         for (String upstreamIssueKey : issue.getIssues()) {
            Issue upstreamIssue = upstreamIssueManager.getIssue(upstreamIssueKey);

            if (upstreamIssue != null) {
               logger.debug("upstream issue " + upstreamIssueKey + " linked to downstream issue " + issue.getKey());
               if (!upstreamIssue.getIssues().contains(issue.getKey())) {
                  upstreamIssue.getIssues().add(issue.getKey());
               }
            } else {
               logger.warn("upstream issue " + upstreamIssueKey + " not found for downstream issue " + issue.getKey());
            }
         }
      }

      // Store upstream issues
      if (!upstreamIssuesFile.exists()) {
         upstreamIssueManager.storeIssues(upstreamIssuesFile);
      }


      // Store downstream issues
      if (!downstreamIssuesFile.exists()) {
         downstreamIssueManager.storeIssues(downstreamIssuesFile);
      }

      // Load upstream commits
      Deque<GitCommit> upstreamCommits = new ArrayDeque<>();
      Queue<Map.Entry<GitCommit, String>> upstreamRevertingCommits = new LinkedList<>();
      for (GitCommit commit : gitRepository.log("upstream/" + upstreamBranch, "origin/" + downstreamBranch)) {
         if (!commit.getShortMessage().startsWith("Merge pull request")) {
            upstreamCommits.push(commit);

            Matcher revertedCommitMatcher = revertedCommitPattern.matcher(commit.getFullMessage());

            if (revertedCommitMatcher.find()) {
               String revertedCommitName = revertedCommitMatcher.group(1);
               logger.info("upstream reverting commit: " + revertedCommitName);
               upstreamRevertingCommits.add(new AbstractMap.SimpleEntry<>(commit, revertedCommitMatcher.group(1)));
            }
         }
      }


      //Load upstream reverting chains
      Map<String, List<String>> upstreamRevertingChains = new HashMap<>();
      for (Map.Entry<GitCommit, String> upstreamRevertingCommitEntry : upstreamRevertingCommits) {
         if (!upstreamRevertingChains.containsKey(upstreamRevertingCommitEntry.getKey().getName())) {
            List<String> upstreamRevertingChain = new ArrayList();
            loadRevertingChain(upstreamRevertingCommitEntry, 0, upstreamRevertingCommits, upstreamRevertingChain);
            for(String upstreamRevertingChainItem : upstreamRevertingChain) {
               upstreamRevertingChains.put(upstreamRevertingChainItem, upstreamRevertingChain);
            }
         }
      }


      // Load cherry-picked commits
      Map<String, GitCommit> downstreamRevertedCommits = new HashMap<>();
      Deque<Map.Entry<GitCommit, ReleaseVersion>> downstreamCommits = new ArrayDeque<>();
      Map<String, Map.Entry<GitCommit, ReleaseVersion>> cherryPickedCommits = new HashMap<>();
      ReleaseVersion cherryPickedReleaseVersion = candidateReleaseVersion;
      for (GitCommit commit : gitRepository.log("origin/" + downstreamBranch, "upstream/" + upstreamBranch)) {

         //Check if the commit is reverted
         if (downstreamRevertedCommits.remove(commit.getName()) == null) {

            // Search prepare release commits to extract the release version
            Matcher prepareReleaseCommitMatcher = prepareReleaseCommitPattern.matcher(commit.getShortMessage());
            if (prepareReleaseCommitMatcher.find()) {
               logger.info("prepare release commit found: " + commit.getName() + " - " + commit.getShortMessage());
               cherryPickedReleaseVersion = ReleaseVersion.fromString(prepareReleaseCommitMatcher.group(1));
            } else {
               downstreamCommits.push(new AbstractMap.SimpleEntry<>(commit, cherryPickedReleaseVersion));

               //Check if the commit is cherry-picked
               GitCommit cherryPickedCommit;
               Matcher cherryPickedCommitMatcher = cherryPickedCommitPattern.matcher(commit.getFullMessage());
               if (cherryPickedCommitMatcher.find()) {
                  String cherryPickedCommitName = cherryPickedCommitMatcher.group(1);

                  cherryPickedCommit = upstreamCommits.stream().filter(
                     upstreamCommit -> upstreamCommit.getName().equals(cherryPickedCommitName)).findAny().orElse(null);
                  if (cherryPickedCommit == null) {
                     logger.error("cherry-picked commit not found: " + cherryPickedCommitName + " - " + commit.getShortMessage());

                     cherryPickedCommit = upstreamCommits.stream().filter(
                        upstreamCommit -> upstreamCommit.getShortMessage().equals(commit.getShortMessage())).findAny().orElse(null);

                     if (cherryPickedCommit != null) {
                        logger.warn("similar cherry-picked commit found: " + cherryPickedCommit.getName() + " - " + cherryPickedCommit.getShortMessage());
                     }
                  }

               } else {
                  cherryPickedCommit = upstreamCommits.stream().filter(
                     upstreamCommit -> upstreamCommit.getShortMessage().equals(commit.getShortMessage())).findAny().orElse(null);

                  if (cherryPickedCommit != null) {
                     logger.warn("similar cherry-picked commit found: " + cherryPickedCommit.getName() + " - " + cherryPickedCommit.getShortMessage());
                  }
               }

               if (cherryPickedCommit != null) {
                  cherryPickedCommits.put(cherryPickedCommit.getName(), new AbstractMap.SimpleEntry<>(commit, cherryPickedReleaseVersion));
               } else {
                  Matcher revertedCommitMatcher = revertedCommitPattern.matcher(commit.getFullMessage());

                  if (revertedCommitMatcher.find()) {
                     logger.info("downstream reverting commit: " + revertedCommitMatcher.group(1));
                     downstreamRevertedCommits.put(revertedCommitMatcher.group(1), commit);
                  }
               }
            }
         } else {
            logger.info("downstream reverted commit: " + commit.getName() + " - " + commit.getShortMessage());
         }
      }

      // Load confirmed commits
      Map<String, Commit> confirmedCommits = new HashMap<>();
      File confirmedCommitsFile = new File(confirmedCommitsFilename);
      if (confirmedCommitsFile.exists()) {
         Commit[] confirmedCommitsArray = gson.fromJson(FileUtils.readFileToString(
            confirmedCommitsFile, Charset.defaultCharset()), Commit[].class);
         for (Commit confirmedCommit : confirmedCommitsArray) {
            if (confirmedCommit.getRelease().equals(release)) {
               confirmedCommits.put(confirmedCommit.getUpstreamCommit(), confirmedCommit);
            }
         }
      }


      // Load confirmed downstream issues
      Map<String, Issue> confirmedDownstreamIssues = loadIssues(confirmedDownstreamIssueKeys, downstreamIssueManager);

      // Load excluded downstream issues
      Map<String, Issue> excludedDownstreamIssues = loadIssues(excludedDownstreamIssueKeys, downstreamIssueManager);

      // Load confirmed upstream issues
      Map<String, Issue> confirmedUpstreamIssues = loadIssues(confirmedUpstreamIssueKeys, upstreamIssueManager);

      // Load excluded upstream issues
      Map<String, Issue> excludedUpstreamIssues = loadIssues(excludedUpstreamIssueKeys, upstreamIssueManager);


      // Initialize commits dir
      File commitsDir = new File(targetDir, "commits");
      if (commitsDir.exists()) {
         FileUtils.deleteDirectory(commitsDir);
      }
      if (!commitsDir.mkdirs()) {
         throw new RuntimeException("Error creating commits directory");
      }


      // Init commit parser
      CommitProcessor commitProcessor = new CommitProcessor(
         candidateReleaseVersion,
         targetReleaseFormat,
         projectConfig,
         projectStreamName,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      commitProcessor.setCherryPickedCommits(cherryPickedCommits);
      commitProcessor.setConfirmedCommits(confirmedCommits);
      commitProcessor.setConfirmedDownstreamIssues(confirmedDownstreamIssues);
      commitProcessor.setExcludedDownstreamIssues(excludedDownstreamIssues);
      commitProcessor.setConfirmedUpstreamIssues(confirmedUpstreamIssues);
      commitProcessor.setExcludedUpstreamIssues(excludedUpstreamIssues);
      commitProcessor.setUpstreamRevertingChains(upstreamRevertingChains);
      commitProcessor.setDownstreamIssuesCustomerPriority(downstreamIssuesCustomerPriority);
      commitProcessor.setDownstreamIssuesSecurityImpact(downstreamIssuesSecurityImpact);
      commitProcessor.setCheckIncompleteCommits(checkIncompleteCommits);
      commitProcessor.setDryRun(dryRun);
      commitProcessor.setCheckCommand(checkCommand);
      commitProcessor.setCheckTestsCommand(checkTestsCommand);
      commitProcessor.setCommitsDir(commitsDir);

      //Delete current commits file
      File commitsFile;
      if (commitsFilename != null) {
         commitsFile = new File(commitsFilename);
      } else {
         commitsFile = new File(targetDir, downstreamRepositoryBaseName + "-" + release + "-commits.json");
      }
      if (commitsFile.exists()) {
         if (!commitsFile.delete()) {
            throw new RuntimeException("Error deleting commits file");
         }
      }


      // Process upstream commits
      List<Commit> commits = new ArrayList<>();
      try {
         for (GitCommit upstreamCommit : upstreamCommits) {
            logger.info("Upstream commit: " + upstreamCommit.getName() + " - " + upstreamCommit.getShortMessage());

            commits.add(commitProcessor.process(upstreamCommit));
         }
      } finally {
         // Store commits

         // Ignore SKIPPED commits and DONE commits without EXECUTED tasks
         //FileUtils.writeStringToFile(commitsFile, gson.toJson(commits.stream()
         //   .filter(commit -> (commit.getState() != Commit.State.SKIPPED && commit.getState() != Commit.State.DONE) ||
         //      (commit.getState() == Commit.State.DONE && commit.getTasks().stream()
         //         .anyMatch(commitTask -> CommitTask.State.EXECUTED.equals(commitTask.getState()))))
         //   .collect(Collectors.toList())), Charset.defaultCharset());

         // Ignore DONE commits of other releases
         //FileUtils.writeStringToFile(commitsFile, gson.toJson(commits.stream()
         //   .filter(commit -> (commit.getState() != Commit.State.DONE) ||
         //      (commit.getState() == Commit.State.DONE && commit.getRelease().equals(release)))
         //   .collect(Collectors.toList())), Charset.defaultCharset());

         FileUtils.writeStringToFile(commitsFile, gson.toJson(commits), Charset.defaultCharset());

         // Store upstream issues
         upstreamIssueManager.storeIssues(upstreamIssuesFile);


         // Store downstream issues
         downstreamIssueManager.storeIssues(downstreamIssuesFile);
      }

      File payloadFile;
      if (payloadFilename != null) {
         payloadFile = new File(payloadFilename);
      } else {
         payloadFile = new File(targetDir, downstreamRepositoryBaseName + "-payload.csv");
      }

      try (CSVPrinter printer = new CSVPrinter(new FileWriter(payloadFile), CSVFormat.DEFAULT
         .withHeader("state", "release", "upstreamCommit", "downstreamCommit", "author", "summary", "upstreamIssue", "downstreamIssues", "upstreamTestCoverage"))) {

         for (Map.Entry<GitCommit, ReleaseVersion> downstreamCommit : downstreamCommits) {
            Commit processedCommit = commits.stream().filter(commit -> downstreamCommit.getKey().getName().equals(commit.getDownstreamCommit())).findFirst().orElse(null);

            printer.printRecord("DONE", downstreamCommit.getValue(), processedCommit != null ? processedCommit.getUpstreamCommit() : "", downstreamCommit.getKey().getName(), downstreamCommit.getKey().getAuthorName(), downstreamCommit.getKey().getShortMessage(),
               processedCommit != null ? processedCommit.getUpstreamIssue() : "", String.join(" ", processedCommit != null ? processedCommit.getDownstreamIssues() : Collections.emptyList()), processedCommit != null && processedCommit.getTests().size() > 0);
         }

         for (Commit commit : commits.stream()
            .filter(commit -> (commit.getState() != Commit.State.SKIPPED && commit.getState() != Commit.State.DONE))
            .collect(Collectors.toList())) {
            printer.printRecord(commit.getState(), commit.getRelease(), commit.getUpstreamCommit(), commit.getDownstreamCommit(), commit.getAuthor(), commit.getSummary(),
               commit.getUpstreamIssue(), String.join(" ", commit.getDownstreamIssues()), commit.getTests().size() > 0);
         }
      }

      gitRepository.close();
   }

   private static Map<String, Issue> loadIssues(String issueKeys, IssueManager issueManager) {
      Map<String, Issue> issues = null;
      if (issueKeys != null) {
         issues = new HashMap<>();
         for (String confirmedDownstreamIssueKey : issueKeys.split(",")) {
            Issue confirmedDownstreamIssue = issueManager.getIssue(confirmedDownstreamIssueKey);
            if (confirmedDownstreamIssue == null) {
               logger.warn("Issue not found: " + confirmedDownstreamIssueKey);
            }
            issues.put(confirmedDownstreamIssueKey, confirmedDownstreamIssue);
         }
      }
      return issues;
   }

   private static void loadRevertingChain(Map.Entry<GitCommit, String> revertingCommitEntry, int revertingChainCount, Queue<Map.Entry<GitCommit, String>> revertingCommits, List<String> revertingChain) {
      revertingChain.add(revertingCommitEntry.getKey().getName());

      Map.Entry<GitCommit, String> recursiveRevertingCommitEntry = revertingCommits.stream().filter(gitCommitStringEntry -> gitCommitStringEntry.getKey().getName().equals(revertingCommitEntry.getValue())).findFirst().orElse(null);

      if (recursiveRevertingCommitEntry != null) {
         loadRevertingChain(recursiveRevertingCommitEntry, revertingChainCount + 1, revertingCommits, revertingChain);
      } else {
         revertingChain.add(revertingCommitEntry.getValue());
      }
   }
}