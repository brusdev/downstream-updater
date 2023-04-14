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
import dev.brus.downstream.updater.project.ProjectConfig;
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
   private static final String ISSUE_LABEL_NO_BACKPORT_NEEDED = "NO-BACKPORT-NEEDED";
   private static final String ISSUE_STATE_DEV_COMPLETE = "Dev Complete";

   @Rule
   public TemporaryFolder testFolder = new TemporaryFolder();

   private ReleaseVersion releaseVersion;
   private String projectStream;
   private User testUser;

   private ProjectConfig projectConfig;
   private GitRepository gitRepository;

   private UserResolver userResolver;

   private IssueManager upstreamIssueManager;

   private DownstreamIssueManager downstreamIssueManager;

   @Before
   public void initMocks() throws Exception {
      releaseVersion = ReleaseVersion.fromString("1.1.0.CR1");
      projectStream = "1.1";

      testUser = new User().setUsername(TEST_USER_NAME)
         .setEmailAddresses(new String[] {TEST_USER_EMAIL});

      projectConfig = Mockito.mock(ProjectConfig.class);

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
         projectConfig,
         projectStream,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.TODO, commit.getState());
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
         projectConfig,
         projectStream,
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

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         projectConfig,
         projectStream,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      commitProcessor.setExcludedUpstreamIssues(Collections.singletonMap(UPSTREAM_ISSUE_KEY_0, upstreamIssue));

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals("NO_VALID_UPSTREAM_ISSUES", commit.getReason());
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
         projectConfig,
         projectStream,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
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
         projectConfig,
         projectStream,
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
         projectConfig,
         projectStream,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.NEW, commit.getState());
      Assert.assertEquals(CommitTask.Action.STEP, commit.getTasks().get(0).getAction());
      Assert.assertEquals(CommitTask.Type.CLONE_UPSTREAM_ISSUE, commit.getTasks().get(0).getType());
      Assert.assertEquals(UPSTREAM_ISSUE_KEY_1, commit.getTasks().get(0).getArgs().get("issueKey"));
   }

   @Test
   public void testCommitCherryPicked() throws Exception {
      String commitShortMessage = TEST_MESSAGE_UPSTREAM_ISSUE_KEY_0;
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
         projectConfig,
         projectStream,
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
      Assert.assertEquals(Commit.State.INCOMPLETE, incompleteCommit.getState());

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
         projectConfig,
         projectStream,
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
               setType(CommitTask.Type.CHERRY_PICK_UPSTREAM_COMMIT).
               setArgs(commit.getShortMessage().startsWith(NO_ISSUE_KEY) ?
               Collections.emptyMap() : Map.of("downstreamIssues", DOWNSTREAM_ISSUE_KEY_0))));
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
         projectConfig,
         "1.0",
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
         } else {
            Assert.assertEquals(Commit.State.INCOMPLETE, commit.getState());
         }

         File commitDir = new File(commitProcessor.getCommitsDir(), upstreamCommit.getName());
         File checkLogFile = new File(commitDir, "check.log");
         Assert.assertTrue(checkLogFile.exists());
      }
   }
}
