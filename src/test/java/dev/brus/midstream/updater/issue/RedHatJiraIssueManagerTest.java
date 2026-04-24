package dev.brus.midstream.updater.issue;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Date;

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
   public void testLoadingIssueWithBulkFetch() throws Exception {
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();
      
      try {
         // Response 1: approximate-count endpoint
         String countResponse = "{\"count\": 1}";
         mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(countResponse)
            .addHeader("Content-Type", "application/json"));

         // Response 2: search/jql endpoint (ID-only search)
         String jqlSearchResponse = "{\"issues\": [{\"id\": \"123\"}], \"total\": 1}";
         mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(jqlSearchResponse)
            .addHeader("Content-Type", "application/json"));

         // Response 3: bulkfetch endpoint
         String bulkfetchResponse = "{\"issues\": [{" +
            "\"key\": \"ENTMQBR-123\", " +
            "\"fields\": {" +
            "\"summary\": \"Test Issue\", " +
            "\"status\": {\"name\": \"Open\"}, " +
            "\"assignee\": {\"name\": \"user1\"}, " +
            "\"created\": \"2024-01-01T00:00:00.000-0500\", " +
            "\"updated\": \"2024-01-02T00:00:00.000-0500\", " +
            "\"description\": \"Test description\", " +
            "\"issuetype\": {\"name\": \"Bug\"}, " +
            "\"reporter\": {\"name\": \"reporter1\"}, " +
            "\"labels\": [], " +
            "\"components\": [], " +
            "\"resolution\": null, " +
            "\"creator\": {\"name\": \"creator1\"}" +
            "}}]}";
         mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(bulkfetchResponse)
            .addHeader("Content-Type", "application/json"));


         JiraIssueManager issueManager = new JiraIssueManager(
            mockWebServer.url("rest/api/2").toString(), 
            null, 
            "ENTMQBR", 
            true  // useOptimizedLoading = true
         );

         
         Date lastUpdated = new Date(0);
         issueManager.loadIssues(lastUpdated);

     
         RecordedRequest countRequest = mockWebServer.takeRequest();
         Assert.assertNotNull(countRequest);
         Assert.assertTrue(countRequest.getPath().contains("search/approximate-count"));


         RecordedRequest jqlRequest = mockWebServer.takeRequest();
         Assert.assertNotNull(jqlRequest);
         Assert.assertTrue(jqlRequest.getPath().contains("search/jql"));
         String jqlBody = jqlRequest.getBody().readUtf8();
         Assert.assertTrue("Expected fields:[\"id\"] in search/jql request", 
            jqlBody.contains("\"fields\"") && jqlBody.contains("\"id\""));

  
         RecordedRequest bulkfetchRequest = mockWebServer.takeRequest();
         Assert.assertNotNull(bulkfetchRequest);
         Assert.assertTrue(bulkfetchRequest.getPath().contains("issue/bulkfetch"));
         String bulkfetchBody = bulkfetchRequest.getBody().readUtf8();
         Assert.assertTrue("Expected issueIdsOrKeys in bulkfetch request", 
            bulkfetchBody.contains("issueIdsOrKeys"));

      } finally {
         mockWebServer.shutdown();
      }
   }
}