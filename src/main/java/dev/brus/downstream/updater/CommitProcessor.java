/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.brus.downstream.updater;

import dev.brus.downstream.updater.git.GitCommit;
import dev.brus.downstream.updater.git.GitRepository;
import dev.brus.downstream.updater.issues.Issue;
import dev.brus.downstream.updater.issues.IssueCustomerPriority;
import dev.brus.downstream.updater.issues.IssueManager;
import dev.brus.downstream.updater.issues.IssueSecurityImpact;
import dev.brus.downstream.updater.users.User;
import dev.brus.downstream.updater.users.UserResolver;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommitProcessor {
   private final static Logger logger = LoggerFactory.getLogger(CommitProcessor.class);

   private final static Pattern upstreamIssuePattern = Pattern.compile("ARTEMIS-[0-9]+");
   private final static Pattern downstreamLinePattern = Pattern.compile("downstream:.*");
   private final static Pattern downstreamIssuePattern = Pattern.compile("ENTMQBR-[0-9]+");



   private static final String COMMITTER_NAME = "rh-messaging-ci";
   private static final String COMMITTER_EMAIL = "messaging-infra@redhat.com";

   private static final String FUTURE_GA_RELEASE = "Future GA";

   private static final String TEST_PATH = "src/test/java/";

   private GitRepository gitRepository;
   private ReleaseVersion candidateReleaseVersion;
   private IssueManager upstreamIssueManager;
   private IssueManager downstreamIssueManager;
   private UserResolver userResolver;
   private Map<String, Map.Entry<GitCommit, ReleaseVersion>> cherryPickedCommits;
   private Map<String, Commit> confirmedCommits;
   private Map<String, Issue> confirmedDownstreamIssues;
   private Map<String, Issue> excludedDownstreamIssues;
   private Map<String, Issue> confirmedUpstreamIssues;
   private Map<String, Issue> excludedUpstreamIssues;
   private IssueCustomerPriority downstreamIssuesCustomerPriority;
   private IssueSecurityImpact downstreamIssuesSecurityImpact;
   private boolean checkIncompleteCommits;
   private boolean dryRun;
   private boolean skipCommitTest;
   private File commitTestsDir;

   public GitRepository getGitRepository() {
      return gitRepository;
   }

   public ReleaseVersion getCandidateReleaseVersion() {
      return candidateReleaseVersion;
   }

   public IssueManager getUpstreamIssueManager() {
      return upstreamIssueManager;
   }

   public IssueManager getDownstreamIssueManager() {
      return downstreamIssueManager;
   }

   public UserResolver getUserResolver() {
      return userResolver;
   }

   public Map<String, Map.Entry<GitCommit, ReleaseVersion>> getCherryPickedCommits() {
      return cherryPickedCommits;
   }

   public CommitProcessor setCherryPickedCommits(Map<String, Map.Entry<GitCommit, ReleaseVersion>> cherryPickedCommits) {
      this.cherryPickedCommits = cherryPickedCommits;
      return this;
   }

   public Map<String, Commit> getConfirmedCommits() {
      return confirmedCommits;
   }

   public CommitProcessor setConfirmedCommits(Map<String, Commit> confirmedCommits) {
      this.confirmedCommits = confirmedCommits;
      return this;
   }

   public Map<String, Issue> getConfirmedDownstreamIssues() {
      return confirmedDownstreamIssues;
   }

   public CommitProcessor setConfirmedDownstreamIssues(Map<String, Issue> confirmedDownstreamIssues) {
      this.confirmedDownstreamIssues = confirmedDownstreamIssues;
      return this;
   }

   public Map<String, Issue> getExcludedDownstreamIssues() {
      return excludedDownstreamIssues;
   }

   public CommitProcessor setExcludedDownstreamIssues(Map<String, Issue> excludedDownstreamIssues) {
      this.excludedDownstreamIssues = excludedDownstreamIssues;
      return this;
   }

   public Map<String, Issue> getConfirmedUpstreamIssues() {
      return confirmedUpstreamIssues;
   }

   public CommitProcessor setConfirmedUpstreamIssues(Map<String, Issue> confirmedUpstreamIssues) {
      this.confirmedUpstreamIssues = confirmedUpstreamIssues;
      return this;
   }

   public Map<String, Issue> getExcludedUpstreamIssues() {
      return excludedUpstreamIssues;
   }

   public CommitProcessor setExcludedUpstreamIssues(Map<String, Issue> excludedUpstreamIssues) {
      this.excludedUpstreamIssues = excludedUpstreamIssues;
      return this;
   }

   public IssueCustomerPriority getDownstreamIssuesCustomerPriority() {
      return downstreamIssuesCustomerPriority;
   }

   public CommitProcessor setDownstreamIssuesCustomerPriority(IssueCustomerPriority downstreamIssuesCustomerPriority) {
      this.downstreamIssuesCustomerPriority = downstreamIssuesCustomerPriority;
      return this;
   }

   public IssueSecurityImpact getDownstreamIssuesSecurityImpact() {
      return downstreamIssuesSecurityImpact;
   }

   public CommitProcessor setDownstreamIssuesSecurityImpact(IssueSecurityImpact downstreamIssuesSecurityImpact) {
      this.downstreamIssuesSecurityImpact = downstreamIssuesSecurityImpact;
      return this;
   }

   public boolean isCheckIncompleteCommits() {
      return checkIncompleteCommits;
   }

   public CommitProcessor setCheckIncompleteCommits(boolean checkIncompleteCommits) {
      this.checkIncompleteCommits = checkIncompleteCommits;
      return this;
   }

   public boolean isDryRun() {
      return dryRun;
   }

   public CommitProcessor setDryRun(boolean dryRun) {
      this.dryRun = dryRun;
      return this;
   }

   public boolean isSkipCommitTest() {
      return skipCommitTest;
   }

   public CommitProcessor setSkipCommitTest(boolean skipCommitTest) {
      this.skipCommitTest = skipCommitTest;
      return this;
   }

   public File getCommitTestsDir() {
      return commitTestsDir;
   }

   public CommitProcessor setCommitTestsDir(File commitTestsDir) {
      this.commitTestsDir = commitTestsDir;
      return this;
   }

   public CommitProcessor(
      ReleaseVersion candidateReleaseVersion,
      GitRepository gitRepository,
      IssueManager upstreamIssueManager,
      IssueManager downstreamIssueManager,
      UserResolver userResolver) {

      this.candidateReleaseVersion = candidateReleaseVersion;
      this.gitRepository = gitRepository;
      this.upstreamIssueManager = upstreamIssueManager;
      this.downstreamIssueManager = downstreamIssueManager;
      this.userResolver = userResolver;

      this.cherryPickedCommits = Collections.emptyMap();
      this.confirmedCommits = Collections.emptyMap();
      this.confirmedDownstreamIssues = Collections.emptyMap();
      this.excludedDownstreamIssues = Collections.emptyMap();
      this.confirmedUpstreamIssues = Collections.emptyMap();
      this.excludedUpstreamIssues = Collections.emptyMap();
      this.downstreamIssuesCustomerPriority = IssueCustomerPriority.NONE;
      this.downstreamIssuesSecurityImpact = IssueSecurityImpact.NONE;
      this.checkIncompleteCommits = true;
      this.dryRun = false;
      this.skipCommitTest = false;
      this.commitTestsDir = new File("commit-tests");
   }

   public Commit process(GitCommit upstreamCommit) throws Exception {
      logger.info("Processing " + upstreamCommit.getName() + " - " + upstreamCommit.getShortMessage());

      ReleaseVersion candidateReleaseVersion = this.candidateReleaseVersion;
      Map.Entry<GitCommit, ReleaseVersion> cherryPickedCommit = cherryPickedCommits.get(upstreamCommit.getName());
      if (cherryPickedCommit != null) {
         candidateReleaseVersion = cherryPickedCommit.getValue();
      }

      String release = "AMQ " + candidateReleaseVersion.getMajor() + "." +
         candidateReleaseVersion.getMinor() + "." +
         candidateReleaseVersion.getPatch() + ".GA";
      String qualifier = candidateReleaseVersion.getQualifier();


      Commit confirmedCommit = confirmedCommits.get(upstreamCommit.getName());
      List<CommitTask> confirmedTasks = null;
      if (confirmedCommit != null) {
         confirmedTasks = confirmedCommit.getTasks();
      }

      Commit commit = new Commit().setUpstreamCommit(upstreamCommit.getName())
         .setSummary(upstreamCommit.getShortMessage()).setState(Commit.State.DONE);

      Matcher upstreamIssueMatcher = upstreamIssuePattern.matcher(upstreamCommit.getShortMessage());

      String upstreamIssueKey = null;
      if (upstreamIssueMatcher.find()) {
         upstreamIssueKey = upstreamIssueMatcher.group();
      } else if (cherryPickedCommit == null) {
         logger.info("SKIPPED because the commit message does not include an upstream issue key");
         commit.setState(Commit.State.SKIPPED).setReason("NO_UPSTREAM_ISSUE");
         return commit;
      }

      Issue upstreamIssue = null;
      if (upstreamIssueKey != null) {
         commit.setUpstreamIssue(upstreamIssueKey);

         if (upstreamIssueMatcher.find() && cherryPickedCommit == null) {
            logger.warn("SKIPPED because the commit message includes multiple upstream issue keys");
            commit.setState(Commit.State.INVALID).setReason("MULTIPLE_UPSTREAM_ISSUES");
            return commit;
         }

         upstreamIssue = upstreamIssueKey != null ? upstreamIssueManager.getIssue(upstreamIssueKey) : null;

         if (upstreamIssue == null && cherryPickedCommit == null) {
            logger.warn("SKIPPED because the upstream issue is not found: " + upstreamIssueKey);
            commit.setState(Commit.State.INVALID).setReason("UPSTREAM_ISSUE_NOT_FOUND");
            return commit;
         }
      }


      commit.setAuthor(upstreamCommit.getAuthorName());
      commit.setRelease(candidateReleaseVersion.toString());
      commit.setDownstreamCommit(cherryPickedCommit != null ? cherryPickedCommit.getKey().getName() : null);
      commit.setTests(getCommitTests(upstreamCommit));


      // Get downstreamIssueKeys
      List<String> downstreamIssueKeys = new ArrayList<>();

      if (upstreamIssue != null) {
         downstreamIssueKeys.addAll(upstreamIssue.getIssues());
      }

      if (cherryPickedCommit != null) {
         Matcher downstreamLineMatcher = downstreamLinePattern.matcher(cherryPickedCommit.getKey().getFullMessage());

         if (downstreamLineMatcher.find()) {
            Matcher downstreamIssueMatcher = downstreamIssuePattern.matcher(downstreamLineMatcher.group());

            while (downstreamIssueMatcher.find()) {
               String downstreamIssueKey = downstreamIssueMatcher.group();
               Issue downstreamIssue = downstreamIssueKey != null ? downstreamIssueManager.getIssue(downstreamIssueKey) : null;

               if (downstreamIssue != null) {
                  if (!downstreamIssueKeys.contains(downstreamIssueKey)) {
                     downstreamIssueKeys.add(downstreamIssueKey);
                  }
               } else {
                  logger.warn("Downstream issue not found: " + downstreamIssueKey);
               }
            }
         }
      }


      // Select downstream issues
      String selectedTargetRelease = null;
      List<Issue> selectedDownstreamIssues = null;
      List<Issue> allDownstreamIssues = new ArrayList<>();
      Map<String, List<Issue>> downstreamIssuesGroups = groupDownstreamIssuesByTargetRelease(downstreamIssueKeys, release);
      if (downstreamIssuesGroups != null && downstreamIssuesGroups.size() > 0) {
         selectedTargetRelease = selectRelease(downstreamIssuesGroups.keySet(), release);
         selectedDownstreamIssues = downstreamIssuesGroups.get(selectedTargetRelease);

         for (List<Issue> downstreamIssuesGroup : downstreamIssuesGroups.values()) {
            for (Issue downstreamIssue : downstreamIssuesGroup) {
               allDownstreamIssues.add(downstreamIssue);
               commit.getDownstreamIssues().add(downstreamIssue.getKey());
            }
         }
      }

      commit.setAssignee(getAssignee(upstreamCommit, upstreamIssue, selectedDownstreamIssues).getUsername());

      // The commits related to downstream issues fixed in another release requires
      // a downstream release issue if they are cherry-picked to a branch after the first release
      boolean requireReleaseIssues =  candidateReleaseVersion.getPatch() > 0 ||
         !isAnyDownstreamIssueDone(allDownstreamIssues);

      if (selectedDownstreamIssues != null && selectedDownstreamIssues.size() > 0) {
         // Commit related to downstream issues

         if (cherryPickedCommit != null) {
            // Commit cherry-picked, check the downstream issues

            if (this.candidateReleaseVersion.compareWithoutQualifierTo(candidateReleaseVersion) == 0) {
               if(isCurrentOrFutureRelease(release, selectedTargetRelease)) {
                  if (processDownstreamIssues(commit, release, qualifier, selectedDownstreamIssues, confirmedTasks)) {
                     commit.setState(Commit.State.DONE);
                  } else {
                     commit.setState(Commit.State.INCOMPLETE);
                  }
               } else {
                  if (requireReleaseIssues) {
                     // The commits related to downstream issues fixed in another release requires
                     // a downstream release issue if they are cherry-picked to a branch after the first release
                     logger.warn("INCOMPLETE because no downstream issues with the required target release");

                     if (cloneDownstreamIssues(commit, release, qualifier, selectedDownstreamIssues, confirmedTasks)) {
                        commit.setState(Commit.State.DONE);
                     } else {
                        commit.setState(Commit.State.INCOMPLETE).setReason("NO_DOWNSTREAM_ISSUES_WITH_REQUIRED_TARGET_RELEASE");
                     }
                  }
               }
            }
         } else {
            // Commit not cherry-picked

            if (isCommitExcluded(allDownstreamIssues)) {
               commit.setState(Commit.State.SKIPPED).setReason("DOWNSTREAM_ISSUE_NO-BACKPORT-NEEDED");
            } else if (isCurrentOrFutureRelease(release, selectedTargetRelease)) {
               // The selected downstream issues have the required target release

               if (processCommitTask(commit, release, qualifier, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, upstreamCommit.getName(),
                                     selectedDownstreamIssues.stream().map(Issue::getKey).collect(Collectors.joining(",")), confirmedTasks)) {

                  if (processDownstreamIssues(commit, release, qualifier, selectedDownstreamIssues, confirmedTasks)) {
                     commit.setState(Commit.State.DONE);
                  } else {
                     commit.setState(Commit.State.INCOMPLETE);
                  }
               } else {
                  commit.setState(Commit.State.TODO);
               }
            } else {
               // The selected downstream issues do not have the required target release

               if (isCommitRequired(allDownstreamIssues)) {
                  // At least one downstream issue match sufficient criteria to cherry-pick the commit

                  if (requireReleaseIssues) {
                     // The commits related to downstream issues fixed in another release requires
                     // a downstream release issue if they are cherry-picked to a branch after the first release
                     if (cloneDownstreamIssues(commit, release, qualifier, selectedDownstreamIssues, confirmedTasks)) {
                        commit.setState(Commit.State.DONE);
                     } else {
                        commit.setState(Commit.State.NEW).setReason("NO_DOWNSTREAM_ISSUES_WITH_REQUIRED_TARGET_RELEASE");
                     }
                  } else {
                     // The commits related to a downstream issue fixed in another release don't require
                     // a downstream release issue if they are cherry-picked to a branch before the first release
                     if (processCommitTask(commit, release, qualifier, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, upstreamCommit.getName(),
                                           selectedDownstreamIssues.stream().map(Issue::getKey).collect(Collectors.joining(",")), confirmedTasks)) {

                        commit.setState(Commit.State.DONE);
                     } else {
                        commit.setState(Commit.State.TODO);
                     }
                  }
               } else {
                  commit.setState(Commit.State.SKIPPED).setReason("DOWNSTREAM_ISSUE_NOT_SUFFICIENT");
               }
            }
         }
      } else {
         // No selected downstream issues

         if (cherryPickedCommit != null) {
            // Commit cherry-picked but no downstream issues
            if (this.candidateReleaseVersion.compareWithoutQualifierTo(candidateReleaseVersion) == 0) {
               if (!commit.getSummary().startsWith("NO-JIRA")) {
                  if (checkIncompleteCommits) {
                     logger.warn("INCOMPLETE because no downstream issues");
                     commit.setState(Commit.State.INCOMPLETE).setReason("NO_DOWNSTREAM_ISSUES");
                  }
               }
            }
         } else {
            // Commit not cherry-picked and no downstream issues

            if ((confirmedUpstreamIssues != null && confirmedUpstreamIssues.containsKey(upstreamIssue.getKey())) ||
               (!isUpstreamIssueExcluded(upstreamIssue) && upstreamIssue.getType().equals(upstreamIssueManager.getIssueTypeBug()))) {
               commit.setState(Commit.State.NEW);
               processCommitTask(commit, release, qualifier, CommitTask.Type.CLONE_UPSTREAM_ISSUE, upstreamIssue.getKey(), null, confirmedTasks);
            } else {
               commit.setState(Commit.State.SKIPPED).setReason("UPSTREAM_ISSUE_NOT_SUFFICIENT");
            }
         }
      }

      return commit;
   }

   private boolean isCurrentOrFutureRelease(String currentRelease, String release) {
      return currentRelease.equals(release) || FUTURE_GA_RELEASE.equals(release);
   }

   private boolean isAnyDownstreamIssueDone(List<Issue> downstreamIssues) {
      for (Issue downstreamIssue : downstreamIssues) {
         if (downstreamIssueManager.getIssueStateDone().equals(downstreamIssue.getState())) {
            return true;
         }
      }

      return false;
   }

   private boolean isCommitRequired(List<Issue> downstreamIssues) {
      for (Issue downstreamIssue : downstreamIssues) {
         if ((confirmedDownstreamIssues != null && confirmedDownstreamIssues.containsKey(downstreamIssue.getKey())) ||
            (!isDownstreamIssueExcluded(downstreamIssue) && isCommitRequired(downstreamIssue))) {
            return true;
         }
      }

      return false;
   }

   private boolean isCommitRequired(Issue downstreamIssue) {
      return (downstreamIssueManager.getIssueTypeBug().equals(downstreamIssue.getType()) && (
         (downstreamIssue.isCustomer() && downstreamIssuesCustomerPriority.compareTo(downstreamIssue.getCustomerPriority()) <= 0) ||
            (downstreamIssue.isSecurity() && downstreamIssuesSecurityImpact.compareTo(downstreamIssue.getSecurityImpact()) <= 0) ||
            (downstreamIssue.isPatch())));
   }

   private boolean isDownstreamIssueExcluded(Issue downstreamIssue) {
      return (excludedDownstreamIssues != null && excludedDownstreamIssues.containsKey(downstreamIssue.getKey()));
   }

   private boolean isUpstreamIssueExcluded(Issue upstreamIssue) {
      return (excludedUpstreamIssues != null && excludedUpstreamIssues.containsKey(upstreamIssue.getKey()));
   }

   private boolean isCommitExcluded(List<Issue> downstreamIssues) {
      for (Issue downstreamIssue : downstreamIssues) {
         ReleaseVersion targetReleaseVersion;

         try {
            targetReleaseVersion = new ReleaseVersion(downstreamIssue.getTargetRelease());
         } catch (Exception e) {
            targetReleaseVersion = null;
         }

         if (targetReleaseVersion != null && downstreamIssue.getLabels().contains(downstreamIssueManager.getIssueLabelNoBackportNeeded()) &&
            targetReleaseVersion.getMajor() == candidateReleaseVersion.getMajor() &&
            targetReleaseVersion.getMinor() == candidateReleaseVersion.getMinor()) {
            return true;
         }
      }

      return false;
   }

   private boolean cloneDownstreamIssues(Commit commit, String release, String qualifier, List<Issue> downstreamIssues, List<CommitTask> confirmedTasks) throws Exception {
      boolean executed = true;

      for (Issue downstreamIssue : downstreamIssues) {
         executed &= processCommitTask(commit, release, qualifier, CommitTask.Type.CLONE_DOWNSTREAM_ISSUE, downstreamIssue.getKey(), null, confirmedTasks);
      }

      return executed;
   }

   private boolean processDownstreamIssues(Commit commit, String release, String qualifier, List<Issue> downstreamIssues, List<CommitTask> confirmedTasks) throws Exception {
      boolean executed = true;

      for (Issue downstreamIssue : downstreamIssues) {
         //Check if the downstream issue define a target release
         if (downstreamIssue.getTargetRelease() == null || downstreamIssue.getTargetRelease().isEmpty() || downstreamIssue.getTargetRelease().equals(FUTURE_GA_RELEASE)) {
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, qualifier, CommitTask.Type.SET_DOWNSTREAM_ISSUE_TARGET_RELEASE, downstreamIssue.getKey(), release, confirmedTasks);
            }
         }

         //Check if the downstream issue has the qualifier label
         if (!downstreamIssue.getLabels().contains(qualifier)) {
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, qualifier, CommitTask.Type.ADD_DOWNSTREAM_ISSUE_LABEL, downstreamIssue.getKey(), qualifier, confirmedTasks);
            }
         }

         //Check if the downstream issue has the upstream issue
         if (commit.getUpstreamIssue() != null && !downstreamIssue.getIssues().contains(commit.getUpstreamIssue())) {
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, qualifier, CommitTask.Type.ADD_DOWNSTREAM_ISSUE_UPSTREAM_ISSUE, downstreamIssue.getKey(), commit.getUpstreamIssue(), confirmedTasks);
            }
         }

         //Check if the downstream issue has the upstream-test-coverage label
         if (commit.getTests().size() > 0 && !downstreamIssue.getLabels().contains(downstreamIssueManager.getIssueLabelUpstreamTestCoverage()) &&
            !downstreamIssue.getLabels().contains(downstreamIssueManager.getIssueLabelNoTestingNeeded())){
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, qualifier, CommitTask.Type.ADD_DOWNSTREAM_ISSUE_LABEL, downstreamIssue.getKey(), downstreamIssueManager.getIssueLabelUpstreamTestCoverage(), confirmedTasks);
            }
         }

         //Check if the downstream issue has the right state
         if (downstreamIssueManager.getIssueStateMachine().getStateIndex(downstreamIssue.getState()) < downstreamIssueManager.getIssueStateMachine().getStateIndex(downstreamIssueManager.getIssueStateDevComplete())) {
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, qualifier, CommitTask.Type.TRANSITION_DOWNSTREAM_ISSUE, downstreamIssue.getKey(), downstreamIssueManager.getIssueStateDevComplete(), confirmedTasks);
            }
         }
      }

      return executed;
   }

   private boolean testCommit(Commit commit) throws Exception {
      File commitTestDir = new File(commitTestsDir, commit.getUpstreamCommit());
      commitTestDir.mkdirs();

      File outputCommitTestFile = new File(commitTestDir, "output.log");
      try (BufferedWriter outputCommitTestWriter = new BufferedWriter(new FileWriter(outputCommitTestFile))) {
         int mavenResult;
         String outputCommitTestLine;
         List<String> mevaneCommitTestCommand = buildMavenCommitTestCommand(commit);

         outputCommitTestLine = String.join(" ", mevaneCommitTestCommand);
         logger.debug(outputCommitTestLine);
         outputCommitTestWriter.write(outputCommitTestLine);
         outputCommitTestWriter.newLine();

         ProcessBuilder mavenProcessBuilder = new ProcessBuilder(mevaneCommitTestCommand)
                 .directory(gitRepository.getDirectory().getParentFile())
                 .redirectErrorStream(true);

         Process mavenProcess = mavenProcessBuilder.start();

         try (BufferedReader outputProcessReader = new BufferedReader(new InputStreamReader(mavenProcess.getInputStream()))) {
            while ((outputCommitTestLine = outputProcessReader.readLine ()) != null) {
               logger.debug(outputCommitTestLine);
               outputCommitTestWriter.write(outputCommitTestLine);
               outputCommitTestWriter.newLine();
            }

            mavenResult = mavenProcess.waitFor();

            outputCommitTestLine = "Process finished with exit code " + mavenResult;
            logger.debug(outputCommitTestLine);
            outputCommitTestWriter.write(outputCommitTestLine);
            outputCommitTestWriter.newLine();
         }

         if (mavenResult != 0) {
            return false;
         }
      }

      //Check tests
      if (commit.getTests().size() > 0) {
         //Find surefireReportsDirectories
         List<File> surefireReportsDirectories = new ArrayList<>();
         try (Stream<Path> walk = Files.walk(Paths.get(gitRepository.getDirectory().getParent()))) {
            walk.filter(path -> Files.isDirectory(path) && path.endsWith("surefire-reports"))
               .forEach(path -> surefireReportsDirectories.add(path.toFile()));
         }

         //Copy surefireReports
         for (File surefireReportsDirectory : surefireReportsDirectories) {
            FileUtils.copyDirectory(surefireReportsDirectory, commitTestDir);
         }

         //Analyze surefireReports
         SurefireReportParser surefireReportParser = new SurefireReportParser(surefireReportsDirectories, java.util.Locale.ENGLISH, new NullConsoleLogger());
         List<ReportTestSuite> reportTestSuites = surefireReportParser.parseXMLReportFiles();
         for (ReportTestSuite reportTestSuite : reportTestSuites) {
            if (reportTestSuite.getNumberOfFailures() > 0) {
               return false;
            }
         }
      }

      return true;
   }

   private List<String> buildMavenCommitTestCommand(Commit commit) {
      List<String> testCommand = new ArrayList<>();

      testCommand.add("mvn");
      testCommand.add("--show-version");

      if (commit.getTests().size() > 0) {
         testCommand.add("--activate-profiles=dev,tests,redhat-indy");
         testCommand.add("--define=failIfNoTests=false");
         testCommand.add("--define=test=" + String.join(",", commit.getTests()));
      } else {
         testCommand.add("--activate-profiles=dev,redhat-indy");
         testCommand.add("--define=skipTests=true");
      }

      testCommand.add("clean");
      testCommand.add("package");

      return testCommand;
   }

   private void cherryPickUpstreamCommit(Commit commit, String key, String value, CommitTask commitTask) throws Exception {
      GitCommit upstreamCommit = gitRepository.resolveCommit(key);
      if (gitRepository.cherryPick(upstreamCommit)) {
         if (!skipCommitTest && !testCommit(commit)) {
            logger.warn("Error testing commit: " + commit.getUpstreamCommit());

            gitRepository.resetHard();

            commitTask.setState(CommitTask.State.FAILED);
            commitTask.setResult("TEST_COMMIT_FAILED");
         } else {
            String commitMessage = upstreamCommit.getFullMessage() + "\n" +
               "(cherry picked from commit " + upstreamCommit.getName() + ")\n\n" +
               "downstream: " + value;

            GitCommit cherryPickedCommit = gitRepository.commit(commitMessage,
                                                                upstreamCommit.getAuthorName(),
                                                                upstreamCommit.getAuthorEmail(),
                                                                upstreamCommit.getAuthorWhen(),
                                                                upstreamCommit.getAuthorTimeZone(),
                                                                COMMITTER_NAME,
                                                                COMMITTER_EMAIL);

            if (!dryRun) {
               gitRepository.push("origin", null);
            }

            cherryPickedCommits.put(upstreamCommit.getName(), new AbstractMap.SimpleEntry(candidateReleaseVersion, cherryPickedCommit));

            commitTask.setState(CommitTask.State.EXECUTED);
            commitTask.setResult(cherryPickedCommit.getName());
         }
      } else {
         logger.warn("Error cherry picking: " + commit.getUpstreamCommit());

         gitRepository.resetHard();

         commitTask.setState(CommitTask.State.FAILED);
         commitTask.setResult("CHERRY_PICK_FAILED");
      }
   }

   private boolean processCommitTask(Commit commit, String release, String qualifier, CommitTask.Type type, String key, String value, List<CommitTask> confirmedTasks) throws Exception {
      CommitTask commitTask = new CommitTask().setType(type).setKey(key).setValue(value).setState(CommitTask.State.NEW);

      commit.getTasks().add(commitTask);

      if (getCommitTask(type, key, value, confirmedTasks) == null) {
         commitTask.setState(CommitTask.State.UNCONFIRMED);
         return false;
      }

      if (type == CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT) {
         cherryPickUpstreamCommit(commit, key, value, commitTask);
      } else if (type == CommitTask.Type.ADD_DOWNSTREAM_ISSUE_LABEL) {
         if (!dryRun) {
            downstreamIssueManager.addIssueLabels(key, value);
         }
         downstreamIssueManager.getIssue(key).getLabels().add(value);
         commitTask.setState(CommitTask.State.EXECUTED);
      } else if (type == CommitTask.Type.ADD_DOWNSTREAM_ISSUE_UPSTREAM_ISSUE) {
         if (!dryRun) {
            downstreamIssueManager.addIssueUpstreamIssues(key, value);
         }
         downstreamIssueManager.getIssue(key).getIssues().add(value);
         commitTask.setState(CommitTask.State.EXECUTED);
      } else if (type == CommitTask.Type.SET_DOWNSTREAM_ISSUE_TARGET_RELEASE) {
         if (!dryRun) {
            downstreamIssueManager.setIssueTargetRelease(key, value);
         }
         downstreamIssueManager.getIssue(key).setTargetRelease(value);
         commitTask.setState(CommitTask.State.EXECUTED);
      } else if (type == CommitTask.Type.TRANSITION_DOWNSTREAM_ISSUE) {
         if (!dryRun) {
            downstreamIssueManager.transitionIssue(key, value);
         }
         downstreamIssueManager.getIssue(key).setState(value);
         commitTask.setState(CommitTask.State.EXECUTED);
      } else if (type == CommitTask.Type.CLONE_DOWNSTREAM_ISSUE) {
         Issue cloningIssue = downstreamIssueManager.getIssue(key);
         ReleaseVersion releaseVersion = new ReleaseVersion(release);
         String summaryPrefix = "[" + releaseVersion.getMajor() + "." + releaseVersion.getMinor() + "]";

         List<String> labels = new ArrayList<>();
         for (String label : cloningIssue.getLabels()) {
            if (!label.startsWith("CR")) {
               labels.add(label);
            }
         }

         String upstreamIssues = String.join(",", cloningIssue.getIssues());

         String summary = summaryPrefix + " " + cloningIssue.getSummary();

         Issue clonedIssue;
         if (dryRun) {
            clonedIssue = new Issue().setKey(key + "-DRY-RUN-CLONE").setSummary(summary)
               .setDescription(cloningIssue.getDescription()).setType(cloningIssue.getType())
               .setAssignee(cloningIssue.getAssignee()).setTargetRelease(release);
            clonedIssue.getLabels().addAll(labels);
            clonedIssue.getIssues().addAll(cloningIssue.getIssues());
         } else {
            clonedIssue = downstreamIssueManager.createIssue(
               summary, cloningIssue.getDescription(), cloningIssue.getType(),
               cloningIssue.getAssignee(), upstreamIssues, release, labels);

            downstreamIssueManager.linkIssue(clonedIssue.getKey(), key, "Cloners");
         }

         for (String upstreamIssueKey : clonedIssue.getIssues()) {
            Issue upstreamIssue = upstreamIssueManager.getIssue(upstreamIssueKey);
            upstreamIssue.getIssues().add(clonedIssue.getKey());
         }

         commitTask.setResult(clonedIssue.getKey());
         commitTask.setState(CommitTask.State.EXECUTED);
      } else if (type == CommitTask.Type.CLONE_UPSTREAM_ISSUE) {
         Issue upstreamIssue = upstreamIssueManager.getIssue(key);
         List<String> labels = new ArrayList<>();
         labels.add(qualifier);

         User assignee = userResolver.getUserFromUsername(commit.getAssignee());

         Issue downstreamIssue;
         if (dryRun) {
            downstreamIssue = new Issue().setKey(key + "-DRY-RUN-CLONE")
               .setSummary(upstreamIssue.getSummary())
               .setDescription(upstreamIssue.getDescription())
               .setType(downstreamIssueManager.getIssueTypeBug())
               .setAssignee(assignee.getDownstreamUsername())
               .setTargetRelease(release);
            downstreamIssue.getLabels().addAll(labels);
            downstreamIssue.getIssues().add(upstreamIssue.getKey());
         } else {
            downstreamIssue = downstreamIssueManager.createIssue(
               upstreamIssue.getSummary(), upstreamIssue.getDescription(), downstreamIssueManager.getIssueTypeBug(),
               assignee.getDownstreamUsername(), upstreamIssue.getKey(), release, labels);

            upstreamIssue.getIssues().add(downstreamIssue.getKey());
         }

         commitTask.setResult(downstreamIssue.getKey());
         commitTask.setState(CommitTask.State.EXECUTED);
      } else {
         throw new IllegalStateException("Commit task type not supported: " + type);
      }

      return CommitTask.State.EXECUTED.equals(commitTask.getState());
   }

   private CommitTask getCommitTask(CommitTask.Type type, String key, String value, List<CommitTask> tasks) {
      if (tasks != null) {
         for (CommitTask task : tasks) {
            if (Objects.equals(type, task.getType()) &&
               Objects.equals(key, task.getKey()) &&
               Objects.equals(value, task.getValue())) {
               return task;
            }
         }
      }

      return null;
   }

   private List<String> getCommitTests(GitCommit upstreamCommit) throws Exception {
      List<String> tests = new ArrayList<>();
      for (String ChangedFile : gitRepository.getChangedFiles(upstreamCommit)) {
         if (ChangedFile.contains(TEST_PATH) && ChangedFile.endsWith("Test.java")) {
            tests.add(ChangedFile.substring(ChangedFile.indexOf(TEST_PATH) + TEST_PATH.length(),
                                            ChangedFile.length() - 5).replace('/', '.'));
         }
      }

      return tests;
   }

   private String selectRelease(Set<String> releases, String release) {
      String selectedRelease = null;
      for (String selectingRelease : releases) {
         if (release.equals(selectingRelease)) {
            return selectingRelease;
         } else if (selectedRelease == null || FUTURE_GA_RELEASE.equals(selectedRelease) ||
            (!FUTURE_GA_RELEASE.equals(selectingRelease) && ReleaseVersion.compare(selectingRelease, selectedRelease) > 0)) {
            selectedRelease = selectingRelease;
         }
      }

      return selectedRelease;
   }

   private Map<String, List<Issue>> groupDownstreamIssuesByTargetRelease(List<String> downstreamIssues, String release) {
      Map<String, List<Issue>> downstreamIssuesGroups = new HashMap<>();
      for (String downstreamIssueKey : downstreamIssues) {
         Issue downstreamIssue = downstreamIssueManager.getIssue(downstreamIssueKey);
         if (downstreamIssue != null) {
            String downstreamIssueTargetRelease = downstreamIssue.getTargetRelease();
            if (downstreamIssueTargetRelease == null ||
               downstreamIssueTargetRelease.isEmpty() ||
               downstreamIssueTargetRelease.equals(FUTURE_GA_RELEASE)) {
               downstreamIssueTargetRelease = FUTURE_GA_RELEASE;
            }

            List<Issue> downstreamIssuesGroup = downstreamIssuesGroups.get(downstreamIssueTargetRelease);
            if (downstreamIssuesGroup == null) {
               downstreamIssuesGroup = new ArrayList<>();
               downstreamIssuesGroups.put(downstreamIssueTargetRelease, downstreamIssuesGroup);
            }
            downstreamIssuesGroup.add(downstreamIssue);
         } else {
            logger.warn("Downstream issue not found: " + downstreamIssueKey);
         }
      }

      return downstreamIssuesGroups;
   }

   public User getAssignee(GitCommit upstreamCommit, Issue upstreamIssue, List<Issue> downstreamIssues) {
      User user;

      user = userResolver.getUserFromEmailAddress(upstreamCommit.getAuthorEmail());
      if (user != null) {
         return user;
      }

      user = userResolver.getUserFromEmailAddress(upstreamCommit.getCommitterEmail());
      if (user != null) {
         return user;
      }

      if (upstreamIssue != null) {
         user = userResolver.getUserFromUpstreamUsername(upstreamIssue.getAssignee());
         if (user != null) {
            return user;
         }

         user = userResolver.getUserFromUpstreamUsername(upstreamIssue.getReporter());
         if (user != null) {
            return user;
         }

         user = userResolver.getUserFromUpstreamUsername(upstreamIssue.getCreator());
         if (user != null) {
            return user;
         }
      }

      if (downstreamIssues != null) {
         for (Issue downstreamIssue : downstreamIssues) {
            user = userResolver.getUserFromDownstreamUsername(downstreamIssue.getAssignee());
            if (user != null) {
               return user;
            }

            user = userResolver.getUserFromDownstreamUsername(downstreamIssue.getReporter());
            if (user != null) {
               return user;
            }

            user = userResolver.getUserFromDownstreamUsername(downstreamIssue.getCreator());
            if (user != null) {
               return user;
            }
         }
      }

      return userResolver.getDefaultUser();
   }

}
