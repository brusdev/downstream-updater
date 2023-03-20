package dev.brus.midstream.updater;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.brus.downstream.updater.Commit;
import dev.brus.downstream.updater.CommitProcessor;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class CommitProcessorTest {

   private final static String NO_JIRA_KEY = "NO-JIRA";
   private final static String UPSTREAM_ISSUE_KEY_0 = "ARTEMIS-0";
   private final static String DOWNSTREAM_ISSUE_KEY_0 = "ENTMQBR-0";
   private final static String TEST_USERNAME = "test";
   private final static String TEST_EMAIL_ADDRESS = "test@user.com";

   private ReleaseVersion releaseVersion;
   private User testUser;

   private GitRepository gitRepository;

   private UserResolver userResolver;

   private IssueManager upstreamIssueManager;

   private DownstreamIssueManager downstreamIssueManager;

   @Before
   public void initMocks() throws Exception {
      releaseVersion = ReleaseVersion.fromString("1.1.0.CR1");

      testUser = new User().setUsername(TEST_USERNAME)
         .setEmailAddresses(new String[] {TEST_EMAIL_ADDRESS});

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
         .setAuthorEmail(TEST_EMAIL_ADDRESS);

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

      CommitProcessor commitProcessor = new CommitProcessor(releaseVersion,
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
         .setShortMessage(NO_JIRA_KEY + " Test message")
         .setAuthorEmail(TEST_EMAIL_ADDRESS);

      CommitProcessor commitProcessor = new CommitProcessor(releaseVersion,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals(TEST_USERNAME, commit.getAssignee());
   }

   @Test
   public void testCommitIncludedInRevertingChain() throws Exception {
      String commitShortMessage = UPSTREAM_ISSUE_KEY_0 + " Test message";
      MockGitCommit revertedUpstreamCommit = new MockGitCommit()
         .setName("UP-0")
         .setShortMessage(commitShortMessage)
         .setAuthorEmail(TEST_EMAIL_ADDRESS);

      MockGitCommit revertedDownstreamCommit = new MockGitCommit()
         .setName("DOWN-0")
         .setShortMessage(UPSTREAM_ISSUE_KEY_0 + " Test message")
         .setFullMessage("downstream: " + DOWNSTREAM_ISSUE_KEY_0)
         .setAuthorEmail(TEST_EMAIL_ADDRESS);

      MockGitCommit revertingUpstreamCommit = new MockGitCommit()
         .setName("UP-1")
         .setShortMessage("This reverts commit 0.")
         .setAuthorEmail(TEST_EMAIL_ADDRESS);

      Issue upstreamIssue = new Issue().setKey(UPSTREAM_ISSUE_KEY_0).setType("Bug");

      List<String> upstreamRevertingChain = new ArrayList<>();
      upstreamRevertingChain.add(revertingUpstreamCommit.getName());
      upstreamRevertingChain.add(revertedUpstreamCommit.getName());
      Map<String, List<String>> upstreamRevertingChains = new HashMap<>();
      for(String upstreamRevertingChainItem : upstreamRevertingChain) {
         upstreamRevertingChains.put(upstreamRevertingChainItem, upstreamRevertingChain);
      }

      CommitProcessor commitProcessor = new CommitProcessor(releaseVersion,
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
}
