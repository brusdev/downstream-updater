package dev.brus.midstream.updater.issue;

import java.util.List;

import dev.brus.downstream.updater.issue.GithubIssueManager;
import org.junit.Assert;
import org.junit.Test;

public class GithubIssueManagerTest {

   @Test
   public void testParseCommentWithMultipleUpstreamIssues() throws Exception {
      GithubIssueManager manager = new GithubIssueManager(
         "https://api.github.com/repos/test-org/test-rep/issues", "DUMMY-AUTH", "TEST");

      List<String> issues = manager.parseIssueKeys("[#0][#1][#2] Test message");
      Assert.assertEquals(3, issues.size());
      Assert.assertEquals("TEST-0", issues.get(0));
      Assert.assertEquals("TEST-1", issues.get(1));
      Assert.assertEquals("TEST-2", issues.get(2));

      List<String> spacedIssues = manager.parseIssueKeys("[#0] [#1] [#2] Test message");
      Assert.assertEquals(3, spacedIssues.size());
      Assert.assertEquals("TEST-0", spacedIssues.get(0));
      Assert.assertEquals("TEST-1", spacedIssues.get(1));
      Assert.assertEquals("TEST-2", spacedIssues.get(2));
   }
}
