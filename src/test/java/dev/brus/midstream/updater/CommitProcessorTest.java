package dev.brus.midstream.updater;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.brus.downstream.updater.Commit;
import dev.brus.downstream.updater.CommitProcessor;
import dev.brus.downstream.updater.CommitTask;
import dev.brus.downstream.updater.git.JGitRepository;
import dev.brus.downstream.updater.issue.DownstreamIssueStateMachine;
import dev.brus.downstream.updater.project.ExcludedIssue;
import dev.brus.downstream.updater.project.Project;
import dev.brus.downstream.updater.project.ProjectConfig;
import dev.brus.downstream.updater.project.ProjectStream;
import dev.brus.downstream.updater.util.CommandExecutor;
import dev.brus.downstream.updater.util.ReleaseVersion;
import dev.brus.downstream.updater.git.GitCommit;
import dev.brus.downstream.updater.git.GitRepository;
import dev.brus.downstream.updater.issue.DownstreamIssueManager;
import dev.brus.downstream.updater.issue.Issue;
import dev.brus.downstream.updater.issue.IssueCustomerPriority;
import dev.brus.downstream.updater.issue.IssueManager;
import dev.brus.downstream.updater.user.User;
import dev.brus.downstream.updater.user.UserResolver;
import dev.brus.midstream.updater.git.MockGitCommit;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class CommitProcessorTest {

   private final static String COMMIT_NAME_0 = "0";
   private final static String NO_ISSUE_KEY = "NO-ISSUE";
   private final static String UPSTREAM_ISSUE_KEY_0 = "UP-0";
   private final static String UPSTREAM_ISSUE_KEY_1 = "UP-1";
   private final static String DOWNSTREAM_ISSUE_KEY_0 = "DOWN-0";
   private final static String TEST_USER_NAME = "test";
   private final static String TEST_USER_EMAIL = "test@user.com";

   private final static String TEST_MESSAGE = "Test message";
   private final static String TEST_MESSAGE_NO_ISSUE_KEY = NO_ISSUE_KEY + " " + TEST_MESSAGE;
   private final static String TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0 = UPSTREAM_ISSUE_KEY_0 + " " + TEST_MESSAGE;
   private final static String TEST_MESSAGE_UPSTREAM_ISSUE_KEY_1 = UPSTREAM_ISSUE_KEY_1 + " " + TEST_MESSAGE;
   private final static String TARGET_RELEASE_FORMAT = "%d.%d.%d.GA";

   private final static String ISSUE_TYPE_BUG = "Bug";
   private final static String ISSUE_TYPE_IMPROVEMENT = "Improvement";
   private static final String ISSUE_RESOLUTION_DONE = "Done";
   private static final String ISSUE_RESOLUTION_DONE_ERRATA = "Done-Errata";
   private static final String ISSUE_RESOLUTION_WONT_DO = "Won't Do";
   private static final String ISSUE_LABEL_NO_BACKPORT_NEEDED = "NO-BACKPORT-NEEDED";
   private static final String ISSUE_STATE_TODO = "To Do";
   private static final String ISSUE_STATE_DEV_COMPLETE = "Dev Complete";
   private static String PREVIOUS_PROJECT_STREAM_NAME = "1.0";
   private static String CURRENT_PROJECT_STREAM_NAME = "1.1";

   @Rule
   public TemporaryFolder testFolder = new TemporaryFolder();

   private ReleaseVersion releaseVersion;
   private ReleaseVersion previousReleaseVersion;
   private ReleaseVersion nextPatchReleaseVersion;
   private ReleaseVersion nextNextPatchReleaseVersion;
   private ReleaseVersion nextReleaseVersion;
   private Project project;
   private ProjectConfig projectConfig;
   private ProjectStream previousProjectStream;
   private ProjectStream currentProjectStream;
   private User testUser;

   private GitRepository gitRepository;

   private UserResolver userResolver;

   private IssueManager upstreamIssueManager;

   private DownstreamIssueManager downstreamIssueManager;

   @Before
   public void initMocks() throws Exception {
      releaseVersion = ReleaseVersion.fromString("1.1.0.CR1");
      previousReleaseVersion = ReleaseVersion.fromString("1.0.0.CR1");
      nextPatchReleaseVersion = new ReleaseVersion(releaseVersion.getMajor(),
         releaseVersion.getMinor(), releaseVersion.getPatch() + 1, releaseVersion.getQualifier(), "CR1");
      nextNextPatchReleaseVersion = new ReleaseVersion(nextPatchReleaseVersion.getMajor(),
         nextPatchReleaseVersion.getMinor(), nextPatchReleaseVersion.getPatch() + 1, nextPatchReleaseVersion.getQualifier(), "CR1");
      nextReleaseVersion = new ReleaseVersion(releaseVersion.getMajor(),
         releaseVersion.getMinor() + 1, 0, releaseVersion.getQualifier(), "CR1");

      testUser = new User()
         .setName(TEST_USER_NAME)
         .setUsername(TEST_USER_NAME)
         .setUpstreamUsername(TEST_USER_NAME)
         .setDownstreamUsername(TEST_USER_NAME)
         .setEmailAddresses(new String[] {TEST_USER_EMAIL});

      project = new Project();
      previousProjectStream = new ProjectStream();
      previousProjectStream.setName(PREVIOUS_PROJECT_STREAM_NAME);
      previousProjectStream.setMode(ProjectStream.Mode.UPDATING);
      project.getStreams().add(previousProjectStream);
      currentProjectStream = new ProjectStream();
      currentProjectStream.setName(CURRENT_PROJECT_STREAM_NAME);
      currentProjectStream.setMode(ProjectStream.Mode.UPDATING);
      project.getStreams().add(currentProjectStream);
      projectConfig = Mockito.mock(ProjectConfig.class);
      Mockito.when(projectConfig.getProject()).thenReturn(project);
      Mockito.doAnswer(invocationOnMock -> {
         ProjectStream projectStream = project.getStream((String)invocationOnMock.getArguments()[2]);
         ExcludedIssue excludedIssue = new ExcludedIssue();
         excludedIssue.setKey((String)invocationOnMock.getArguments()[0]);
         excludedIssue.setUntil((String)invocationOnMock.getArguments()[1]);

         Optional<ExcludedIssue> existingExcludedIssue = projectStream.getExcludedUpstreamIssues().stream().
            filter(issue -> Objects.equals(excludedIssue.getKey(), issue.getKey())).findFirst();

         if (existingExcludedIssue.isPresent()) {
            existingExcludedIssue.get().setUntil(excludedIssue.getUntil());
         } else {
            projectStream.getExcludedUpstreamIssues().add(excludedIssue);
         }

         return null;
      }).when(projectConfig).putExcludedUpstreamIssueWithRetries(Mockito.anyString(), Mockito.nullable(String.class), Mockito.anyString(), Mockito.anyInt());

      gitRepository = Mockito.mock(GitRepository.class);
      Mockito.when(gitRepository.remoteGet("origin")).thenReturn("https://github.com/origin/test.git");
      Mockito.when(gitRepository.remoteGet("upstream")).thenReturn("https://github.com/upstream/test.git");

      userResolver = new UserResolver(new User[] {testUser}).setDefaultUser(testUser);

      upstreamIssueManager = Mockito.mock(IssueManager.class);

      downstreamIssueManager = Mockito.mock(DownstreamIssueManager.class);
   }

   @Test
   public void testCommitNotRequiringReleaseIssue() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0);
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setTargetRelease("1.0.0.GA")
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setResolution(ISSUE_RESOLUTION_DONE);
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.TODO, commit.getState());
   }

   @Test
   public void testCommitWithNotSufficientDownstreamIssue() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0);
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setTargetRelease("1.0.0.GA")
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setResolution(ISSUE_RESOLUTION_DONE);
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals(1, commit.getTasks().size());
      Assert.assertEquals(Commit.Action.FORCE, commit.getTasks().get(0).getAction());
   }

   @Test
   public void testCommitWithBlockedUpstreamIssue() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0);
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setResolution(ISSUE_RESOLUTION_DONE);
      downstreamIssue.getLabels().add(ISSUE_LABEL_NO_BACKPORT_NEEDED);
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);
      Mockito.when(downstreamIssueManager.getIssueLabelNoBackportNeeded()).thenReturn(ISSUE_LABEL_NO_BACKPORT_NEEDED);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commitWithoutTargetRelease = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commitWithoutTargetRelease.getState());
      Assert.assertEquals("UPSTREAM_ISSUE_BACKPORT_BLOCKED", commitWithoutTargetRelease.getReason());

      downstreamIssue.setTargetRelease("1.0.0.GA");

      Commit commitWithPreviousTargetRelease = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.TODO, commitWithPreviousTargetRelease.getState());
      Assert.assertEquals("DOWNSTREAM_ISSUES_WITH_REQUIRED_TARGET_RELEASE_NOT_REQUIRED", commitWithPreviousTargetRelease.getReason());

      downstreamIssue.setTargetRelease("1.1.0.GA");

      Commit commitWithCurrentTargetRelease = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.TODO, commitWithCurrentTargetRelease.getState());
      Assert.assertEquals("DOWNSTREAM_ISSUES_WITH_REQUIRED_TARGET_RELEASE_SUFFICIENT", commitWithCurrentTargetRelease.getReason());

      downstreamIssue.setTargetRelease("1.2.0.GA");

      Commit commitWithNextTargetRelease = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commitWithNextTargetRelease.getState());
      Assert.assertEquals("UPSTREAM_ISSUE_BACKPORT_BLOCKED", commitWithNextTargetRelease.getReason());
   }

   @Test
   public void testCommitWithExcludedUpstreamIssue() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      commitProcessor.setExcludedUpstreamIssues(Collections.singletonMap(UPSTREAM_ISSUE_KEY_0, upstreamIssue));

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals("UPSTREAM_ISSUE_EXCLUDED", commit.getReason());
   }

   @Test
   public void testCommitWithoutUpstreamIssue() throws Exception {
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(TEST_MESSAGE_NO_ISSUE_KEY)
         .setAuthorEmail(TEST_USER_EMAIL);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals(TEST_USER_NAME, commit.getAssignee());
   }

   @Test
   public void testCherryPickedCommitWithoutUpstreamIssue() throws Exception {
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(TEST_MESSAGE_NO_ISSUE_KEY)
         .setAuthorEmail(TEST_USER_EMAIL);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Map<String, Map.Entry<GitCommit, ReleaseVersion>> cherryPickedCommits = new HashMap<>();
      cherryPickedCommits.put(upstreamCommit.getName(), new AbstractMap.SimpleEntry<>(new MockGitCommit(), releaseVersion));
      commitProcessor.setCherryPickedCommits(cherryPickedCommits);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.DONE, commit.getState());
      Assert.assertEquals(TEST_USER_NAME, commit.getAssignee());
   }

   @Test
   public void testCommitWithMultipleUpstreamIssues() throws Exception {
      String commitShortMessage = UPSTREAM_ISSUE_KEY_0 + "," + UPSTREAM_ISSUE_KEY_1 + " " + TEST_MESSAGE;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue0 = new Issue().setKey(UPSTREAM_ISSUE_KEY_0);

      Issue upstreamIssue1 = new Issue().setKey(UPSTREAM_ISSUE_KEY_1);
      upstreamIssue1.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setTargetRelease("1.1.0.GA")
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH);
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_1);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue0);
      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_1)).thenReturn(upstreamIssue1);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0, UPSTREAM_ISSUE_KEY_1));

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.TODO, commit.getState());

      CommitProcessor previousCommitProcessor = new CommitProcessor(
         previousReleaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, PREVIOUS_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit previousCommit = previousCommitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.NEW, previousCommit.getState());

      previousCommitProcessor.setExcludedUpstreamIssues(Collections.singletonMap(UPSTREAM_ISSUE_KEY_0, upstreamIssue0));

      Commit previousCommitAfterExcludingUpstreamIssue0 = previousCommitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.NEW, previousCommitAfterExcludingUpstreamIssue0.getState());

      previousCommitProcessor.setExcludedUpstreamIssues(Collections.singletonMap(UPSTREAM_ISSUE_KEY_1, upstreamIssue1));

      Commit previousCommitAfterExcludingUpstreamIssue1 = previousCommitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.SKIPPED, previousCommitAfterExcludingUpstreamIssue1.getState());

      previousCommitProcessor.setExcludedUpstreamIssues(Map.of(UPSTREAM_ISSUE_KEY_0, upstreamIssue0, UPSTREAM_ISSUE_KEY_1, upstreamIssue1));

      Commit previousCommitAfterExcludingUpstreamIssue0And1 = previousCommitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.SKIPPED, previousCommitAfterExcludingUpstreamIssue0And1.getState());
   }

   @Test
   public void testCommitWithMultipleUpstreamIssuesWithoutDownstreamIssues() throws Exception {
      String commitShortMessage = UPSTREAM_ISSUE_KEY_0 + "," + UPSTREAM_ISSUE_KEY_1 + " " + TEST_MESSAGE;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue0 = new Issue().setKey(UPSTREAM_ISSUE_KEY_0);
      upstreamIssue0.setType("Improvement");

      Issue upstreamIssue1 = new Issue().setKey(UPSTREAM_ISSUE_KEY_1);
      upstreamIssue1.setType(ISSUE_TYPE_BUG);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue0);
      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_1)).thenReturn(upstreamIssue1);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0, UPSTREAM_ISSUE_KEY_1));
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.NEW, commit.getState());
      Assert.assertEquals(Commit.Action.STEP, commit.getTasks().get(0).getAction());
      Assert.assertEquals(CommitTask.Type.CLONE_UPSTREAM_ISSUE, commit.getTasks().get(0).getType());
      Assert.assertEquals(UPSTREAM_ISSUE_KEY_1, commit.getTasks().get(0).getArgs().get("issueKey"));

      commitProcessor.setDownstreamIssuesRequired(true);
      commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals("DOWNSTREAM_ISSUE_NOT_FOUND", commit.getReason());
   }

   @Test
   public void testCommitBlocked() throws Exception {
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(UPSTREAM_ISSUE_KEY_0)
         .setShortMessage(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0)
         .setFullMessage(TEST_MESSAGE)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType(ISSUE_TYPE_BUG);

      currentProjectStream.setMode(ProjectStream.Mode.MANAGING);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Mockito.when(gitRepository.resolveCommit(upstreamCommit.getName())).thenReturn(upstreamCommit);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(upstreamIssueManager.parseIssueKeys(Mockito.anyString())).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Commit newCommit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.NEW, newCommit.getState());
      Assert.assertEquals(4, newCommit.getTasks().size());

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setTargetRelease("1.1.0.GA")
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH);
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);
      downstreamIssue.getLabels().add(ISSUE_STATE_TODO);
      downstreamIssue.getLabels().add(releaseVersion.getCandidate());
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);
      Mockito.when(downstreamIssueManager.parseIssueKeys(Mockito.anyString())).thenReturn(Arrays.asList(DOWNSTREAM_ISSUE_KEY_0));

      DownstreamIssueStateMachine downstreamIssueStateMachine = Mockito.mock(DownstreamIssueStateMachine.class);
      Mockito.when(downstreamIssueStateMachine.getStateIndex(Mockito.any())).thenReturn(0);
      Mockito.when(downstreamIssueManager.getIssueStateMachine()).thenReturn(downstreamIssueStateMachine);

      Commit blockedCommit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.BLOCKED, blockedCommit.getState());
      Assert.assertEquals(1, blockedCommit.getTasks().size());
      Assert.assertEquals(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, blockedCommit.getTasks().get(0).getType());
      Assert.assertEquals(CommitTask.State.BLOCKED, blockedCommit.getTasks().get(0).getState());

      Map<String, Commit> confirmedCommits = new HashMap<>();
      Commit confirmedCommit = new Commit().
         setUpstreamCommit(upstreamCommit.getName()).
         setTasks(Collections.singletonList(
         new CommitTask().
            setAction(Commit.Action.STEP).
            setType(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT).
            setArgs(Map.of("upstreamCommit", upstreamCommit.getName(), "downstreamIssues", DOWNSTREAM_ISSUE_KEY_0)).
            setUserArgs(Map.of("force", Boolean.TRUE.toString()))));
      confirmedCommits.put(upstreamCommit.getName(), confirmedCommit);
      commitProcessor.setConfirmedCommits(confirmedCommits);

      GitCommit cherryPickedCommit = Mockito.mock(GitCommit.class);
      Mockito.when(gitRepository.commit(Mockito.anyString(),
         Mockito.eq(upstreamCommit.getAuthorName()),
         Mockito.eq(upstreamCommit.getAuthorEmail()),
         Mockito.eq(upstreamCommit.getAuthorWhen()),
         Mockito.eq(upstreamCommit.getAuthorTimeZone()))).
         thenReturn(cherryPickedCommit);

      Commit unblockedCommit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.DONE, unblockedCommit.getState());
   }

   @Test
   public void testCommitCherryPicked() throws Exception {
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(UPSTREAM_ISSUE_KEY_0)
         .setShortMessage(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0)
         .setAuthorEmail(TEST_USER_EMAIL);

      MockGitCommit downstreamCommit = new MockGitCommit()
         .setName(DOWNSTREAM_ISSUE_KEY_0)
         .setShortMessage(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0)
         .setFullMessage("downstream: " + DOWNSTREAM_ISSUE_KEY_0)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType(ISSUE_TYPE_BUG);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setTargetRelease("1.1.0.GA")
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH);
      downstreamIssue.getLabels().add(ISSUE_STATE_DEV_COMPLETE);
      downstreamIssue.getLabels().add(releaseVersion.getCandidate());

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);
      commitProcessor.setCherryPickedCommits(Collections.singletonMap(UPSTREAM_ISSUE_KEY_0, new AbstractMap.SimpleEntry<>(downstreamCommit, releaseVersion)));

      Mockito.when(gitRepository.resolveCommit(upstreamCommit.getName())).thenReturn(upstreamCommit);
      Mockito.when(gitRepository.resolveCommit(downstreamCommit.getName())).thenReturn(downstreamCommit);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(upstreamIssueManager.parseIssueKeys(Mockito.anyString())).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);
      Mockito.when(downstreamIssueManager.parseIssueKeys(Mockito.anyString())).thenReturn(Arrays.asList(DOWNSTREAM_ISSUE_KEY_0));

      DownstreamIssueStateMachine downstreamIssueStateMachine = Mockito.mock(DownstreamIssueStateMachine.class);
      Mockito.when(downstreamIssueStateMachine.getIssueStateDevComplete()).thenReturn(ISSUE_STATE_DEV_COMPLETE);
      Mockito.when(downstreamIssueStateMachine.getStateIndex(Mockito.any())).thenReturn(0);
      Mockito.when(downstreamIssueManager.getIssueStateMachine()).thenReturn(downstreamIssueStateMachine);

      Commit incompleteCommit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.PARTIAL, incompleteCommit.getState());

      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Commit doneCommit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.DONE, doneCommit.getState());
   }

   @Test
   public void testCommitCherryPickedWithNextTargetRelease() throws Exception {
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(UPSTREAM_ISSUE_KEY_0)
         .setShortMessage(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0)
         .setAuthorEmail(TEST_USER_EMAIL);

      MockGitCommit downstreamCommit = new MockGitCommit()
         .setName(DOWNSTREAM_ISSUE_KEY_0)
         .setShortMessage(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0)
         .setFullMessage("downstream: " + DOWNSTREAM_ISSUE_KEY_0)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType(ISSUE_TYPE_BUG);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setTargetRelease("1.1.1.GA")
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH);
      downstreamIssue.getLabels().add(ISSUE_STATE_DEV_COMPLETE);
      downstreamIssue.getLabels().add(releaseVersion.getCandidate());

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);
      commitProcessor.setCherryPickedCommits(Collections.singletonMap(UPSTREAM_ISSUE_KEY_0, new AbstractMap.SimpleEntry<>(downstreamCommit, releaseVersion)));

      Mockito.when(gitRepository.resolveCommit(upstreamCommit.getName())).thenReturn(upstreamCommit);
      Mockito.when(gitRepository.resolveCommit(downstreamCommit.getName())).thenReturn(downstreamCommit);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(upstreamIssueManager.parseIssueKeys(Mockito.anyString())).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);
      Mockito.when(downstreamIssueManager.parseIssueKeys(Mockito.anyString())).thenReturn(Arrays.asList(DOWNSTREAM_ISSUE_KEY_0));

      DownstreamIssueStateMachine downstreamIssueStateMachine = Mockito.mock(DownstreamIssueStateMachine.class);
      Mockito.when(downstreamIssueStateMachine.getStateIndex(Mockito.any())).thenReturn(0);
      Mockito.when(downstreamIssueStateMachine.getIssueStateDevComplete()).thenReturn(ISSUE_STATE_DEV_COMPLETE);
      Mockito.when(downstreamIssueManager.getIssueStateMachine()).thenReturn(downstreamIssueStateMachine);

      Commit commit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals("DOWNSTREAM_ISSUES_WITH_NEXT_TARGET_RELEASE_EXIST", commit.getReason());
   }

   @Test
   public void testCommitIncludedInRevertingChain() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit revertedUpstreamCommit = new MockGitCommit()
         .setName(UPSTREAM_ISSUE_KEY_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      MockGitCommit revertedDownstreamCommit = new MockGitCommit()
         .setName(DOWNSTREAM_ISSUE_KEY_0)
         .setShortMessage(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0)
         .setFullMessage("downstream: " + DOWNSTREAM_ISSUE_KEY_0)
         .setAuthorEmail(TEST_USER_EMAIL);

      MockGitCommit revertingUpstreamCommit = new MockGitCommit()
         .setName(UPSTREAM_ISSUE_KEY_1)
         .setShortMessage("This reverts commit 0.")
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType(ISSUE_TYPE_BUG);

      List<String> upstreamRevertingChain = new ArrayList<>();
      upstreamRevertingChain.add(revertingUpstreamCommit.getName());
      upstreamRevertingChain.add(revertedUpstreamCommit.getName());
      Map<String, List<String>> upstreamRevertingChains = new HashMap<>();
      for(String upstreamRevertingChainItem : upstreamRevertingChain) {
         upstreamRevertingChains.put(upstreamRevertingChainItem, upstreamRevertingChain);
      }

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);
      commitProcessor.setUpstreamRevertingChains(upstreamRevertingChains);

      Mockito.when(gitRepository.resolveCommit(revertedUpstreamCommit.getName())).thenReturn(revertedUpstreamCommit);
      Mockito.when(gitRepository.resolveCommit(revertedDownstreamCommit.getName())).thenReturn(revertedDownstreamCommit);
      Mockito.when(gitRepository.resolveCommit(revertingUpstreamCommit.getName())).thenReturn(revertingUpstreamCommit);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Commit revertingCommit = commitProcessor.process(revertingUpstreamCommit);
      Assert.assertEquals(Commit.State.SKIPPED, revertingCommit.getState());

      Commit revertedCommit = commitProcessor.process(revertedUpstreamCommit);
      Assert.assertEquals(Commit.State.SKIPPED, revertedCommit.getState());

      Map<String, Map.Entry<GitCommit, ReleaseVersion>> cherryPickedCommits = new HashMap<>();
      cherryPickedCommits.put(revertedUpstreamCommit.getName(), new AbstractMap.SimpleEntry<>(revertedDownstreamCommit, releaseVersion));
      commitProcessor.setCherryPickedCommits(cherryPickedCommits);

      Commit revertingCherryPickedCommit = commitProcessor.process(revertingUpstreamCommit);
      Assert.assertEquals(Commit.State.NEW, revertingCherryPickedCommit.getState());

      Commit revertedCherryPickeCommit = commitProcessor.process(revertedUpstreamCommit);
      Assert.assertEquals(Commit.State.DONE, revertedCherryPickeCommit.getState());
   }

   @Test
   public void testCommitSkippedWithInsufficientUpstreamIssues() throws Exception {
      MockGitCommit upstreamCommit = new MockGitCommit()
          .setName(UPSTREAM_ISSUE_KEY_0)
          .setShortMessage(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0)
          .setFullMessage(TEST_MESSAGE)
          .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType(ISSUE_TYPE_IMPROVEMENT);

      currentProjectStream.setMode(ProjectStream.Mode.UPDATING);

      CommitProcessor commitProcessor = new CommitProcessor(
          releaseVersion,
          TARGET_RELEASE_FORMAT,
          projectConfig, CURRENT_PROJECT_STREAM_NAME,
          gitRepository,
          upstreamIssueManager,
          downstreamIssueManager,
          userResolver);

      Mockito.when(gitRepository.resolveCommit(upstreamCommit.getName())).thenReturn(upstreamCommit);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(upstreamIssueManager.parseIssueKeys(Mockito.anyString())).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Commit newCommit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.SKIPPED, newCommit.getState());
      Assert.assertEquals(4, newCommit.getTasks().size());
   }

   @Test
   public void testCheckCommands() throws Exception {
      String upstreamBranch = "main";
      File upstreamRepoDir = testFolder.newFolder("upstream-repo");
      FileUtils.copyDirectory(new File("src/test/resources/org.example.test"), upstreamRepoDir);
      CommandExecutor.execute("git init --initial-branch " + upstreamBranch, upstreamRepoDir);
      CommandExecutor.execute("git config user.name '" + TEST_USER_NAME + "'", upstreamRepoDir);
      CommandExecutor.execute("git config user.email '" + TEST_USER_EMAIL + "'", upstreamRepoDir);
      CommandExecutor.execute("git add --all", upstreamRepoDir);
      CommandExecutor.execute("git commit --message 'Initial commit'", upstreamRepoDir);

      String downstreamBranch = "main";
      File downstreamRepoDir = testFolder.newFolder("downstream-repo");
      FileUtils.copyDirectory(upstreamRepoDir, downstreamRepoDir);

      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      CommandExecutor.execute("git commit --allow-empty -m 'NO-ISSUE test'", upstreamRepoDir);
      CommandExecutor.execute("sed -i.bak 's/Unit test for simple App./Unit test for test App./' src/test/java/org/example/AppTest.java", upstreamRepoDir);
      CommandExecutor.execute("git commit --all --message '" + commitShortMessage + "'", upstreamRepoDir);

      File repoDir = testFolder.newFolder("repo");
      GitRepository gitRepository = new JGitRepository();
      gitRepository.setUserName(TEST_USER_NAME);
      gitRepository.setUserEmail(TEST_USER_EMAIL);
      gitRepository.clone("file://" + downstreamRepoDir.getAbsolutePath(), repoDir);
      gitRepository.remoteAdd("upstream", "file://" + upstreamRepoDir.getAbsolutePath());
      gitRepository.fetch("upstream");
      gitRepository.branchCreate(downstreamBranch, "origin/" + downstreamBranch);
      gitRepository.checkout(downstreamBranch);

      // Load upstream commits
      Map<String, Commit> confirmedCommits = new HashMap<>();
      Deque<GitCommit> upstreamCommits = new ArrayDeque<>();
      for (GitCommit commit : gitRepository.log("upstream/" + upstreamBranch, "origin/" + downstreamBranch)) {
         upstreamCommits.push(commit);

         Commit confirmedCommit = new Commit().
            setUpstreamCommit(commit.getName()).
            setTasks(Collections.singletonList(
            new CommitTask().
               setAction(commit.getShortMessage().startsWith(NO_ISSUE_KEY) ?
               Commit.Action.FORCE : Commit.Action.STEP).
               setType(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT).
               setArgs(commit.getShortMessage().startsWith(NO_ISSUE_KEY) ?
               Map.of("upstreamCommit", commit.getName()) :
               Map.of("upstreamCommit", commit.getName(), "downstreamIssues", DOWNSTREAM_ISSUE_KEY_0)).
               setUserArgs(commit.getShortMessage().startsWith(NO_ISSUE_KEY) ?
               Map.of("skipTests", Boolean.TRUE.toString()) :
               Map.of("skipTests", Boolean.FALSE.toString()))));
         confirmedCommits.put(commit.getName(), confirmedCommit);
      }

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0);
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setTargetRelease("1.0.0.GA")
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setState(ISSUE_RESOLUTION_DONE);
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      DownstreamIssueStateMachine downstreamIssueStateMachine = Mockito.mock(DownstreamIssueStateMachine.class);
      Mockito.when(downstreamIssueStateMachine.getStateIndex(Mockito.any())).thenReturn(0);
      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);
      Mockito.when(downstreamIssueManager.getIssueLabelUpstreamTestCoverage()).thenReturn("upstream-test-coverage");
      Mockito.when(downstreamIssueManager.getIssueStateMachine()).thenReturn(downstreamIssueStateMachine);

      File commitsDir = testFolder.newFolder("commits");
      CommitProcessor commitProcessor = new CommitProcessor(
         ReleaseVersion.fromString("1.0.0.CR1"),
         TARGET_RELEASE_FORMAT,
         projectConfig, PREVIOUS_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      commitProcessor.setCommitsDir(commitsDir);
      commitProcessor.setCheckCommand("mvn --show-version -DskipTests clean package");
      commitProcessor.setCheckTestsCommand("mvn --show-version -Dtest=${TEST} clean package");
      commitProcessor.setConfirmedCommits(confirmedCommits);

      for (GitCommit upstreamCommit : upstreamCommits) {
         Commit commit = commitProcessor.process(upstreamCommit);
         if (upstreamCommit.getShortMessage().startsWith(NO_ISSUE_KEY)) {
            Assert.assertEquals(Commit.State.DONE, commit.getState());
            Assert.assertEquals(Boolean.TRUE.toString(), commit.getTasks().get(0).getUserArgs().get("skipTests"));
         } else {
            Assert.assertEquals(Commit.State.PARTIAL, commit.getState());
            Assert.assertEquals(Boolean.FALSE.toString(), commit.getTasks().get(0).getUserArgs().get("skipTests"));
         }

         File commitDir = new File(commitProcessor.getCommitsDir(), upstreamCommit.getName());
         File checkLogFile = new File(commitDir, "check.log");
         Assert.assertTrue(checkLogFile.exists());
      }
   }

   @Test
   public void testCloneUpstreamIssue() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Map<String, Commit> confirmedCommits = new HashMap<>();
      Commit confirmedCommit = new Commit().
         setUpstreamCommit(upstreamCommit.getName()).
         setTasks(Collections.singletonList(
         new CommitTask().
            setAction(Commit.Action.STEP).
            setType(CommitTask.Type.CLONE_UPSTREAM_ISSUE).
            setArgs(Map.of("issueKey", UPSTREAM_ISSUE_KEY_0))));
      confirmedCommits.put(upstreamCommit.getName(), confirmedCommit);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType(ISSUE_TYPE_BUG).setSummary(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0);

      Mockito.when(downstreamIssueManager.createIssue(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(downstreamIssue);

      DownstreamIssueStateMachine downstreamIssueStateMachine = Mockito.mock(DownstreamIssueStateMachine.class);
      Mockito.when(downstreamIssueStateMachine.getIssueStateToDo()).thenReturn(ISSUE_STATE_TODO);
      Mockito.when(downstreamIssueManager.getIssueStateMachine()).thenReturn(downstreamIssueStateMachine);

      Mockito.doAnswer(invocationContext -> downstreamIssue.setState(invocationContext.getArgument(1))).
         when(downstreamIssueManager).transitionIssue(Mockito.any(), Mockito.any());

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      commitProcessor.setConfirmedCommits(confirmedCommits);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.TODO, commit.getState());
      Assert.assertEquals(2, commit.getTasks().size());
      Assert.assertEquals(1, commit.getDownstreamIssues().size());
      Assert.assertEquals(DOWNSTREAM_ISSUE_KEY_0, commit.getDownstreamIssues().get(0).getKey());

      CommitTask doneTask = commit.getTasks().get(0);
      Assert.assertEquals(CommitTask.Type.CLONE_UPSTREAM_ISSUE, doneTask.getType());
      Assert.assertEquals(CommitTask.State.DONE, doneTask.getState());

      CommitTask todoTask = commit.getTasks().get(1);
      Assert.assertEquals(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, todoTask.getType());
      Assert.assertEquals(CommitTask.State.NEW, todoTask.getState());

      Assert.assertEquals(ISSUE_STATE_TODO, downstreamIssue.getState());
   }

   @Test
   public void testHoldUpstreamIssue() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType(ISSUE_TYPE_BUG).setSummary(TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0);

      Mockito.when(downstreamIssueManager.createIssue(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(downstreamIssue);

      DownstreamIssueStateMachine downstreamIssueStateMachine = Mockito.mock(DownstreamIssueStateMachine.class);
      Mockito.when(downstreamIssueStateMachine.getIssueStateToDo()).thenReturn(ISSUE_STATE_TODO);
      Mockito.when(downstreamIssueManager.getIssueStateMachine()).thenReturn(downstreamIssueStateMachine);

      Mockito.doAnswer(invocationContext -> downstreamIssue.setState(invocationContext.getArgument(1))).
         when(downstreamIssueManager).transitionIssue(Mockito.any(), Mockito.any());

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.NEW, commit.getState());
      Assert.assertEquals(4, commit.getTasks().size());

      CommitTask cloneTask0 = commit.getTasks().get(0);
      Assert.assertEquals(Commit.Action.STEP, cloneTask0.getAction());
      Assert.assertEquals(CommitTask.Type.CLONE_UPSTREAM_ISSUE, cloneTask0.getType());
      Assert.assertEquals(CommitTask.State.NEW, cloneTask0.getState());

      CommitTask cherryPickTask0 = commit.getTasks().get(1);
      Assert.assertEquals(Commit.Action.FORCE, cherryPickTask0.getAction());
      Assert.assertEquals(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, cherryPickTask0.getType());
      Assert.assertEquals(CommitTask.State.NEW, cherryPickTask0.getState());

      CommitTask holdTask0 = commit.getTasks().get(2);
      Assert.assertEquals(Commit.Action.HOLD, holdTask0.getAction());
      Assert.assertEquals(CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE, holdTask0.getType());
      Assert.assertEquals(CommitTask.State.NEW, holdTask0.getState());
      Assert.assertEquals(nextPatchReleaseVersion.toString(), holdTask0.getUserArgs().get("until"));

      CommitTask excludeTask0 = commit.getTasks().get(3);
      Assert.assertEquals(Commit.Action.SKIP, excludeTask0.getAction());
      Assert.assertEquals(CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE, excludeTask0.getType());
      Assert.assertEquals(CommitTask.State.NEW, excludeTask0.getState());

      Map<String, Commit> confirmedCommits = new HashMap<>();
      Commit confirmedCommit = new Commit().
         setUpstreamCommit(upstreamCommit.getName()).
         setTasks(Collections.singletonList(holdTask0));
      confirmedCommits.put(upstreamCommit.getName(), confirmedCommit);

      commitProcessor.setConfirmedCommits(confirmedCommits);
      Commit commitWithDoneTasks = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commitWithDoneTasks.getState());
      Assert.assertEquals(3, commitWithDoneTasks.getTasks().size());

      CommitTask cloneTask1 = commitWithDoneTasks.getTasks().get(0);
      Assert.assertEquals(Commit.Action.STEP, cloneTask1.getAction());
      Assert.assertEquals(CommitTask.Type.CLONE_UPSTREAM_ISSUE, cloneTask1.getType());
      Assert.assertEquals(CommitTask.State.NEW, cloneTask1.getState());

      CommitTask cherryPickTask1 = commitWithDoneTasks.getTasks().get(1);
      Assert.assertEquals(Commit.Action.FORCE, cherryPickTask1.getAction());
      Assert.assertEquals(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, cherryPickTask1.getType());
      Assert.assertEquals(CommitTask.State.NEW, cherryPickTask1.getState());

      CommitTask holdTask1 = commitWithDoneTasks.getTasks().get(2);
      Assert.assertEquals(Commit.Action.HOLD, holdTask1.getAction());
      Assert.assertEquals(CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE, holdTask1.getType());
      Assert.assertEquals(CommitTask.State.DONE, holdTask1.getState());

      Assert.assertEquals(1, projectConfig.getProject().getStream(CURRENT_PROJECT_STREAM_NAME).getExcludedUpstreamIssues().size());
      Assert.assertEquals(UPSTREAM_ISSUE_KEY_0, projectConfig.getProject().getStream(CURRENT_PROJECT_STREAM_NAME).getExcludedUpstreamIssues().get(0).getKey());
      Assert.assertEquals(nextPatchReleaseVersion.toString(), projectConfig.getProject().getStream(CURRENT_PROJECT_STREAM_NAME).getExcludedUpstreamIssues().get(0).getUntil());

      CommitProcessor nextCommitProcessor = new CommitProcessor(
         nextPatchReleaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit nextCommit = nextCommitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.NEW, nextCommit.getState());
      Assert.assertEquals(4, nextCommit.getTasks().size());

      CommitTask cloneTask2 = nextCommit.getTasks().get(0);
      Assert.assertEquals(Commit.Action.STEP, cloneTask2.getAction());
      Assert.assertEquals(CommitTask.Type.CLONE_UPSTREAM_ISSUE, cloneTask2.getType());
      Assert.assertEquals(CommitTask.State.NEW, cloneTask2.getState());

      CommitTask cherryPickTask2 = nextCommit.getTasks().get(1);
      Assert.assertEquals(Commit.Action.FORCE, cherryPickTask2.getAction());
      Assert.assertEquals(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, cherryPickTask2.getType());
      Assert.assertEquals(CommitTask.State.NEW, cherryPickTask2.getState());

      CommitTask holdTask2 = nextCommit.getTasks().get(2);
      Assert.assertEquals(Commit.Action.HOLD, holdTask2.getAction());
      Assert.assertEquals(CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE, holdTask2.getType());
      Assert.assertEquals(CommitTask.State.NEW, holdTask2.getState());
      Assert.assertEquals(nextNextPatchReleaseVersion.toString(), holdTask2.getUserArgs().get("until"));

      CommitTask excludeTask2 = nextCommit.getTasks().get(3);
      Assert.assertEquals(Commit.Action.SKIP, excludeTask2.getAction());
      Assert.assertEquals(CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE, excludeTask2.getType());
      Assert.assertEquals(CommitTask.State.NEW, excludeTask2.getState());

      Map<String, Commit> nextConfirmedCommits = new HashMap<>();
      Commit nextConfirmedCommit = new Commit().
         setUpstreamCommit(upstreamCommit.getName()).
         setTasks(Collections.singletonList(excludeTask2));
      nextConfirmedCommits.put(upstreamCommit.getName(), nextConfirmedCommit);

      nextCommitProcessor.setConfirmedCommits(nextConfirmedCommits);
      Commit nextCommitWithDoneTasks = nextCommitProcessor.process(upstreamCommit);

      Assert.assertEquals(1, projectConfig.getProject().getStream(CURRENT_PROJECT_STREAM_NAME).getExcludedUpstreamIssues().size());
      Assert.assertEquals(UPSTREAM_ISSUE_KEY_0, projectConfig.getProject().getStream(CURRENT_PROJECT_STREAM_NAME).getExcludedUpstreamIssues().get(0).getKey());
      Assert.assertNull(projectConfig.getProject().getStream(CURRENT_PROJECT_STREAM_NAME).getExcludedUpstreamIssues().get(0).getUntil());

      Assert.assertEquals(Commit.State.SKIPPED, nextCommitWithDoneTasks.getState());
      Assert.assertEquals(4, nextCommitWithDoneTasks.getTasks().size());

      CommitTask cloneTask3 = nextCommitWithDoneTasks.getTasks().get(0);
      Assert.assertEquals(Commit.Action.STEP, cloneTask3.getAction());
      Assert.assertEquals(CommitTask.Type.CLONE_UPSTREAM_ISSUE, cloneTask3.getType());
      Assert.assertEquals(CommitTask.State.NEW, cloneTask3.getState());

      CommitTask cherryPickTask3 = nextCommitWithDoneTasks.getTasks().get(1);
      Assert.assertEquals(Commit.Action.FORCE, cherryPickTask3.getAction());
      Assert.assertEquals(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, cherryPickTask3.getType());
      Assert.assertEquals(CommitTask.State.NEW, cherryPickTask3.getState());

      CommitTask holdTask3 = nextCommitWithDoneTasks.getTasks().get(2);
      Assert.assertEquals(Commit.Action.HOLD, holdTask3.getAction());
      Assert.assertEquals(CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE, holdTask3.getType());
      Assert.assertEquals(CommitTask.State.NEW, holdTask3.getState());

      CommitTask excludeTask3 = nextCommitWithDoneTasks.getTasks().get(3);
      Assert.assertEquals(Commit.Action.SKIP, excludeTask3.getAction());
      Assert.assertEquals(CommitTask.Type.EXCLUDE_UPSTREAM_ISSUE, excludeTask3.getType());
      Assert.assertEquals(CommitTask.State.DONE, excludeTask3.getState());
   }

   @Test
   public void testCommitWithDownstreamIssueNotDone() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG);
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setResolution(ISSUE_RESOLUTION_WONT_DO);
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.NEW, commit.getState());
      Assert.assertEquals("UPSTREAM_ISSUE_SUFFICIENT_BUT_NO_DOWNSTREAM_ISSUES", commit.getReason());
   }

   @Test
   public void testCommitWithDownstreamIssueDoneErrata() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG);
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setTargetRelease(PREVIOUS_PROJECT_STREAM_NAME + ".0.CR1")
         .setResolution(ISSUE_RESOLUTION_DONE_ERRATA);
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.NEW, commit.getState());
      Assert.assertEquals("DOWNSTREAM_ISSUES_SUFFICIENT_BUT_NONE_WITH_REQUIRED_TARGET_RELEASE", commit.getReason());
   }

   @Test
   public void testCommitWithNextTargetRelease() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName(COMMIT_NAME_0)
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG);
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType(ISSUE_TYPE_BUG)
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setTargetRelease("1.1.1.CR1");
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn(ISSUE_TYPE_BUG);
      Mockito.when(downstreamIssueManager.getIssueResolutionDone()).thenReturn(ISSUE_RESOLUTION_DONE);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig, CURRENT_PROJECT_STREAM_NAME,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals("DOWNSTREAM_ISSUES_WITH_NEXT_TARGET_RELEASE_EXIST", commit.getReason());
   }
}
