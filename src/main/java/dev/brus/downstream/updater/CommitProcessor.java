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
import dev.brus.downstream.updater.issue.DownstreamIssueManager;
import dev.brus.downstream.updater.issue.Issue;
import dev.brus.downstream.updater.issue.IssueCustomerPriority;
import dev.brus.downstream.updater.issue.IssueManager;
import dev.brus.downstream.updater.issue.IssueReference;
import dev.brus.downstream.updater.issue.IssueSecurityImpact;
import dev.brus.downstream.updater.project.ProjectConfig;
import dev.brus.downstream.updater.user.User;
import dev.brus.downstream.updater.user.UserResolver;
import dev.brus.downstream.updater.util.CommandExecutor;
import dev.brus.downstream.updater.util.ReleaseVersion;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
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

   private final static Pattern downstreamLinePattern = Pattern.compile("downstream:.*");

   private static final String FUTURE_GA_RELEASE = "Future GA";

   private static final String TEST_PATH = "src/test/java/";


   private ProjectConfig projectConfig;
   private String projectStreamName;
   private GitRepository gitRepository;
   private ReleaseVersion candidateReleaseVersion;
   private String targetReleaseFormat;
   private IssueManager upstreamIssueManager;
   private DownstreamIssueManager downstreamIssueManager;
   private UserResolver userResolver;
   private Map<String, Map.Entry<GitCommit, ReleaseVersion>> cherryPickedCommits;
   private Map<String, Commit> confirmedCommits;
   private Map<String, Issue> confirmedDownstreamIssues;
   private Map<String, Issue> excludedDownstreamIssues;
   private Map<String, Issue> confirmedUpstreamIssues;
   private Map<String, Issue> excludedUpstreamIssues;
   private Map<String, List<String>> upstreamRevertingChains;
   private IssueCustomerPriority downstreamIssuesCustomerPriority;
   private IssueSecurityImpact downstreamIssuesSecurityImpact;
   private boolean checkIncompleteCommits;
   private String checkCommand;
   private String checkTestsCommand;
   private File commitsDir;

   public GitRepository getGitRepository() {
      return gitRepository;
   }

   public ReleaseVersion getCandidateReleaseVersion() {
      return candidateReleaseVersion;
   }

   public String getTargetReleaseFormat() {
      return targetReleaseFormat;
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

   public Map<String, List<String>> getUpstreamRevertingChains() {
      return upstreamRevertingChains;
   }

   public CommitProcessor setUpstreamRevertingChains(Map<String, List<String>> upstreamRevertingChains) {
      this.upstreamRevertingChains = upstreamRevertingChains;
      return this;
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

   public String getCheckCommand() {
      return checkCommand;
   }

   public CommitProcessor setCheckCommand(String checkCommand) {
      this.checkCommand = checkCommand;
      return this;
   }

   public String getCheckTestsCommand() {
      return checkTestsCommand;
   }

   public CommitProcessor setCheckTestsCommand(String checkTestsCommand) {
      this.checkTestsCommand = checkTestsCommand;
      return this;
   }

   public File getCommitsDir() {
      return commitsDir;
   }

   public CommitProcessor setCommitsDir(File commitsDir) {
      this.commitsDir = commitsDir;
      return this;
   }

   public CommitProcessor(
      ReleaseVersion candidateReleaseVersion,
      String targetReleaseFormat,
      ProjectConfig projectConfig,
      String projectStreamName,
      GitRepository gitRepository,
      IssueManager upstreamIssueManager,
      DownstreamIssueManager downstreamIssueManager,
      UserResolver userResolver) {

      this.candidateReleaseVersion = candidateReleaseVersion;
      this.targetReleaseFormat = targetReleaseFormat;
      this.projectConfig = projectConfig;
      this.projectStreamName = projectStreamName;
      this.gitRepository = gitRepository;
      this.upstreamIssueManager = upstreamIssueManager;
      this.downstreamIssueManager = downstreamIssueManager;
      this.userResolver = userResolver;

      this.cherryPickedCommits = new HashMap<>();
      this.confirmedCommits = Collections.emptyMap();
      this.confirmedDownstreamIssues = Collections.emptyMap();
      this.excludedDownstreamIssues = Collections.emptyMap();
      this.confirmedUpstreamIssues = Collections.emptyMap();
      this.excludedUpstreamIssues = Collections.emptyMap();
      this.upstreamRevertingChains = Collections.emptyMap();
      this.downstreamIssuesCustomerPriority = IssueCustomerPriority.NONE;
      this.downstreamIssuesSecurityImpact = IssueSecurityImpact.NONE;
      this.checkIncompleteCommits = true;
      this.checkTestsCommand = null;
      this.checkCommand = null;
      this.commitsDir = null;
   }

   public Commit process(GitCommit upstreamCommit) throws Exception {
      logger.info("Processing " + upstreamCommit.getName() + " - " + upstreamCommit.getShortMessage());

      ReleaseVersion candidateReleaseVersion = this.candidateReleaseVersion;
      Map.Entry<GitCommit, ReleaseVersion> cherryPickedCommit = cherryPickedCommits.get(upstreamCommit.getName());
      if (cherryPickedCommit != null) {
         candidateReleaseVersion = cherryPickedCommit.getValue();
      }

      String release = String.format(targetReleaseFormat,
         candidateReleaseVersion.getMajor(),
         candidateReleaseVersion.getMinor(),
         candidateReleaseVersion.getPatch(),
         candidateReleaseVersion.getQualifier());
      String candidate = candidateReleaseVersion.getCandidate();


      String downstreamRemoteUri = gitRepository.remoteGet("origin");
      String upstreamRemoteUri = gitRepository.remoteGet("upstream");

      Commit commit = new Commit()
         .setAssignee(getAssignee(upstreamCommit, null, null).getUsername())
         .setAuthor(upstreamCommit.getAuthorName())
         .setUpstreamCommit(upstreamCommit.getName())
         .setUpstreamCommitUrl(upstreamRemoteUri.replace(".git", "/commit/" + upstreamCommit.getName()))
         .setDownstreamCommit(cherryPickedCommit != null ? cherryPickedCommit.getKey().getName() : null)
         .setDownstreamCommitUrl(cherryPickedCommit != null ? downstreamRemoteUri.replace(".git", "/commit/" + cherryPickedCommit.getKey().getName()) : null)
         .setSummary(upstreamCommit.getShortMessage())
         .setRelease(candidateReleaseVersion.toString())
         .setTests(getCommitTests(upstreamCommit))
         .setState(Commit.State.DONE);

      Commit confirmedCommit = confirmedCommits.get(upstreamCommit.getName());
      List<CommitTask> confirmedTasks = null;
      if (confirmedCommit != null) {
         commit.setAction(confirmedCommit.getAction());
         confirmedTasks = confirmedCommit.getTasks();
      }

      if (commitsDir != null) {
         // Initialize commit dir
         File commitDir = new File(commitsDir, upstreamCommit.getName());
         if (!commitDir.mkdirs()) {
            throw new RuntimeException("Error creating commit directory: " + commitDir);
         }
         commit.setUpstreamCommitDir(commitDir.getPath());
      }

      List<String> upstreamRevertingChain = upstreamRevertingChains.get(upstreamCommit.getName());

      List<String> upstreamIssueKeys = upstreamIssueManager.parseIssueKeys(upstreamCommit.getShortMessage());

      if (upstreamIssueKeys.isEmpty()) {
         if (upstreamRevertingChain != null) {
            for(String upstreamRevertingChainItem : upstreamRevertingChain) {
               GitCommit upstreamRevertingChainCommit = gitRepository.resolveCommit(upstreamRevertingChainItem);
               List<String> upstreamRevertingIssueKeys = upstreamIssueManager.parseIssueKeys(upstreamRevertingChainCommit.getShortMessage());

               if (upstreamRevertingIssueKeys.size() > 0) {
                  upstreamIssueKeys.addAll(upstreamRevertingIssueKeys);
                  break;
               }
            }
         }
      }

      List<Issue> upstreamIssues = new ArrayList<>();
      for (String upstreamIssueKey : upstreamIssueKeys){
         Issue upstreamIssue = upstreamIssueManager.getIssue(upstreamIssueKey);

         if (upstreamIssue != null) {
            if (!isUpstreamIssueExcluded(upstreamIssue)) {
               upstreamIssues.add(upstreamIssue);
               commit.getUpstreamIssues().add(new IssueReference(upstreamIssue));
            } else {
               logger.info("Upstream issue excluded: " + upstreamIssueKey);
            }
         } else {
            logger.warn("Upstream issue not found: " + upstreamIssueKey);
         }
      }

      commit.setAssignee(getAssignee(upstreamCommit, upstreamIssues, null).getUsername());

      if (upstreamIssues.isEmpty()) {
         if (processCommitTask(commit, release, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT,
            Commit.Action.FORCE, Map.of("upstreamCommit", commit.getUpstreamCommit()), confirmedTasks)) {
            commit.setState(Commit.State.DONE);
         } else {
            logger.info("SKIPPED because the commit message has no valid upstream issues");
            commit.setState(Commit.State.SKIPPED).setReason("NO_VALID_UPSTREAM_ISSUES");
         }
         return commit;
      }

      //Skip commits of reverting chains only if they are even and none is cherry-picked
      if (cherryPickedCommit == null && upstreamRevertingChain != null && upstreamRevertingChain.size() % 2 == 0
         && !upstreamRevertingChain.stream().anyMatch(revertingCommit -> cherryPickedCommits.containsKey(revertingCommit))) {
         logger.warn("SKIPPED because the commits of the revering chain are even and none is cherry-picked: " + upstreamCommit.getName());
         if (upstreamRevertingChain.indexOf(upstreamCommit.getName()) == 0) {
            if (processCommitTask(commit, release, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT,
               Commit.Action.FORCE, Map.of("upstreamCommit", commit.getUpstreamCommit()), confirmedTasks)) {
               commit.setState(Commit.State.DONE);
            } else {
               commit.setState(Commit.State.SKIPPED).setReason("UPSTREAM_REVERTING_COMMIT");
            }
         } else {
            if (processCommitTask(commit, release, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT,
               Commit.Action.FORCE, Map.of("upstreamCommit", commit.getUpstreamCommit()), confirmedTasks)) {
               commit.setState(Commit.State.DONE);
            } else {
               commit.setState(Commit.State.SKIPPED).setReason("UPSTREAM_REVERTED_COMMIT");
            }
         }
         return commit;
      }


      // Get downstreamIssueKeys
      List<String> downstreamIssueKeys = new ArrayList<>();

      for (Issue upstreamIssue : upstreamIssues) {
         downstreamIssueKeys.addAll(upstreamIssue.getIssues());
      }

      if (cherryPickedCommit != null) {
         Matcher downstreamLineMatcher = downstreamLinePattern.matcher(cherryPickedCommit.getKey().getFullMessage());

         if (downstreamLineMatcher.find()) {
            List<String> downstreamCommitIssueKeys = downstreamIssueManager.parseIssueKeys(downstreamLineMatcher.group());

            for (String downstreamIssueKey : downstreamCommitIssueKeys) {
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
      Map<String, List<Issue>> downstreamIssuesGroups = groupDownstreamIssuesByTargetRelease(downstreamIssueKeys);
      if (downstreamIssuesGroups != null && downstreamIssuesGroups.size() > 0) {
         selectedTargetRelease = selectRelease(downstreamIssuesGroups.keySet(), release);
         selectedDownstreamIssues = downstreamIssuesGroups.get(selectedTargetRelease);

         for (List<Issue> downstreamIssuesGroup : downstreamIssuesGroups.values()) {
            for (Issue downstreamIssue : downstreamIssuesGroup) {
               allDownstreamIssues.add(downstreamIssue);
               commit.getDownstreamIssues().add(new IssueReference(downstreamIssue));
            }
         }
      }

      commit.setAssignee(getAssignee(upstreamCommit, upstreamIssues, selectedDownstreamIssues).getUsername());

      // The commits related to downstream issues fixed in another release requires
      // a downstream release issue if they are cherry-picked to a branch after the first release
      boolean requireReleaseIssues =  candidateReleaseVersion.getPatch() > 0 ||
         !isAnyPreviousDownstreamIssueDone(candidateReleaseVersion, allDownstreamIssues);

      if (selectedDownstreamIssues != null && selectedDownstreamIssues.size() > 0) {
         // Commit related to downstream issues

         if (cherryPickedCommit != null) {
            // Commit cherry-picked, check the downstream issues

            if (this.candidateReleaseVersion.compareWithoutCandidateTo(candidateReleaseVersion) == 0) {
               if(isCurrentOrFutureRelease(release, selectedTargetRelease)) {
                  if (processDownstreamIssues(commit, release, candidate, selectedDownstreamIssues, confirmedTasks)) {
                     commit.setState(Commit.State.DONE);
                  } else {
                     commit.setState(Commit.State.INCOMPLETE).setReason("DOWNSTREAM_ISSUES_NOT_UPDATED");
                  }
               } else {
                  if (requireReleaseIssues && !isAnyPreviousDownstreamIssueDone(candidateReleaseVersion, allDownstreamIssues)) {
                     // The commits related to downstream issues fixed in another release requires
                     // a downstream release issue if they are cherry-picked to a branch after the first release
                     logger.warn("INCOMPLETE because no downstream issues with the required target release");

                     if (cloneDownstreamIssues(commit, release, selectedDownstreamIssues, confirmedTasks)) {
                        commit.setState(Commit.State.DONE);
                     } else {
                        commit.setState(Commit.State.INCOMPLETE).setReason("NO_DOWNSTREAM_ISSUES_WITH_REQUIRED_TARGET_RELEASE");
                     }
                  }
               }
            }
         } else {
            // Commit not cherry-picked

            if (isUpstreamIssueBackportBlocked(allDownstreamIssues)) {
               logger.info("SKIPPED because the upstream issue is referenced by a downstream issue with NO-BACKPORT-NEEDED");
               commit.setState(Commit.State.SKIPPED).setReason("UPSTREAM_ISSUE_BACKPORT_BLOCKED");
            } else if (isCurrentOrFutureRelease(release, selectedTargetRelease)) {
               // The selected downstream issues have the required target release

               String downstreamIssues = selectedDownstreamIssues.stream()
                  .map(Issue::getKey).collect(Collectors.joining(","));
               if (processCommitTask(commit, release, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT,
                  Commit.Action.STEP, Map.of("upstreamCommit", commit.getUpstreamCommit(),
                     "downstreamIssues", downstreamIssues), confirmedTasks)) {

                  if (processDownstreamIssues(commit, release, candidate, selectedDownstreamIssues, confirmedTasks)) {
                     commit.setState(Commit.State.DONE);
                  } else {
                     commit.setState(Commit.State.INCOMPLETE).setReason("DOWNSTREAM_ISSUES_NOT_UPDATED");
                  }
               } else {
                  commit.setState(Commit.State.TODO).setReason("DOWNSTREAM_ISSUES_WITH_REQUIRED_TARGET_RELEASE_SUFFICIENT");
               }
            } else {
               // The selected downstream issues do not have the required target release

               if (isCommitRequired(allDownstreamIssues)) {
                  // At least one downstream issue match sufficient criteria to cherry-pick the commit

                  if (requireReleaseIssues) {
                     // The commits related to downstream issues fixed in another release requires
                     // a downstream release issue if they are cherry-picked to a branch after the first release
                     if (cloneDownstreamIssues(commit, release, selectedDownstreamIssues, confirmedTasks)) {
                        commit.setState(Commit.State.TODO);
                     } else {
                        for (Issue upstreamIssue : upstreamIssues) {
                           if (processCommitTask(commit, release, CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE,
                              Commit.Action.SKIP, Map.of("issueKey",upstreamIssue.getKey()), confirmedTasks)) {
                              commit.setState(Commit.State.SKIPPED).setReason("UPSTREAM_ISSUE_EXCLUDED");
                           } else {
                              commit.setState(Commit.State.NEW).setReason("DOWNSTREAM_ISSUES_SUFFICIENT_BUT_NONE_WITH_REQUIRED_TARGET_RELEASE");
                           }
                        }
                     }
                  } else {
                     // The commits related to a downstream issue fixed in another release don't require
                     // a downstream release issue if they are cherry-picked to a branch before the first release
                     String downstreamIssues = selectedDownstreamIssues.stream()
                        .map(Issue::getKey).collect(Collectors.joining(","));
                     if (processCommitTask(commit, release, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT,
                        Commit.Action.STEP, Map.of("upstreamCommit", commit.getUpstreamCommit(),
                           "downstreamIssues", downstreamIssues), confirmedTasks)) {

                        commit.setState(Commit.State.DONE);
                     } else {
                        commit.setState(Commit.State.TODO).setReason("DOWNSTREAM_ISSUES_WITH_REQUIRED_TARGET_RELEASE_NOT_REQUIRED");
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
            // Commit cherry-picked with no downstream issues
            logger.warn("Commit cherry-picked with no downstream issues");
         } else {
            // Commit not cherry-picked and no downstream issues
            Issue sufficientUpstreamIssue = upstreamIssues.stream().filter(issue ->
               confirmedUpstreamIssues != null && confirmedUpstreamIssues.containsKey(issue.getKey()) ||
                  issue.getType().equals(upstreamIssueManager.getIssueTypeBug())).findFirst().orElse(null);

            if (sufficientUpstreamIssue != null) {
               if (processCommitTask(commit, release, CommitTask.Type.CLONE_UPSTREAM_ISSUE,
                  Commit.Action.STEP, Map.of("issueKey", sufficientUpstreamIssue.getKey()), confirmedTasks)) {
                  commit.setState(Commit.State.DONE);
               } else {
                  if (processCommitTask(commit, release, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT,
                     Commit.Action.FORCE, Map.of("upstreamCommit", commit.getUpstreamCommit()), confirmedTasks)) {
                     commit.setState(Commit.State.DONE);
                  } else {
                     if (processCommitTask(commit, release, CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE,
                        Commit.Action.SKIP, Map.of("issueKey", sufficientUpstreamIssue.getKey()), confirmedTasks)) {
                        commit.setState(Commit.State.SKIPPED).setReason("UPSTREAM_ISSUE_EXCLUDED");
                     } else {
                        logger.info("SKIPPED because the the upstream issue is sufficient but there are no downstream issues");
                        commit.setState(Commit.State.NEW).setReason("UPSTREAM_ISSUE_SUFFICIENT_BUT_NO_DOWNSTREAM_ISSUES");
                     }
                  }
               }
            } else {
               if (processCommitTask(commit, release, CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT,
                  Commit.Action.FORCE, Map.of("upstreamCommit", commit.getUpstreamCommit()), confirmedTasks)) {
                  commit.setState(Commit.State.DONE);
               } else {
                  logger.info("SKIPPED because the the upstream issue is not sufficient");
                  commit.setState(Commit.State.SKIPPED).setReason("UPSTREAM_ISSUE_NOT_SUFFICIENT");
               }
            }
         }
      }

      return commit;
   }

   private boolean isCurrentOrFutureRelease(String currentRelease, String release) {
      return currentRelease.equals(release) || FUTURE_GA_RELEASE.equals(release);
   }

   private boolean isAnyPreviousDownstreamIssueDone(ReleaseVersion candidateReleaseVersion, List<Issue> downstreamIssues) {
      for (Issue downstreamIssue : downstreamIssues) {
         ReleaseVersion targetReleaseVersion;

         try {
            targetReleaseVersion = ReleaseVersion.fromString(downstreamIssue.getTargetRelease());
         } catch (Exception e) {
            targetReleaseVersion = null;
         }

         if (downstreamIssueManager.getIssueResolutionDone().equals(downstreamIssue.getResolution()) &&
            targetReleaseVersion != null && targetReleaseVersion.compareTo(candidateReleaseVersion) <= 0) {
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

   private boolean isUpstreamIssueBackportBlocked(List<Issue> downstreamIssues) {
      for (Issue downstreamIssue : downstreamIssues) {
         ReleaseVersion targetReleaseVersion;

         try {
            targetReleaseVersion = ReleaseVersion.fromString(downstreamIssue.getTargetRelease());
         } catch (Exception e) {
            targetReleaseVersion = null;
         }

         if (downstreamIssue.getLabels().contains(downstreamIssueManager.getIssueLabelNoBackportNeeded()) &&
            ((targetReleaseVersion == null && downstreamIssue.getTargetRelease() == null) ||
               (targetReleaseVersion != null && targetReleaseVersion.getMajor() == candidateReleaseVersion.getMajor() &&
                  targetReleaseVersion.getMinor() == candidateReleaseVersion.getMinor()))) {
            return true;
         }
      }

      return false;
   }

   private boolean cloneDownstreamIssues(Commit commit, String release, List<Issue> downstreamIssues, List<CommitTask> confirmedTasks) throws Exception {
      boolean executed = true;

      for (Issue downstreamIssue : downstreamIssues) {
         executed &= processCommitTask(commit, release, CommitTask.Type.CLONE_DOWNSTREAM_ISSUE,
            Commit.Action.STEP, Map.of("issueKey", downstreamIssue.getKey()), confirmedTasks);
      }

      return executed;
   }

   private boolean processDownstreamIssues(Commit commit, String release, String candidate, List<Issue> downstreamIssues, List<CommitTask> confirmedTasks) throws Exception {
      boolean executed = true;

      for (Issue downstreamIssue : downstreamIssues) {
         //Check if the downstream issue define a target release
         if (downstreamIssue.getTargetRelease() == null || downstreamIssue.getTargetRelease().isEmpty() || downstreamIssue.getTargetRelease().equals(FUTURE_GA_RELEASE)) {
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, CommitTask.Type.SET_DOWNSTREAM_ISSUE_TARGET_RELEASE,
                  Commit.Action.STEP, Map.of("issueKey", downstreamIssue.getKey(), "targetRelease", release), confirmedTasks);
            }
         }

         //Check if the downstream issue has the candidate label
         if (!downstreamIssue.getLabels().contains(candidate)) {
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, CommitTask.Type.ADD_LABEL_TO_DOWNSTREAM_ISSUE,
                  Commit.Action.STEP, Map.of("issueKey", downstreamIssue.getKey(), "label", candidate), confirmedTasks);
            }
         }

         //Check if the downstream issue has any upstream issues
         if (!commit.getUpstreamIssues().stream().anyMatch(issue -> downstreamIssue.getIssues().contains(issue.getKey()))) {
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, CommitTask.Type.ADD_UPSTREAM_ISSUE_TO_DOWNSTREAM_ISSUE,
                  Commit.Action.STEP, Map.of("issueKey", downstreamIssue.getKey(), "upstreamIssue", commit.getUpstreamIssues().get(0).getKey()), confirmedTasks);
            }
         }

         //Check if the downstream issue has the upstream-test-coverage label
         if (commit.getTests().size() > 0 && !downstreamIssue.getLabels().contains(downstreamIssueManager.getIssueLabelUpstreamTestCoverage()) &&
            !downstreamIssue.getLabels().contains(downstreamIssueManager.getIssueLabelNoTestingNeeded())){
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, CommitTask.Type.ADD_LABEL_TO_DOWNSTREAM_ISSUE,
                  Commit.Action.STEP, Map.of("issueKey", downstreamIssue.getKey(), "label",
                     downstreamIssueManager.getIssueLabelUpstreamTestCoverage()), confirmedTasks);
            }
         }

         if (downstreamIssueManager.getIssueStateMachine().getStateIndex(downstreamIssue.getState()) <
            downstreamIssueManager.getIssueStateMachine().getStateIndex(downstreamIssueManager.getIssueStateDevComplete())) {
            if (checkIncompleteCommits) {
               executed &= processCommitTask(commit, release, CommitTask.Type.TRANSITION_DOWNSTREAM_ISSUE,
                  Commit.Action.STEP, Map.of("issueKey", downstreamIssue.getKey(), "state",
                     downstreamIssueManager.getIssueStateDevComplete()), confirmedTasks);
            }
         }
         //Check if the downstream issue has the right state
      }

      return executed;
   }

   private boolean checkCommit(Commit commit) throws Exception {
      String checkCommand = formatCommitCommand(commit, this.checkCommand);
      if (commit.getTests().size() > 0 && checkTestsCommand != null) {
         checkCommand = formatCommitCommand(commit, checkTestsCommand);
      }

      if (checkCommand != null) {
         BufferedWriter outputCommitTestWriter = null;
         if (commit.getUpstreamCommitDir() != null) {
            File outputCommitTestFile = new File(commit.getUpstreamCommitDir(), "check.log");
            outputCommitTestWriter = new BufferedWriter(new FileWriter(outputCommitTestFile));
         }

         try {
            int exitCode = CommandExecutor.tryExecute(checkCommand,
               gitRepository.getDirectory(), outputCommitTestWriter);

            if (exitCode != 0) {
               return false;
            }

            File pomXmlFile = new File(gitRepository.getDirectory(), "pom.xml");
            if (pomXmlFile.exists() && commit.getTests().size() > 0) {
               return checkSurefireReports(commit.getUpstreamCommitDir());
            }
         } finally {
            outputCommitTestWriter.close();
         }
      }

      return true;
   }

   private String formatCommitCommand(Commit commit, String command) throws Exception {
      return command.replace("${HOSTNAME}", InetAddress.getLocalHost().getHostName())
         .replace("${HOSTIP}", InetAddress.getLocalHost().getHostAddress())
         .replace("${TEST}", String.join(",", commit.getTests()));
   }

   private boolean checkSurefireReports(String commitTestDir) throws Exception {
      //Find surefireReportsDirectories
      List<File> surefireReportsDirectories = new ArrayList<>();
      try (Stream<Path> walk = Files.walk(Paths.get(gitRepository.getDirectory().getAbsolutePath()))) {
         walk.filter(path -> Files.isDirectory(path) && path.endsWith("surefire-reports"))
            .forEach(path -> surefireReportsDirectories.add(path.toFile()));
      }

      //Copy surefireReports
      for (File surefireReportsDirectory : surefireReportsDirectories) {
         FileUtils.copyDirectory(surefireReportsDirectory, new File(commitTestDir));
      }

      //Analyze surefireReports
      SurefireReportParser surefireReportParser = new SurefireReportParser(surefireReportsDirectories, java.util.Locale.ENGLISH, new NullConsoleLogger());
      List<ReportTestSuite> reportTestSuites = surefireReportParser.parseXMLReportFiles();
      for (ReportTestSuite reportTestSuite : reportTestSuites) {
         if (reportTestSuite.getNumberOfFailures() > 0) {
            return false;
         }
      }

      return true;
   }

   private GitCommit cherryPickUpstreamCommit(Commit commit, String downstreamIssues) throws Exception {
      GitCommit upstreamCommit = gitRepository.resolveCommit(commit.getUpstreamCommit());

      try {
         gitRepository.cherryPick(upstreamCommit);

         if (!checkCommit(commit)) {
            throw new IllegalStateException("Error checking commit: " + commit.getUpstreamCommit());
         }

         String commitMessage = upstreamCommit.getFullMessage() + "\n" +
            "(cherry picked from commit " + upstreamCommit.getName() + ")";

         if (downstreamIssues != null) {
            commitMessage += "\n\n" + "downstream: " + downstreamIssues;
         }

         GitCommit cherryPickedCommit = gitRepository
            .commit(commitMessage,
               upstreamCommit.getAuthorName(),
               upstreamCommit.getAuthorEmail(),
               upstreamCommit.getAuthorWhen(),
               upstreamCommit.getAuthorTimeZone());

         gitRepository.push("origin", null);

         cherryPickedCommits.put(upstreamCommit.getName(), new AbstractMap.SimpleEntry(candidateReleaseVersion, cherryPickedCommit));

         return cherryPickedCommit;
      } finally {
         gitRepository.resetHard();
      }
   }

   private boolean processCommitTask(Commit commit, String release, CommitTask.Type type, Commit.Action action, Map<String, String> args, List<CommitTask> confirmedTasks) throws Exception {
      CommitTask commitTask = new CommitTask().setType(type).setAction(action).setArgs(args).setState(CommitTask.State.NEW);

      commit.getTasks().add(commitTask);

      if (getCommitTask(type, args, confirmedTasks) == null) {
         return false;
      }

      try {
         executeCommitTask(commit, release, commitTask);
      } catch (Exception e) {
         logger.error("Commit task failed: " + e);
         commitTask.setState(CommitTask.State.FAILED);
         commitTask.setResult(e.getMessage());
         return false;
      }

      return true;
   }

   private void executeCommitTask(Commit commit, String release, CommitTask commitTask) throws Exception {
      if (commitTask.getType() == CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT) {
         GitCommit cherryPickedCommit = cherryPickUpstreamCommit(commit, commitTask.getArgs().get("downstreamIssues"));
         commitTask.setResult(cherryPickedCommit.getName());
      } else if (commitTask.getType() == CommitTask.Type.ADD_LABEL_TO_DOWNSTREAM_ISSUE) {
         downstreamIssueManager.addIssueLabels(commitTask.getArgs().get("issueKey"), commitTask.getArgs().get("label"));
      } else if (commitTask.getType() == CommitTask.Type.ADD_UPSTREAM_ISSUE_TO_DOWNSTREAM_ISSUE) {
         downstreamIssueManager.addIssueUpstreamIssues(commitTask.getArgs().get("issueKey"), commitTask.getArgs().get("upstreamIssue"));
      } else if (commitTask.getType() == CommitTask.Type.SET_DOWNSTREAM_ISSUE_TARGET_RELEASE) {
         downstreamIssueManager.setIssueTargetRelease(commitTask.getArgs().get("issueKey"), commitTask.getArgs().get("targetRelease"));
      } else if (commitTask.getType() == CommitTask.Type.TRANSITION_DOWNSTREAM_ISSUE) {
         downstreamIssueManager.transitionIssue(commitTask.getArgs().get("issueKey"), commitTask.getArgs().get("state"));
      } else if (commitTask.getType() == CommitTask.Type.CLONE_DOWNSTREAM_ISSUE) {
         Issue clonedIssue = cloneDownstreamIssue(release, commitTask.getArgs().get("issueKey"));
         commitTask.setResult(clonedIssue.getKey());
      } else if (commitTask.getType() == CommitTask.Type.CLONE_UPSTREAM_ISSUE) {
         Issue downstreamIssue = cloneUpstreamIssue(commit, release, commitTask.getArgs().get("issueKey"));
         commitTask.setResult(downstreamIssue.getKey());
      } else if (commitTask.getType() == CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE) {
         String issueKey = commitTask.getArgs().get("issueKey");

         if (!projectConfig.getProject().getStream(projectStreamName).getExcludedUpstreamIssues().contains(issueKey)) {
            projectConfig.addExcludedUpstreamIssue(issueKey, projectStreamName, 3);
         } else {
            logger.info("Upstream issue " + issueKey + " already excluded from " +
               projectConfig.getProject().getName() + "/" + projectStreamName);
         }
      } else {
         throw new IllegalStateException("Commit task type not supported: " + commitTask.getType());
      }

      commitTask.setState(CommitTask.State.DONE);
   }

   private Issue cloneDownstreamIssue(String release, String issueKey) throws Exception {
      Issue cloningIssue = downstreamIssueManager.getIssue(issueKey);
      ReleaseVersion releaseVersion = ReleaseVersion.fromString(release);
      String summaryPrefix = "[" + releaseVersion.getMajor() + "." + releaseVersion.getMinor() + "]";

      List<String> labels = new ArrayList<>();
      for (String label : cloningIssue.getLabels()) {
         if (!label.startsWith("CR")) {
            labels.add(label);
         }
      }

      String summary = summaryPrefix + " " + cloningIssue.getSummary();

      Issue clonedIssue = downstreamIssueManager.createIssue(
         summary, cloningIssue.getDescription(), cloningIssue.getType(),
         cloningIssue.getAssignee(), release, labels);

      downstreamIssueManager.copyIssueUpstreamIssues(cloningIssue.getKey(), clonedIssue.getKey());

      downstreamIssueManager.linkIssue(clonedIssue.getKey(), issueKey, "Cloners");

      for (String upstreamIssueKey : clonedIssue.getIssues()) {
         Issue upstreamIssue = upstreamIssueManager.getIssue(upstreamIssueKey);
         upstreamIssue.getIssues().add(clonedIssue.getKey());
      }

      return clonedIssue;
   }

   private Issue cloneUpstreamIssue(Commit commit, String release, String issueKey) throws Exception {
      Issue upstreamIssue = upstreamIssueManager.getIssue(issueKey);

      User assignee = userResolver.getUserFromUsername(commit.getAssignee());

      Issue downstreamIssue = downstreamIssueManager.createIssue(
         upstreamIssue.getSummary(), upstreamIssue.getDescription(), downstreamIssueManager.getIssueTypeBug(),
         assignee.getDownstreamUsername(), release, Collections.emptyList());

      downstreamIssueManager.addIssueUpstreamIssues(downstreamIssue.getKey(), upstreamIssue.getKey());

      upstreamIssue.getIssues().add(downstreamIssue.getKey());

      return downstreamIssue;
   }


   private CommitTask getCommitTask(CommitTask.Type type, Map<String, String> args, List<CommitTask> tasks) {
      if (tasks != null) {
         for (CommitTask task : tasks) {
            if (Objects.equals(type, task.getType()) &&
               Objects.equals(args, task.getArgs())) {
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

   private Map<String, List<Issue>> groupDownstreamIssuesByTargetRelease(List<String> downstreamIssues) {
      Map<String, List<Issue>> downstreamIssuesGroups = new HashMap<>();
      for (String downstreamIssueKey : downstreamIssues) {
         Issue downstreamIssue = downstreamIssueManager.getIssue(downstreamIssueKey);
         if (downstreamIssue != null) {
            if (!downstreamIssue.isDocumentation()) {
               if (!downstreamIssueManager.isDuplicateIssue(downstreamIssueKey)) {
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
                  logger.warn("Downstream issue duplicate: " + downstreamIssueKey);
               }
            } else {
               logger.warn("Downstream issue for documentation: " + downstreamIssueKey);
            }
         } else {
            logger.warn("Downstream issue not found: " + downstreamIssueKey);
         }
      }

      return downstreamIssuesGroups;
   }

   public User getAssignee(GitCommit upstreamCommit, List<Issue> upstreamIssues, List<Issue> downstreamIssues) {
      User user;

      user = userResolver.getUserFromEmailAddress(upstreamCommit.getAuthorEmail());
      if (user != null) {
         return user;
      }

      user = userResolver.getUserFromEmailAddress(upstreamCommit.getCommitterEmail());
      if (user != null) {
         return user;
      }

      if (upstreamIssues != null) {
         for (Issue upstreamIssue : upstreamIssues) {
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
