package dev.brus.midstream.updater.issue;

import java.io.InputStreamReader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.brus.downstream.updater.issue.Issue;
import dev.brus.downstream.updater.issue.IssueManager;
import dev.brus.downstream.updater.issue.JiraIssueManager;
import dev.brus.downstream.updater.issue.RedHatIssueStateMachine;
import dev.brus.downstream.updater.issue.RedHatJiraIssueManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class RedHatJiraIssueManagerTest {

   @Test
   public void testAddIssueUpstreamIssues() throws Exception {
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();

      String upstreamServerBaseURL = "https://issues.apache.org/jira";
      IssueManager upstreamIssueManager = Mockito.spy(new JiraIssueManager(upstreamServerBaseURL + "/rest/api/2",
         null, "ARTEMIS"));

      String upstreamIssueKey0 = "ARTEMIS-100";
      String upstreamIssueUrl0 = upstreamServerBaseURL + "/browse/" + upstreamIssueKey0;
      Mockito.when(upstreamIssueManager.getIssue(upstreamIssueKey0)).thenReturn(
         new Issue().setKey(upstreamIssueKey0).setUrl(upstreamIssueUrl0));

      String upstreamIssueKey1 = "ARTEMIS-101";
      String upstreamIssueUrl1 = upstreamServerBaseURL + "/browse/" + upstreamIssueKey0;
      Mockito.when(upstreamIssueManager.getIssue(upstreamIssueKey1)).thenReturn(
         new Issue().setKey(upstreamIssueKey1).setUrl(upstreamIssueUrl1));

      RedHatJiraIssueManager issueManager = new RedHatJiraIssueManager(mockWebServer.url("rest/api/2").toString(),
         null, "ENTMQBR", new RedHatIssueStateMachine(), upstreamIssueManager);

      String downstreamIssueKey = "ENTMQBR-100";
      JsonObject downstreamIssueObject = new JsonObject();
      {
         downstreamIssueObject.addProperty("key", downstreamIssueKey);
         JsonObject fieldsObject = new JsonObject();
         fieldsObject.addProperty("customfield_12314640", upstreamIssueKey0);
         downstreamIssueObject.add("fields", fieldsObject);
      }
      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(downstreamIssueObject.toString()));

      mockWebServer.enqueue(new MockResponse());

      issueManager.addIssueUpstreamIssues(downstreamIssueKey, upstreamIssueKey1);

      RecordedRequest loadIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(loadIssueRecordedRequest.getBody());

      RecordedRequest putIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(putIssueRecordedRequest.getBody());

      try (InputStreamReader inputStreamReader = new InputStreamReader(putIssueRecordedRequest.getBody().inputStream())) {
         JsonObject issueObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

         JsonObject issueFields = issueObject.getAsJsonObject("fields");
         JsonElement upstreamJiraField = issueFields.get("customfield_12314640");
         String upstreamJiraFieldValue = upstreamJiraField.getAsString();
         Assert.assertTrue(upstreamJiraFieldValue.contains(upstreamIssueUrl0 + ", " + upstreamIssueUrl1));
      }

      mockWebServer.shutdown();
   }
}
