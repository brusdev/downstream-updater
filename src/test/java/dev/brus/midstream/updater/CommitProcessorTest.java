package dev.brus.midstream.updater;

import dev.brus.downstream.updater.Commit;
import dev.brus.downstream.updater.CommitProcessor;
import dev.brus.downstream.updater.ReleaseVersion;
import dev.brus.downstream.updater.git.GitRepository;
import dev.brus.downstream.updater.issues.Issue;
import dev.brus.downstream.updater.issues.IssueCustomerPriority;
import dev.brus.downstream.updater.issues.IssueManager;
import dev.brus.downstream.updater.users.User;
import dev.brus.downstream.updater.users.UserResolver;
import dev.brus.midstream.updater.git.MockGitCommit;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class CommitProcessorTest {

   private final static String NO_JIRA_KEY = "NO-JIRA";
   private final static String UPSTREAM_ISSUE_KEY_0 = "ARTEMIS-0";
   private final static String DOWNSTREAM_ISSUE_KEY_0 = "ENTMQBR-0";
   private final static String TEST_USERNAME = "test";
   private final static String TEST_EMAIL_ADDRESS = "test@user.com";

   @Test
   public void testCommitNotRequiringReleaseIssue() throws Exception {
      MockGitCommit upstreamCommit = new MockGitCommit()
         .setName("0")
         .setShortMessage(UPSTREAM_ISSUE_KEY_0 + " Test message")
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

      User user = new User().setUsername(TEST_USERNAME)
         .setEmailAddresses(new String[] {TEST_EMAIL_ADDRESS});

      GitRepository gitRepository = Mockito.mock(GitRepository.class);;

      ReleaseVersion candidateReleaseVersion = new ReleaseVersion("1.1.0.CR1");

      UserResolver userResolver = new UserResolver(new User[] { user }).setDefaultUser(user);

      IssueManager upstreamIssueManager = Mockito.mock(IssueManager.class);
      Mockito.when(upstreamIssueManager.getIssue(UPSTREAM_ISSUE_KEY_0)).thenReturn(upstreamIssue);

      IssueManager downstreamIssueManager = Mockito.mock(IssueManager.class);
      Mockito.when(downstreamIssueManager.getIssue(DOWNSTREAM_ISSUE_KEY_0)).thenReturn(downstreamIssue);
      Mockito.when(downstreamIssueManager.getIssueTypeBug()).thenReturn("Bug");
      Mockito.when(downstreamIssueManager.getIssueStateDone()).thenReturn("Done");

      CommitProcessor commitProcessor = new CommitProcessor(
         candidateReleaseVersion,
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

      User user = new User().setUsername(TEST_USERNAME)
         .setEmailAddresses(new String[] {TEST_EMAIL_ADDRESS});

      GitRepository gitRepository = Mockito.mock(GitRepository.class);;

      ReleaseVersion candidateReleaseVersion = new ReleaseVersion("1.1.0.CR1");

      UserResolver userResolver = new UserResolver(new User[] { user }).setDefaultUser(user);

      IssueManager upstreamIssueManager = Mockito.mock(IssueManager.class);

      IssueManager downstreamIssueManager = Mockito.mock(IssueManager.class);

      CommitProcessor commitProcessor = new CommitProcessor(
         candidateReleaseVersion,
         gitRepository,
         upstreamIssueManager,
         downstreamIssueManager,
         userResolver);

      Commit commit = commitProcessor.process(upstreamCommit);

      Assert.assertEquals(Commit.State.SKIPPED, commit.getState());
      Assert.assertEquals(TEST_USERNAME, commit.getAssignee());
   }
}
