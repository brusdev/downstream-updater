package dev.brus.downstream.updater.issue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class IssueManagerFactory {
   private boolean isAtlassianCloudHost(String serverURL) {
      try {
         String host = new URI(serverURL).getHost();
         return host != null && host.toLowerCase(Locale.ROOT).endsWith(".atlassian.net");
      } catch (URISyntaxException e) {
         return false;
      }
   }

   private boolean isDownstreamJiraHost(String serverURL) {
      try {
         String host = new URI(serverURL).getHost();
         if (host == null) {
            return false;
         }

         String normalizedHost = host.toLowerCase(Locale.ROOT);
         return "issues.redhat.com".equals(normalizedHost) || normalizedHost.endsWith(".atlassian.net");
      } catch (URISyntaxException e) {
         return false;
      }
   }

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
      if (isDownstreamJiraHost(serverURL)) {
         String apiVersion = isAtlassianCloudHost(serverURL) ? JiraIssueManager.REST_API_PATH_V3 : JiraIssueManager.REST_API_PATH_V2;
         return new RedHatJiraIssueManager(serverURL, authString, projectKey, apiVersion, new RedHatIssueStateMachine(), upstreamIssueManager);
      } else {
         throw new IllegalArgumentException("Issue server URL not supported: " + serverURL);
      }
   }
}
