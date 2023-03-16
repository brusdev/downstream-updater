package dev.brus.downstream.updater.issue;

public class IssueManagerFactory {
   public IssueManager getIssueManager(String serverURL, String authString, String projectKey) {
      if (serverURL.contains("issues.apache.org")) {
         return new JiraIssueManager(serverURL, authString, projectKey);
      } else if (serverURL.contains("api.github.com")) {
         return new GithubIssueManager(serverURL, authString, projectKey);
      } else {
         throw new IllegalArgumentException("Issue server URL not supported: " + serverURL);
      }
   }

   public DownstreamIssueManager getDownstreamIssueManager(String serverURL, String authString, String projectKey, IssueManager upstreamIssueManager) {
      if (serverURL.contains("issues.redhat.com")) {
         return new RedHatJiraIssueManager(serverURL, authString, projectKey, new RedHatIssueStateMachine(), upstreamIssueManager);
      } else {
         throw new IllegalArgumentException("Issue server URL not supported: " + serverURL);
      }
   }
}
