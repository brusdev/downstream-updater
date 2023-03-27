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

   private final static String NO_ISSUE_KEY = "NO-ISSUE";
   private final static String UPSTREAM_ISSUE_KEY_0 = "ARTEMIS-0";
   private final static String DOWNSTREAM_ISSUE_KEY_0 = "ENTMQBR-0";
   private final static String TEST_USER_NAME = "test";
   private final static String TEST_USER_EMAIL = "test@user.com";
   private final static String TARGET_RELEASE_FORMAT = "%d.%d.%d.GA";

   @Rule
   public TemporaryFolder testFolder = new TemporaryFolder();

   private ReleaseVersion releaseVersion;
   private User testUser;

   private GitRepository gitRepository;

   private UserResolver userResolver;

   private IssueManager upstreamIssueManager;

   private DownstreamIssueManager downstreamIssueManager;

   @Before
   public void initMocks() throws Exception {
      releaseVersion = ReleaseVersion.fromString("1.1.0.CR1");

      testUser = new User().setUsername(TEST_USER_NAME)
         .setEmailAddresses(new String[] {TEST_USER_EMAIL});

      gitRepository = Mockito.mock(GitRepository.class);
      Mockito.when(gitRepository.remoteGet("origin")).thenReturn("https://github.com/origin/test.git");
      Mockito.when(gitRepository.remoteGet("upstream")).thenReturn("https://github.com/upstream/test.git");

      userResolver = new UserResolver(new User[] {testUser}).setDefaultUser(testUser);

      upstreamIssueManager = Mockito.mock(IssueManager.class);

      downstreamIssueManager = Mockito.mock(DownstreamIssueManager.class);
   }

   @Test
   public void testCommitNotRequiringReleaseIssue() throws Exception {
      String commitShortMessage = UPSTREAM_ISSUE_KEY_0 + " Test message";
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName("0")
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0);
      upstreamIssue.getIssues().add(DOWNSTREAM_ISSUE_KEY_0);

      Issue downstreamIssue = new Issue().setKey(DOWNSTREAM_ISSUE_KEY_0)
         .setType("Bug")
         .setTargetRelease("1.0.0.GA")
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setState("Done");
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn("Bug");
      Mockito.when(downstreamIssueManager.getIssueStateDone()).thenReturn("Done");

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.TODO, commit.getState());
   }

   @Test
   public void testCommitWithoutUpstreamIssue() throws Exception {
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName("0")
         .setShortMessage(NO_ISSUE_KEY + " Test message")
         .setAuthorEmail(TEST_USER_EMAIL);

      CommitProcessor commitProcessor = new CommitProcessor(
         releaseVersion,
         TARGET_RELEASE_FORMAT,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals(TEST_USER_NAME, commit.getAssignee());
   }

   @Test
   public void testCommitIncludedInRevertingChain() throws Exception {
      String commitShortMessage = UPSTREAM_ISSUE_KEY_0 + " Test message";
      MockGitCommit revertedUpstreamCommit = new MockGitCommit()
         .setName("UP-0")
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_USER_EMAIL);

      MockGitCommit revertedDownstreamCommit = new MockGitCommit()
         .setName("DOWN-0")
         .setShortMessage(UPSTREAM_ISSUE_KEY_0 + " Test message")
         .setFullMessage("downstream: " + DOWNSTREAM_ISSUE_KEY_0)
         .setAuthorEmail(TEST_USER_EMAIL);

      MockGitCommit revertingUpstreamCommit = new MockGitCommit()
         .setName("UP-1")
         .setShortMessage("This reverts commit 0.")
         .setAuthorEmail(TEST_USER_EMAIL);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType("Bug");

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
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);
      commitProcessor.setUpstreamRevertingChains(upstreamRevertingChains);

      Mockito.when(gitRepository.resolveCommit(revertedUpstreamCommit.getName())).thenReturn(revertedUpstreamCommit);
      Mockito.when(gitRepository.resolveCommit(revertedDownstreamCommit.getName())).thenReturn(revertedDownstreamCommit);
      Mockito.when(gitRepository.resolveCommit(revertingUpstreamCommit.getName())).thenReturn(revertingUpstreamCommit);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.getIssueTypeBug()).thenReturn("Bug");
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
      Assert.assertEquals(Commit.State.INCOMPLETE, revertedCherryPickeCommit.getState());
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

      String commitShortMessage = UPSTREAM_ISSUE_KEY_0 + " Test message";
      CommandExecutor.execute("git commit --allow-empty -m 'NO-ISSUE test'", upstreamRepoDir);
      CommandExecutor.execute("sed -i.bak 's/Unit test for simple App./Unit test for test App./' src/test/java/org/example/AppTest.java", upstreamRepoDir);
      CommandExecutor.execute("git commit --all --message '" + commitShortMessage + "'", upstreamRepoDir);

      File repoDir = testFolder.newFolder("repo");
      GitRepository gitRepository = new JGitRepository();
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
         .setType("Bug")
         .setTargetRelease("1.0.0.GA")
         .setCustomer(true)
         .setCustomerPriority(IssueCustomerPriority.HIGH)
         .setState("Done");
      downstreamIssue.getIssues().add(UPSTREAM_ISSUE_KEY_0);

      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);
      Mockito.when(upstreamIssueManager.parseIssueKeys(commitShortMessage)).thenReturn(Arrays.asList(UPSTREAM_ISSUE_KEY_0));

      IssueStateMachine downstreamIssueStateMachine = Mockito.mock(IssueStateMachine.class);
      Mockito.when(downstreamIssueStateMachine.getStateIndex(Mockito.any())).thenReturn(0);
      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn("Bug");
      Mockito.when(downstreamIssueManager.getIssueStateDone()).thenReturn("Done");
      Mockito.when(downstreamIssueManager.getIssueLabelUpstreamTestCoverage()).thenReturn("upstream-test-coverage");
      Mockito.when(downstreamIssueManager.getIssueStateMachine()).thenReturn(downstreamIssueStateMachine);

      File commitsDir = testFolder.newFolder("commits");
      CommitProcessor commitProcessor = new CommitProcessor(
         ReleaseVersion.fromString("1.0.0.CR1"),
         TARGET_RELEASE_FORMAT,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      commitProcessor.setCommitsDir(commitsDir);
      commitProcessor.setCheckCommand("mvn --show-version -DskipTests clean package");
      commitProcessor.setCheckTestsCommand("mvn --show-version -Dtest=%s clean package");
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
