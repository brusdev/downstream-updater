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

import dev.brus.downstream.updater.Commit;
import dev.brus.downstream.updater.CommitProcessor;
import dev.brus.downstream.updater.CommitTask;
import dev.brus.downstream.updater.git.JGitRepository;
import dev.brus.downstream.updater.issue.IssueStateMachine;
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

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals("UPSTREAM_ISSUE_BACKPORT_BLOCKED", commit.getReason());
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
      Assert.assertEquals(3, newCommit.getTasks().size());

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

      IssueStateMachine downstreamIssueStateMachine = Mockito.mock(IssueStateMachine.class);
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
      Mockito.when(downstreamIssueManager.getIssueStateDevComplete()).thenReturn(ISSUE_STATE_DEV_COMPLETE);
      Mockito.when(downstreamIssueManager.parseIssueKeys(Mockito.anyString())).thenReturn(Arrays.asList(DOWNSTREAM_ISSUE_KEY_0));

      IssueStateMachine downstreamIssueStateMachine = Mockito.mock(IssueStateMachine.class);
      Mockito.when(downstreamIssueStateMachine.getStateIndex(Mockito.any())).thenReturn(0);
      Mockito.when(downstreamIssueManager.getIssueStateMachine()).thenReturn(downstreamIssueStateMachine);

      Commit incompleteCommit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.PARTIAL, incompleteCommit.getState());

      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Commit doneCommit = commitProcessor.process(upstreamCommit);
      Assert.assertEquals(Commit.State.DONE, doneCommit.getState());
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

      IssueStateMachine downstreamIssueStateMachine = Mockito.mock(IssueStateMachine.class);
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
      Mockito.when(downstreamIssueManager.getIssueStateToDo()).thenReturn(ISSUE_STATE_TODO);

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

      CommitTask doneTask = commit.getTasks().get(0);
      Assert.assertEquals(CommitTask.Type.CLONE_UPSTREAM_ISSUE, doneTask.getType());
      Assert.assertEquals(CommitTask.State.DONE, doneTask.getState());

      CommitTask todoTask = commit.getTasks().get(1);
      Assert.assertEquals(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT, todoTask.getType());
      Assert.assertEquals(CommitTask.State.NEW, todoTask.getState());

      Assert.assertEquals(ISSUE_STATE_TODO, downstreamIssue.getState());
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
      downstreamIssue.getLabels().add(ISSUE_LABEL_NO_BACKPORT_NEEDED);
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
      downstreamIssue.getLabels().add(ISSUE_LABEL_NO_BACKPORT_NEEDED);
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
}
