package dev.brus.midstream.updater.issue;

import java.io.InputStreamReader;
import java.util.Collections;

import com.google.gson.JsonArray;
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
   private final static String TEST_USER_NAME = "test";

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

   @Test
   public void testCreateIssue() throws Exception {
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();

      String upstreamServerBaseURL = "https://issues.apache.org/jira";
      IssueManager upstreamIssueManager = Mockito.spy(new JiraIssueManager(upstreamServerBaseURL + "/rest/api/2",
         null, "ARTEMIS"));

      RedHatJiraIssueManager issueManager = new RedHatJiraIssueManager(mockWebServer.url("rest/api/2").toString(),
         null, "ENTMQBR", new RedHatIssueStateMachine(), upstreamIssueManager);

      String downstreamIssueKey = "ENTMQBR-100";
      JsonObject createReponseDownstreamIssueObject = new JsonObject();
      {
         createReponseDownstreamIssueObject.addProperty("key", downstreamIssueKey);
      }
      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(createReponseDownstreamIssueObject.toString()));

      JsonObject downstreamIssueObject = new JsonObject();
      {
         downstreamIssueObject.addProperty("key", downstreamIssueKey);
         JsonObject fieldsObject = new JsonObject();
         {
            JsonObject userObject = new JsonObject();
            userObject.addProperty("name", TEST_USER_NAME);
            fieldsObject.add("creator", userObject);
            fieldsObject.add("reporter", userObject);

            JsonObject statusObject = new JsonObject();
            statusObject.addProperty("name", "New");
            fieldsObject.add("status", statusObject);

            JsonObject issueTypeObject = new JsonObject();
            issueTypeObject.addProperty("name", "Bug");
            fieldsObject.add("issuetype", issueTypeObject);

            fieldsObject.addProperty("summary", "Test");
            fieldsObject.addProperty("created", "2000-01-01T00:00:00.000+0000");
            fieldsObject.addProperty("updated", "2000-01-01T00:00:00.000+0000");
         }
         downstreamIssueObject.add("fields", fieldsObject);
      }
      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(downstreamIssueObject.toString()));

      Issue newIssue = issueManager.createIssue("Productize AMQ Broker 7.10.3.OPR.1.CR1", "", "Task", "dbruscin", "AMQ 7.10.3.OPR.1.GA", Collections.emptyList());

      RecordedRequest createIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(createIssueRecordedRequest.getBody());

      Assert.assertEquals(downstreamIssueKey, newIssue.getKey());

      mockWebServer.shutdown();
   }

   @Test
   public void testTransitionIssue() throws Exception {
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();

      String upstreamServerBaseURL = "https://issues.apache.org/jira";
      IssueManager upstreamIssueManager = Mockito.spy(new JiraIssueManager(upstreamServerBaseURL + "/rest/api/2",
         null, "ARTEMIS"));

      RedHatJiraIssueManager issueManager = new RedHatJiraIssueManager(mockWebServer.url("rest/api/2").toString(),
         null, "ENTMQBR", new RedHatIssueStateMachine(), upstreamIssueManager);

      String downstreamIssueKey = "ENTMQBR-100";
      JsonObject statusDownstreamIssueObject = new JsonObject();
      {
         statusDownstreamIssueObject.addProperty("key", downstreamIssueKey);
         JsonObject fieldsObject = new JsonObject();
         {
            JsonObject statusObject = new JsonObject();
            statusObject.addProperty("name", "New");
            fieldsObject.add("status", statusObject);
         }
         statusDownstreamIssueObject.add("fields", fieldsObject);
      }
      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(statusDownstreamIssueObject.toString()));

      JsonObject transitionsDownstreamIssueObject = new JsonObject();
      {
         JsonArray transitionsObject = new JsonArray();
         {
            JsonObject transitionObject = new JsonObject();
            transitionObject.addProperty("id", "0");

            JsonObject toObject = new JsonObject();
            toObject.addProperty("name", "Done");
            transitionObject.add("to", toObject);

            transitionsObject.add(transitionObject);
         }
         transitionsDownstreamIssueObject.add("transitions", transitionsObject);
      }
      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(transitionsDownstreamIssueObject.toString()));

      mockWebServer.enqueue(new MockResponse());

      issueManager.transitionIssue(downstreamIssueKey, "Done");

      RecordedRequest statusIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(statusIssueRecordedRequest.getBody());

      RecordedRequest transitionsIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(transitionsIssueRecordedRequest.getBody());

      RecordedRequest transitionIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(transitionIssueRecordedRequest.getBody());

      mockWebServer.shutdown();
   }

   @Test
   public void testLoadDocumentationIssue() throws Exception {
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();

      String upstreamServerBaseURL = "https://issues.apache.org/jira";
      IssueManager upstreamIssueManager = Mockito.spy(new JiraIssueManager(upstreamServerBaseURL + "/rest/api/2",
         null, "ARTEMIS"));

      RedHatJiraIssueManager issueManager = new RedHatJiraIssueManager(mockWebServer.url("rest/api/2").toString(),
         null, "ENTMQBR", new RedHatIssueStateMachine(), upstreamIssueManager);

      JsonObject emptySearchResultObject = new JsonObject();
      {
         emptySearchResultObject.addProperty("startAt", 0);
         emptySearchResultObject.addProperty("maxResults", 0);
         emptySearchResultObject.addProperty("total", 1);
         emptySearchResultObject.add("issues", new JsonArray());
      }
      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(emptySearchResultObject.toString()));

      String downstreamIssueKey = "ENTMQBR-100";
      JsonObject searchResultObject = new JsonObject();
      {
         searchResultObject.addProperty("startAt", 0);
         searchResultObject.addProperty("maxResults", 0);
         searchResultObject.addProperty("total", 1);

         JsonArray issuesObject = new JsonArray();
         {
            JsonObject downstreamIssueObject = new JsonObject();
            {
               downstreamIssueObject.addProperty("key", downstreamIssueKey);
               JsonObject fieldsObject = new JsonObject();
               {
                  JsonObject userObject = new JsonObject();
                  userObject.addProperty("name", TEST_USER_NAME);
                  fieldsObject.add("creator", userObject);
                  fieldsObject.add("reporter", userObject);

                  JsonObject statusObject = new JsonObject();
                  statusObject.addProperty("name", "New");
                  fieldsObject.add("status", statusObject);

                  JsonObject issueTypeObject = new JsonObject();
                  issueTypeObject.addProperty("name", "Bug");
                  fieldsObject.add("issuetype", issueTypeObject);

                  JsonArray componentsArray = new JsonArray();
                  {
                     JsonObject documentationComponentObject = new JsonObject();
                     documentationComponentObject.addProperty("name", "documentation");
                     componentsArray.add(documentationComponentObject);
                  }
                  fieldsObject.add("components", componentsArray);

                  fieldsObject.addProperty("summary", "Test");
                  fieldsObject.addProperty("created", "2000-01-01T00:00:00.000+0000");
                  fieldsObject.addProperty("updated", "2000-01-01T00:00:00.000+0000");
               }
               downstreamIssueObject.add("fields", fieldsObject);
            }
            issuesObject.add(downstreamIssueObject);
         }
         searchResultObject.add("issues", issuesObject);
      }
      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(searchResultObject.toString()));

      issueManager.loadIssues();

      Issue issue = issueManager.getIssue(downstreamIssueKey);
      Assert.assertNotNull(issue);
      Assert.assertTrue(issue.isDocumentation());

      RecordedRequest statusIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(statusIssueRecordedRequest.getBody());

      RecordedRequest transitionsIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(transitionsIssueRecordedRequest.getBody());

      mockWebServer.shutdown();
   }

   @Test
   public void testLoadingIssueWithRateLimit() throws Exception {
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();

      String upstreamServerBaseURL = "https://issues.apache.org/jira";
      IssueManager upstreamIssueManager = Mockito.spy(new JiraIssueManager(upstreamServerBaseURL + "/rest/api/2",
          null, "ARTEMIS"));

      RedHatJiraIssueManager issueManager = new RedHatJiraIssueManager(mockWebServer.url("rest/api/2").toString(),
          null, "ENTMQBR", new RedHatIssueStateMachine(), upstreamIssueManager);

      mockWebServer.enqueue(new MockResponse()
          .setResponseCode(429));

      JsonObject emptySearchResultObject = new JsonObject();
      {
         emptySearchResultObject.addProperty("startAt", 0);
         emptySearchResultObject.addProperty("maxResults", 0);
         emptySearchResultObject.addProperty("total", 1);
         emptySearchResultObject.add("issues", new JsonArray());
      }
      mockWebServer.enqueue(new MockResponse()
          .addHeader("Content-Type", "application/json; charset=utf-8")
          .setBody(emptySearchResultObject.toString()));

      mockWebServer.enqueue(new MockResponse()
          .setResponseCode(429));

      String downstreamIssueKey = "ENTMQBR-100";
      JsonObject searchResultObject = new JsonObject();
      {
         searchResultObject.addProperty("startAt", 0);
         searchResultObject.addProperty("maxResults", 0);
         searchResultObject.addProperty("total", 1);

         JsonArray issuesObject = new JsonArray();
         {
            JsonObject downstreamIssueObject = new JsonObject();
            {
               downstreamIssueObject.addProperty("key", downstreamIssueKey);
               JsonObject fieldsObject = new JsonObject();
               {
                  JsonObject userObject = new JsonObject();
                  userObject.addProperty("name", TEST_USER_NAME);
                  fieldsObject.add("creator", userObject);
                  fieldsObject.add("reporter", userObject);

                  JsonObject statusObject = new JsonObject();
                  statusObject.addProperty("name", "New");
                  fieldsObject.add("status", statusObject);

                  JsonObject issueTypeObject = new JsonObject();
                  issueTypeObject.addProperty("name", "Bug");
                  fieldsObject.add("issuetype", issueTypeObject);

                  fieldsObject.addProperty("summary", "Test");
                  fieldsObject.addProperty("created", "2000-01-01T00:00:00.000+0000");
                  fieldsObject.addProperty("updated", "2000-01-01T00:00:00.000+0000");
               }
               downstreamIssueObject.add("fields", fieldsObject);
            }
            issuesObject.add(downstreamIssueObject);
         }
         searchResultObject.add("issues", issuesObject);
      }
      mockWebServer.enqueue(new MockResponse()
          .addHeader("Content-Type", "application/json; charset=utf-8")
          .setBody(searchResultObject.toString()));

      issueManager.loadIssues();

      Issue issue = issueManager.getIssue(downstreamIssueKey);
      Assert.assertNotNull(issue);

      RecordedRequest statusIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(statusIssueRecordedRequest.getBody());

      RecordedRequest transitionsIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(transitionsIssueRecordedRequest.getBody());

      mockWebServer.shutdown();
   }

   @Test
   public void testCreateIssueAccountIdForV3Assignee() throws Exception {
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();

      String upstreamServerBaseURL = "https://issues.apache.org/jira";
      IssueManager upstreamIssueManager = Mockito.spy(new JiraIssueManager(upstreamServerBaseURL, null, "ARTEMIS"));

      RedHatJiraIssueManager issueManager = new RedHatJiraIssueManager(
         mockWebServer.url("/").toString(),
         null,
         "ENTMQBR",
         JiraIssueManager.REST_API_PATH_V3,
         new RedHatIssueStateMachine(),
         upstreamIssueManager);

      String downstreamIssueKey = "ENTMQBR-100";

      JsonObject createResponseDownstreamIssueObject = new JsonObject();
      createResponseDownstreamIssueObject.addProperty("key", downstreamIssueKey);

      JsonObject downstreamIssueObject = new JsonObject();
      downstreamIssueObject.addProperty("key", downstreamIssueKey);
      JsonObject fieldsObject = new JsonObject();

      JsonObject userObject = new JsonObject();
      userObject.addProperty("accountId", "test");
      fieldsObject.add("creator", userObject);
      fieldsObject.add("reporter", userObject);
      fieldsObject.add("assignee", userObject);

      JsonObject statusObject = new JsonObject();
      statusObject.addProperty("name", "New");
      fieldsObject.add("status", statusObject);

      JsonObject issueTypeObject = new JsonObject();
      issueTypeObject.addProperty("name", "Task");
      fieldsObject.add("issuetype", issueTypeObject);

      fieldsObject.addProperty("summary", "Productize AMQ Broker 7.10.3.OPR.1.CR1");
      fieldsObject.addProperty("created", "2001-01-01T00:00:00.000+0000");
      fieldsObject.addProperty("updated", "2000-01-01T00:00:00.000+0000");

      downstreamIssueObject.add("fields", fieldsObject);

      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(createResponseDownstreamIssueObject.toString()));

      mockWebServer.enqueue(new MockResponse()
         .addHeader("Content-Type", "application/json; charset=utf-8")
         .setBody(downstreamIssueObject.toString()));

      issueManager.createIssue("Productize AMQ Broker 7.10.3.OPR.1.CR1", "", "Task", "test", "AMQ 7.10.3.OPR.1.GA", Collections.emptyList());

      RecordedRequest createIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(createIssueRecordedRequest.getBody());
      Assert.assertTrue(createIssueRecordedRequest.getPath().contains("/rest/api/3/issue/"));

      try (InputStreamReader inputStreamReader = new InputStreamReader(createIssueRecordedRequest.getBody().inputStream())) {
         JsonObject issueObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

         JsonObject issuefields = issueObject.getAsJsonObject("fields");
         JsonElement assigneeField = issuefields.get("assignee");
         Assert.assertNotNull(assigneeField);
         Assert.assertTrue(assigneeField.isJsonObject());

         JsonObject assigneeFieldObject = assigneeField.getAsJsonObject();
         Assert.assertEquals("test", assigneeFieldObject.get("accountId").getAsString());
         Assert.assertNull(assigneeFieldObject.get("name"));
      }

      RecordedRequest loadIssueRecordedRequest = mockWebServer.takeRequest();
      Assert.assertNotNull(loadIssueRecordedRequest);

      mockWebServer.shutdown();
   }

   @Test
   public void testLoadIssuesUsesV3SearchJqlWithContinuationToken() throws Exception {
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();

      String upstreamServerBaseURL = "https://issues.apache.org/jira";
      IssueManager upstreamIssueManager = Mockito.spy(new JiraIssueManager(upstreamServerBaseURL, null, "ARTEMIS"));

      RedHatJiraIssueManager issueManager = new RedHatJiraIssueManager(mockWebServer.url("/").toString(), null, "ENTMQBR", JiraIssueManager.REST_API_PATH_V3, new RedHatIssueStateMachine(), upstreamIssueManager);

      JsonObject countResponse = new JsonObject();
      countResponse.addProperty("count", 2);
      mockWebServer.enqueue(new MockResponse().addHeader("Content-Type", "application/json; charset=utf-8").setBody(countResponse.toString()));

      JsonObject page1 = new JsonObject();
      JsonArray issues1 = new JsonArray();
      {
         JsonObject issue = new JsonObject();
         issue.addProperty("key", "ENTMQBR-100");
         JsonObject fields = new JsonObject();

         JsonObject user = new JsonObject();
         user.addProperty("accountId", TEST_USER_NAME);
         fields.add("assignee", user);
         fields.add("creator", user);
         fields.add("reporter", user);

         JsonObject status = new JsonObject();
         status.addProperty("name", "New");
         fields.add("status", status);

         JsonObject issueType = new JsonObject();
         issueType.addProperty("name", "Bug");
         fields.add("issuetype", issueType);

         fields.addProperty("summary", "Issue 100");
         fields.addProperty("created", "2000-01-01T00:00:00.000+0000");
         fields.addProperty("updated", "2000-01-01T00:00:00.000+0000");
         fields.add("components", new JsonArray());
         fields.add("labels", new JsonArray());

         issue.add("fields", fields);
         issues1.add(issue);
      }
      page1.add("issues", issues1);
      page1.addProperty("nextPageToken", "TOKEN-1");
      mockWebServer.enqueue(new MockResponse().addHeader("Content-Type", "application/json; charset=utf-8").setBody(page1.toString()));

      JsonObject page2 = new JsonObject();
      JsonArray issues2 = new JsonArray();
      {
         JsonObject issue = new JsonObject();
         issue.addProperty("key", "ENTMQBR-101");
         JsonObject fields = new JsonObject();

         JsonObject user = new JsonObject();
         user.addProperty("accountId", TEST_USER_NAME);
         fields.add("assignee", user);
         fields.add("creator", user);
         fields.add("reporter", user);

         JsonObject status = new JsonObject();
         status.addProperty("name", "New");
         fields.add("status", status);

         JsonObject issueType = new JsonObject();
         issueType.addProperty("name", "Bug");
         fields.add("issuetype", issueType);

         fields.addProperty("summary", "Issue 101");
         fields.addProperty("created", "2000-01-01T00:00:00.000+0000");
         fields.addProperty("updated", "2000-01-01T00:00:00.000+0000");
         fields.add("components", new JsonArray());
         fields.add("labels", new JsonArray());

         issue.add("fields", fields);
         issues2.add(issue);
      }
      page2.add("issues", issues2);
      mockWebServer.enqueue(new MockResponse().addHeader("Content-Type", "application/json; charset=utf-8").setBody(page2.toString()));

      issueManager.loadIssues();

      Assert.assertNotNull(issueManager.getIssue("ENTMQBR-100"));
      Assert.assertNotNull(issueManager.getIssue("ENTMQBR-101"));

      RecordedRequest countReq = mockWebServer.takeRequest();
      Assert.assertTrue(countReq.getPath().contains("/rest/api/3/search/approximate-count"));

      RecordedRequest pageReq1 = mockWebServer.takeRequest();
      Assert.assertTrue(pageReq1.getBody().readUtf8().contains("\"jql\""));

      RecordedRequest pageReq2 = mockWebServer.takeRequest();
      Assert.assertTrue(pageReq2.getPath().contains("/rest/api/3/search/jql"));
      Assert.assertTrue(pageReq2.getBody().readUtf8().contains("\"nextPageToken\":\"TOKEN-1\""));

      mockWebServer.shutdown();
   }
}
