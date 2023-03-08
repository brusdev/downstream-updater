package dev.brus.downstream.updater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.brus.downstream.updater.git.GitCommit;
import dev.brus.downstream.updater.git.GitRepository;
import dev.brus.downstream.updater.git.JGitRepository;
import dev.brus.downstream.updater.issues.ApacheIssueStateMachine;
import dev.brus.downstream.updater.issues.Issue;
import dev.brus.downstream.updater.issues.IssueCustomerPriority;
import dev.brus.downstream.updater.issues.IssueManager;
import dev.brus.downstream.updater.issues.IssueSecurityImpact;
import dev.brus.downstream.updater.issues.JiraIssueManager;
import dev.brus.downstream.updater.issues.RedHatIssueStateMachine;
import dev.brus.downstream.updater.users.User;
import dev.brus.downstream.updater.users.UserResolver;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
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
import java.util.List;
import java.util.Map;
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
   private final static Pattern prepareReleaseCommitPattern = Pattern.compile("Prepare release ([0-9]+\\.[0-9]+\\.[0-9]+.CR[0-9]+)");

   private static final String CONFIRMED_COMMITS_OPTION = "confirmed-commits";
   private static final String CONFIRMED_UPSTREAM_ISSUES_OPTION = "confirmed-upstream-issues";
   private static final String EXCLUDED_UPSTREAM_ISSUES_OPTION = "excluded-upstream-issues";
   private static final String CONFIRMED_DOWNSTREAM_ISSUES_OPTION = "confirmed-downstream-issues";
   private static final String EXCLUDED_DOWNSTREAM_ISSUES_OPTION = "excluded-downstream-issues";
   private static final String UPSTREAM_REPOSITORY_OPTION = "upstream-repository";
   private static final String UPSTREAM_BRANCH_OPTION = "upstream-branch";
   private static final String UPSTREAM_ISSUES_AUTH_STRING_OPTION = "upstream-issues-auth-string";
   private static final String DOWNSTREAM_REPOSITORY_OPTION = "downstream-repository";
   private static final String DOWNSTREAM_BRANCH_OPTION = "downstream-branch";
   private static final String DOWNSTREAM_ISSUES_AUTH_STRING_OPTION = "downstream-issues-auth-string";
   private static final String DOWNSTREAM_ISSUES_CUSTOMER_PRIORITY = "downstream-issues-customer-priority";
   private static final String DOWNSTREAM_ISSUES_SECURITY_IMPACT = "downstream-issues-security-impact";
   private static final String RELEASE_OPTION = "release";
   private static final String QUALIFIER_OPTION = "qualifier";
   private static final String ASSIGNEE_OPTION = "assignee";
   private static final String CHECK_INCOMPLETE_COMMITS_OPTION = "check-incomplete-commits";
   private static final String DRY_RUN_OPTION = "dry-run";
   private static final String SKIP_COMMIT_TEST_OPTION = "skip-commit-test";


   public static void main(String[] args) throws Exception {
      // Parse arguments
      Options options = new Options();
      options.addOption(createOption("a", ASSIGNEE_OPTION, true, true, false, "the default assignee, i.e. dbruscin"));
      options.addOption(createOption(null, RELEASE_OPTION, true, true, false, "the release, i.e. AMQ 7.10.0.GA"));
      options.addOption(createOption(null, QUALIFIER_OPTION, true, true, false, "the qualifier, i.e. CR1"));
      options.addOption(createOption(null, UPSTREAM_REPOSITORY_OPTION, true, true, false, "the upstream repository to cherry-pick from, i.e. https://github.com/apache/activemq-artemis.git"));
      options.addOption(createOption(null, UPSTREAM_BRANCH_OPTION, true, true, false, "the upstream branch to cherry-pick from, i.e. main"));
      options.addOption(createOption(null, DOWNSTREAM_REPOSITORY_OPTION, true, true, false, "the downstream repository to cherry-pick to, i.e. https://github.com/rh-messaging/activemq-artemis.git"));
      options.addOption(createOption(null, DOWNSTREAM_BRANCH_OPTION, true, true, false, "the downstream branch to cherry-pick to, i.e. 2.16.0.jbossorg-x"));

      options.addOption(createOption(null, CONFIRMED_COMMITS_OPTION, false, true, false, "the confirmed commits"));
      options.addOption(createOption(null, CONFIRMED_DOWNSTREAM_ISSUES_OPTION, false, true, true, "the confirmed downstream issues, commits related to other downstream issues with a different target release will be skipped"));
      options.addOption(createOption(null, EXCLUDED_DOWNSTREAM_ISSUES_OPTION, false, true, true, "the excluded downstream issues, commits related to other downstream issues with a different target release will be skipped"));
      options.addOption(createOption(null, CONFIRMED_UPSTREAM_ISSUES_OPTION, false, true, true, "the confirmed upstream issues, commits related to other upstream issues without a downstream issue will be skipped"));
      options.addOption(createOption(null, EXCLUDED_UPSTREAM_ISSUES_OPTION, false, true, true, "the excluded upstream issues, commits related to other upstream issues without a downstream issue will be skipped"));

      options.addOption(createOption(null, UPSTREAM_ISSUES_AUTH_STRING_OPTION, false, true, false, "the auth string to access upstream issues, i.e. \"Bearer ...\""));
      options.addOption(createOption(null, DOWNSTREAM_ISSUES_AUTH_STRING_OPTION, false, true, false, "the auth string to access downstream issues, i.e. \"Bearer ...\""));
      options.addOption(createOption(null, DOWNSTREAM_ISSUES_CUSTOMER_PRIORITY, false, true, false, "the customer priority to filter downstream issues, i.e. HIGH"));
      options.addOption(createOption(null, DOWNSTREAM_ISSUES_SECURITY_IMPACT, false, true, false, "the security impact to filter downstream issues, i.e. IMPORTANT"));

      options.addOption(createOption(null, CHECK_INCOMPLETE_COMMITS_OPTION, false, false, true, "check tasks of cherry-picked commits"));
      options.addOption(createOption(null, DRY_RUN_OPTION, false, false, false, "dry run"));
      options.addOption(createOption(null, SKIP_COMMIT_TEST_OPTION, false, false, true, "skip commit test"));

      CommandLine line;
      CommandLineParser parser = new DefaultParser();

      try {
         line = parser.parse(options, args);
      } catch (ParseException e) {
         throw new RuntimeException("Error on parsing arguments", e);
      }

      String assignee = line.getOptionValue(ASSIGNEE_OPTION);

      String release = line.getOptionValue(RELEASE_OPTION);
      String qualifier = line.getOptionValue(QUALIFIER_OPTION);
      ReleaseVersion candidateReleaseVersion = new ReleaseVersion(release + "." + qualifier);

      String upstreamRepository = line.getOptionValue(UPSTREAM_REPOSITORY_OPTION);
      String upstreamBranch = line.getOptionValue(UPSTREAM_BRANCH_OPTION);

      String downstreamRepository = line.getOptionValue(DOWNSTREAM_REPOSITORY_OPTION);
      String downstreamBranch = line.getOptionValue(DOWNSTREAM_BRANCH_OPTION);

      String downstreamIssuesAuthString = line.getOptionValue(DOWNSTREAM_ISSUES_AUTH_STRING_OPTION);

      IssueCustomerPriority downstreamIssuesCustomerPriority = IssueCustomerPriority.LOW;
      if (line.hasOption(DOWNSTREAM_ISSUES_CUSTOMER_PRIORITY)) {
         downstreamIssuesCustomerPriority = IssueCustomerPriority.fromName(
            line.getOptionValue(DOWNSTREAM_ISSUES_CUSTOMER_PRIORITY));
      }

      IssueSecurityImpact downstreamIssuesSecurityImpact = IssueSecurityImpact.LOW;
      if (line.hasOption(DOWNSTREAM_ISSUES_SECURITY_IMPACT)) {
         downstreamIssuesSecurityImpact = IssueSecurityImpact.fromName(
            line.getOptionValue(DOWNSTREAM_ISSUES_SECURITY_IMPACT));
      }

      String upstreamIssuesAuthString = line.getOptionValue(UPSTREAM_ISSUES_AUTH_STRING_OPTION);

      String confirmedCommitsFilename = null;
      if (line.hasOption(CONFIRMED_COMMITS_OPTION)) {
         confirmedCommitsFilename = line.getOptionValue(CONFIRMED_COMMITS_OPTION);
      }

      String confirmedDownstreamIssueKeys = null;
      if (line.hasOption(CONFIRMED_DOWNSTREAM_ISSUES_OPTION)) {
         confirmedDownstreamIssueKeys = line.getOptionValue(CONFIRMED_DOWNSTREAM_ISSUES_OPTION, "");
      }

      String excludedDownstreamIssueKeys = null;
      if (line.hasOption(EXCLUDED_DOWNSTREAM_ISSUES_OPTION)) {
         excludedDownstreamIssueKeys = line.getOptionValue(EXCLUDED_DOWNSTREAM_ISSUES_OPTION, "");
      }

      String confirmedUpstreamIssueKeys = null;
      if (line.hasOption(CONFIRMED_UPSTREAM_ISSUES_OPTION)) {
         confirmedUpstreamIssueKeys = line.getOptionValue(CONFIRMED_UPSTREAM_ISSUES_OPTION, "");
      }

      String excludedUpstreamIssueKeys = null;
      if (line.hasOption(EXCLUDED_UPSTREAM_ISSUES_OPTION)) {
         excludedUpstreamIssueKeys = line.getOptionValue(EXCLUDED_UPSTREAM_ISSUES_OPTION, "");
      }

      boolean checkIncompleteCommits = true;
      if (line.hasOption(CHECK_INCOMPLETE_COMMITS_OPTION)) {
         checkIncompleteCommits = Boolean.parseBoolean(line.getOptionValue(CHECK_INCOMPLETE_COMMITS_OPTION, "true"));
      }

      boolean dryRun = line.hasOption(DRY_RUN_OPTION);

      boolean skipCommitTest = false;
      if (line.hasOption(SKIP_COMMIT_TEST_OPTION)) {
         skipCommitTest = Boolean.parseBoolean(line.getOptionValue(SKIP_COMMIT_TEST_OPTION, "true"));
      }

      // Initialize target directory
      File targetDir = new File("target");
      if (!targetDir.exists()) {
         if (!targetDir.mkdir()) {
            throw new RuntimeException("Error creating target directory");
         }
      }


      // Initialize git
      GitRepository gitRepository = new JGitRepository();
      File repoDir = new File(targetDir, "activemq-artemis-repo");

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


      // Load upstream issues
      File upstreamIssuesFile = new File(targetDir, "upstream-issues.json");
      IssueManager upstreamIssueManager = new JiraIssueManager(
         "https://issues.apache.org/jira/rest/api/2", upstreamIssuesAuthString, "ARTEMIS", new ApacheIssueStateMachine());
      if (upstreamIssuesFile.exists()) {
         upstreamIssueManager.loadIssues(upstreamIssuesFile);
      } else {
         upstreamIssueManager.loadIssues(false);
      }


      // Load downstream issues
      File downstreamIssuesFile = new File(targetDir, "downstream-issues.json");
      IssueManager downstreamIssueManager = new JiraIssueManager("https://issues.redhat.com/rest/api/2", downstreamIssuesAuthString, "ENTMQBR", new RedHatIssueStateMachine());
      if (downstreamIssuesFile.exists()) {
         downstreamIssueManager.loadIssues(downstreamIssuesFile);
      } else {
         downstreamIssueManager.loadIssues(true);

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
      HashMap<String, GitCommit> upstreamRevertingCommits = new HashMap<>();
      for (GitCommit commit : gitRepository.log("upstream/" + upstreamBranch, "origin/" + downstreamBranch)) {
         if (!commit.getShortMessage().startsWith("Merge pull request")) {
            upstreamCommits.push(commit);

            Matcher revertedCommitMatcher = revertedCommitPattern.matcher(commit.getFullMessage());

            if (revertedCommitMatcher.find()) {
               logger.info("upstream reverting commit: " + revertedCommitMatcher.group(1));
               upstreamRevertingCommits.put(revertedCommitMatcher.group(1), commit);
            }
         }
      }


      // Load cherry-picked commits
      HashMap<String, GitCommit> downstreamRevertedCommits = new HashMap<>();
      Deque<Map.Entry<GitCommit, ReleaseVersion>> downstreamCommits = new ArrayDeque<>();
      HashMap<String, Map.Entry<GitCommit, ReleaseVersion>> cherryPickedCommits = new HashMap<>();
      ReleaseVersion cherryPickedReleaseVersion = candidateReleaseVersion;
      for (GitCommit commit : gitRepository.log("origin/" + downstreamBranch, "upstream/" + upstreamBranch)) {

         //Check if the commit is reverted
         if (downstreamRevertedCommits.remove(commit.getName()) == null) {

            // Search prepare release commits to extract the release version
            Matcher prepareReleaseCommitMatcher = prepareReleaseCommitPattern.matcher(commit.getShortMessage());
            if (prepareReleaseCommitMatcher.find()) {
               logger.info("prepare release commit found: " + commit.getName() + " - " + commit.getShortMessage());
               cherryPickedReleaseVersion = new ReleaseVersion(prepareReleaseCommitMatcher.group(1));
            } else if (commit.getShortMessage().startsWith("7.8.")) {
               logger.info("legacy release commit found: " + commit.getName() + " - " + commit.getShortMessage());
               cherryPickedReleaseVersion = new ReleaseVersion(commit.getShortMessage());
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


      //Skip upstream reverted commits not cherry-picked
      for (Map.Entry<String, GitCommit> upstreamRevertingCommitEntry : upstreamRevertingCommits.entrySet()) {

         GitCommit upstreamRevertedCommit = upstreamCommits.stream().filter(
            upstreamCommit -> upstreamCommit.getName().equals(upstreamRevertingCommitEntry.getKey())).findAny().orElse(null);

         if (upstreamRevertedCommit != null && !cherryPickedCommits.containsKey(upstreamRevertingCommitEntry.getKey())) {
            logger.info("upstream reverted commit: " + upstreamRevertedCommit.getName() + " - " + upstreamRevertedCommit.getShortMessage());
            upstreamCommits.remove(upstreamRevertedCommit);
            upstreamCommits.remove(upstreamRevertingCommitEntry.getValue());
         }
      }


      // Load confirmed commits
      Map<String, Commit> confirmedCommits = new HashMap<>();
      if (confirmedCommitsFilename != null) {
         File confirmedCommitsFile = new File(confirmedCommitsFilename);
         Commit[] confirmedCommitsArray = gson.fromJson(FileUtils.readFileToString(
            confirmedCommitsFile, Charset.defaultCharset()), Commit[].class);
         for (Commit confirmedCommit : confirmedCommitsArray) {
            confirmedCommits.put(confirmedCommit.getUpstreamCommit(), confirmedCommit);
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


      // Initialize test dir
      File commitTestsDir = new File(targetDir, "commit-tests");
      if (commitTestsDir.exists()) {
         FileUtils.deleteDirectory(commitTestsDir);
      }
      if (!commitTestsDir.mkdirs()) {
         throw new RuntimeException("Error creating commit tests directory");
      }


      // Init commit parser
      CommitProcessor commitProcessor = new CommitProcessor(
         gitRepository, candidateReleaseVersion, upstreamIssueManager, downstreamIssueManager, userResolver,
         cherryPickedCommits, confirmedCommits, confirmedDownstreamIssues, excludedDownstreamIssues, confirmedUpstreamIssues, excludedUpstreamIssues,
         downstreamIssuesCustomerPriority, downstreamIssuesSecurityImpact, checkIncompleteCommits, dryRun, skipCommitTest, commitTestsDir);


      //Delete current commits file
      File commitsFile = new File(targetDir, "commits.json");
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
         FileUtils.writeStringToFile(commitsFile, gson.toJson(commits.stream()
            .filter(commit -> (commit.getState() != Commit.State.SKIPPED && commit.getState() != Commit.State.DONE) ||
               (commit.getState() == Commit.State.DONE && commit.getTasks().stream()
                  .anyMatch(commitTask -> CommitTask.State.EXECUTED.equals(commitTask.getState()))))
            .collect(Collectors.toList())), Charset.defaultCharset());
         //FileUtils.writeStringToFile(commitsFile, gson.toJson(commits), Charset.defaultCharset());

         // Store upstream issues
         upstreamIssueManager.storeIssues(upstreamIssuesFile);


         // Store downstream issues
         downstreamIssueManager.storeIssues(downstreamIssuesFile);
      }

      File payloadFile = new File(targetDir, "payload.csv");
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
            printer.printRecord(commit.getState(), commit.getReleaseVersion(), commit.getUpstreamCommit(), commit.getDownstreamCommit(), commit.getAuthor(), commit.getSummary(),
                                commit.getUpstreamIssue(), String.join(" ", commit.getDownstreamIssues()), commit.getTests().size() > 0);
         }
      }
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

   private static Option createOption(String opt, String longOpt, boolean required, boolean hasArg, boolean hasOptionalArg, String description) {
      Option option = new Option(opt, longOpt, hasArg, description);
      option.setRequired(required);
      option.setOptionalArg(hasOptionalArg);

      return option;
   }
}