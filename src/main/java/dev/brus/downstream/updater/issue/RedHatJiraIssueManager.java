/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.brus.downstream.updater.issue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedHatJiraIssueManager extends JiraIssueManager implements DownstreamIssueManager {
   private final static Logger logger = LoggerFactory.getLogger(RedHatJiraIssueManager.class);

   //"id":"customfield_12311240","name":"Target Release"
   private final static String TARGET_RELEASE_FIELD = "customfield_12311240";

   //"id":"customfield_12314640","name":"Upstream Jira"
   private final static String UPSTREAM_JIRA_FIELD = "customfield_12314640";

   private final static String ISSUE_TYPE_BUG = "Bug";

   private static final String ISSUE_STATE_DONE = "Done";
   private static final String ISSUE_STATE_DEV_COMPLETE = "Dev Complete";

   private static final String ISSUE_LABEL_NO_BACKPORT_NEEDED = "NO-BACKPORT-NEEDED";
   private static final String ISSUE_LABEL_NO_TESTING_NEEDED = "no-testing-needed";
   private static final String ISSUE_LABEL_UPSTREAM_TEST_COVERAGE = "upstream-test-coverage";

   private final static Pattern securityImpactPattern = Pattern.compile("Impact: (Critical|Important|Moderate|Low)");

   private final static String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

   private Gson gson = new GsonBuilder().setDateFormat(dateFormat).setPrettyPrinting().create();

   private IssueStateMachine issueStateMachine;

   private IssueManager upstreamIssueManager;

   public IssueStateMachine getIssueStateMachine() {
      return issueStateMachine;
   }

   public RedHatJiraIssueManager(String serverURL, String authString, String projectKey, IssueStateMachine issueStateMachine, IssueManager upstreamIssueManager) {
      super(serverURL, authString, projectKey);

      this.issueStateMachine = issueStateMachine;
      this.upstreamIssueManager = upstreamIssueManager;
   }

   @Override
   public String getIssueTypeBug() {
      return ISSUE_TYPE_BUG;
   }

   @Override
   public String getIssueStateDone() {
      return ISSUE_STATE_DONE;
   }

   @Override
   public String getIssueStateDevComplete() {
      return ISSUE_STATE_DEV_COMPLETE;
   }

   @Override
   public String getIssueLabelNoBackportNeeded() {
      return ISSUE_LABEL_NO_BACKPORT_NEEDED;
   }

   @Override
   public String getIssueLabelNoTestingNeeded() {
      return ISSUE_LABEL_NO_TESTING_NEEDED;
   }

   @Override
   public String getIssueLabelUpstreamTestCoverage() {
      return ISSUE_LABEL_UPSTREAM_TEST_COVERAGE;
   }

   @Override
   public Issue createIssue(String summary, String description, String type, String assignee, String targetRelease, List<String> labels) throws Exception {

      JsonObject issueObject = new JsonObject();
      {
         JsonObject fieldsObject = new JsonObject();
         JsonObject projectObject = new JsonObject();
         projectObject.addProperty("key", getProjectKey());
         fieldsObject.add("project", projectObject);
         JsonObject issueTypeObject = new JsonObject();
         issueTypeObject.addProperty("name", type);
         fieldsObject.add("issuetype", issueTypeObject);
         fieldsObject.addProperty("summary", summary);
         if (description != null) {
            fieldsObject.addProperty("description", description);
         }
         //UPSTREAM_ISSUE_FIELD was removed from the create screen
         //fieldsObject.addProperty(UPSTREAM_ISSUE_FIELD, upstreamIssue);
         JsonObject targetReleaseObject = new JsonObject();
         targetReleaseObject.addProperty("name", targetRelease);
         fieldsObject.add(TARGET_RELEASE_FIELD, targetReleaseObject);
         JsonObject assigneeObject = new JsonObject();
         assigneeObject.addProperty("name", assignee);
         fieldsObject.add("assignee", assigneeObject);
         JsonArray labelsArray = new JsonArray();
         for (String label : labels) {
            labelsArray.add(label);
         }
         fieldsObject.add("labels", labelsArray);
         issueObject.add("fields", fieldsObject);
      }

      String issueKey = postIssue(issueObject);

      Issue issue = parseIssue(loadIssue(issueKey), defaultDateFormat);

      issues.put(issue.getKey(), issue);

      return issue;
   }

   private String postIssue(JsonObject issueObject) throws Exception {
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/");
      try {
         connection.setDoOutput(true);
         connection.setRequestMethod("POST");

         try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(connection.getOutputStream())) {
            outputStreamWriter.write(issueObject.toString());
         }

         try (InputStreamReader inputStreamReader = new InputStreamReader(getConnectionInputStream(connection))) {
            JsonObject responseObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

            String issueKey = responseObject.getAsJsonPrimitive("key").getAsString();

            return issueKey;
         }
      } finally {
         connection.disconnect();
      }
   }

   @Override
   public void addIssueLabels(String issueKey, String... labels) throws Exception {
      List<String> newLabelsList = new ArrayList<>();
      for (String label : labels) {
         newLabelsList.add(label);
      }

      JsonObject issueObject = loadIssue(issueKey);
      JsonObject issueFields = issueObject.getAsJsonObject("fields");
      List<String> labelsList = parseLabels(issueFields.get("labels"));
      for (String label : labelsList) {
         newLabelsList.remove(label);
      }

      if (newLabelsList.size() > 0) {
         for (String newLabel : newLabelsList) {
            labelsList.add(newLabel);
         }

         JsonObject updatingIssueObject = new JsonObject();
         {
            JsonObject updatingFieldsObject = new JsonObject();
            JsonArray updatingLabelsArray = new JsonArray();
            for (String label : labelsList) {
               updatingLabelsArray.add(label);
            }
            updatingFieldsObject.add("labels", updatingLabelsArray);
            updatingIssueObject.add("fields", updatingFieldsObject);
         }

         putIssue(issueKey, updatingIssueObject);
      }
   }

   @Override
   public void addIssueUpstreamIssues(String issueKey, String... upstreamIssues) throws Exception {
      StringBuilder newUpstreamJiraFieldValue = new StringBuilder();
      List<String> newUpstreamIssues = new ArrayList<>();
      for (String label : upstreamIssues) {
         newUpstreamIssues.add(label);
      }

      JsonObject issueObject = loadIssue(issueKey);
      JsonObject issueFields = issueObject.getAsJsonObject("fields");
      JsonElement currentUpstreamJiraField = issueFields.get(UPSTREAM_JIRA_FIELD);
      if (currentUpstreamJiraField != null && !currentUpstreamJiraField.isJsonNull()) {
         String currentUpstreamJiraFieldValue = currentUpstreamJiraField.getAsString();

         String[] currentUpstreamIssues = currentUpstreamJiraFieldValue.split("[, ]+");

         for (String currentUpstreamIssue: currentUpstreamIssues) {
            List<String> upstreamIssueKeys = upstreamIssueManager.parseIssueKeys(currentUpstreamIssue);

            if (upstreamIssueKeys.size() > 1) {
               throw new IllegalStateException("Upstream issues without separator: " + currentUpstreamIssue);
            }

            if (upstreamIssueKeys.size() > 0) {
               String upstreamIssueKey = upstreamIssueKeys.get(0);
               newUpstreamIssues.remove(upstreamIssueKey);
               newUpstreamJiraFieldValue.append(upstreamIssueManager.getIssue(upstreamIssueKey).getUrl());
               newUpstreamJiraFieldValue.append(", ");
            }
         }
      }

      if (newUpstreamIssues.size() > 0) {
         for (String newUpstreamIssueKey : newUpstreamIssues) {
            newUpstreamJiraFieldValue.append(upstreamIssueManager.getIssue(newUpstreamIssueKey).getUrl());
            newUpstreamJiraFieldValue.append(", ");
         }
         newUpstreamJiraFieldValue.delete(newUpstreamJiraFieldValue.length() - 2, newUpstreamJiraFieldValue.length());

         JsonObject updatingIssueObject = new JsonObject();
         {
            JsonObject updatingFieldsObject = new JsonObject();
            updatingFieldsObject.addProperty(UPSTREAM_JIRA_FIELD, newUpstreamJiraFieldValue.toString());
            updatingIssueObject.add("fields", updatingFieldsObject);
         }

         putIssue(issueKey, updatingIssueObject, 3);
      }
   }

   @Override
   public void copyIssueUpstreamIssues(String fromIssueKey, String toIssueKey) throws Exception {
      JsonObject issueObject = loadIssue(fromIssueKey);
      JsonObject issueFields = issueObject.getAsJsonObject("fields");
      JsonElement upstreamJiraField = issueFields.get(UPSTREAM_JIRA_FIELD);
      String upstreamJiraFieldValue = "";
      if (upstreamJiraField != null && !upstreamJiraField.isJsonNull()) {
         upstreamJiraFieldValue = upstreamJiraField.getAsString();
      }

      JsonObject updatingIssueObject = new JsonObject();
      {
         JsonObject updatingFieldsObject = new JsonObject();
         updatingFieldsObject.addProperty(UPSTREAM_JIRA_FIELD, upstreamJiraFieldValue);
         updatingIssueObject.add("fields", updatingFieldsObject);
      }

      putIssue(toIssueKey, updatingIssueObject);
   }

   @Override
   public void linkIssue(String issueKey, String cloningIssueKey, String linkType) throws Exception {

      JsonObject issueLinkObject = new JsonObject();
      JsonObject issueLinkTypeObject = new JsonObject();
      issueLinkTypeObject.addProperty("name", linkType);
      issueLinkObject.add("type", issueLinkTypeObject);
      JsonObject inwardIssue = new JsonObject();
      inwardIssue.addProperty("key", issueKey);
      issueLinkObject.add("inwardIssue", inwardIssue);
      JsonObject outwardIssue = new JsonObject();
      outwardIssue.addProperty("key", cloningIssueKey);
      issueLinkObject.add("outwardIssue", outwardIssue);

      HttpURLConnection connection = createConnection(REST_API_PATH + "/issueLink");
      try {
         connection.setDoOutput(true);
         connection.setRequestMethod("POST");

         try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(connection.getOutputStream())) {
            outputStreamWriter.write(issueLinkObject.toString());
         }

         try (InputStream inputStream = getConnectionInputStream(connection)) {
            logger.debug("linkIssueResponse: " + new String(inputStream.readAllBytes()));
         }
      } finally {
         connection.disconnect();
      }
   }

   private InputStream getConnectionInputStream(HttpURLConnection connection) throws IOException {
      try {
         return connection.getInputStream();
      } catch (Exception e) {
         try (InputStream errorStream = connection.getErrorStream()) {
            if (errorStream != null) {
               String responseError = new String(errorStream.readAllBytes());
               errorStream.close();
               throw new IOException("Server returned HTTP response error: " + responseError, e);
            }
         }
         throw e;
      }
   }

   @Override
   public void setIssueTargetRelease(String issueKey, String targetRelease) throws Exception {

      JsonObject updatingIssueObject = new JsonObject();
      {
         JsonObject updatingFieldsObject = new JsonObject();
         JsonObject targetReleaseObject = new JsonObject();
         targetReleaseObject.addProperty("name", targetRelease);
         updatingFieldsObject.add("customfield_12311240", targetReleaseObject);
         updatingIssueObject.add("fields", updatingFieldsObject);
      }

      putIssue(issueKey, updatingIssueObject);
   }

   private void putIssue(String issueKey, JsonObject issueObject, int retries) throws Exception {
      while (true) {
         try {
            retries--;
            putIssue(issueKey, issueObject);
            break;
         } catch (Exception e) {
            logger.debug("Failed to put issue " + issueKey + ": " + e);
            if (retries == 0) {
               throw new IOException("Failed to put issue " + issueKey + ". Maximum retries reached.", e);
            }
         }
      }
   }

   private void putIssue(String issueKey, JsonObject issueObject) throws Exception {
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/" + issueKey);
      try {
         connection.setDoOutput(true);
         connection.setRequestMethod("PUT");

         try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(connection.getOutputStream())) {
            outputStreamWriter.write(issueObject.toString());
         }

         try (InputStream inputStream = getConnectionInputStream(connection)) {
            logger.debug("putIssueResponse: " + new String(inputStream.readAllBytes()));
         }
      } finally {
         connection.disconnect();
      }
   }

   @Override
   public void transitionIssue(String issueKey, String finalStatus) throws Exception {
      String status = getIssueStatus(issueKey);

      while (!status.equals(finalStatus)) {
         String nextStatus = getIssueStateMachine().getNextState(status, finalStatus);

         for (IssueTransaction transaction : getIssueTransactions(issueKey)) {
            if (transaction.getFinalStatus().equals(nextStatus)) {
               transitionIssue(issueKey, transaction.getId());
               status = transaction.getFinalStatus();
               break;
            }
         }
      }
   }

   public void transitionIssue(String issueKey, int transitionId) throws Exception {
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/" + issueKey + "/transitions");
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");

      try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(connection.getOutputStream())) {
         outputStreamWriter.write("{\"transition\":{\"id\":\"" + transitionId + "\"}}");
      }

      connection.getInputStream().close();
   }

   private JsonObject loadIssue(String issueKey) throws Exception {
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/" + issueKey);
      try {
         try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
            return JsonParser.parseReader(inputStreamReader).getAsJsonObject();
         }
      } finally {
         connection.disconnect();
      }
   }

   public String getIssueStatus(String issueKey) throws Exception {
      JsonObject issueObject = loadIssue(issueKey);

      JsonObject issueFields = issueObject.getAsJsonObject("fields");

      return issueFields.getAsJsonObject("status").getAsJsonPrimitive("name").getAsString();
   }

   public IssueTransaction[] getIssueTransactions(String issueKey) throws Exception {
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/" + issueKey + "/transitions?expand=transitions.fields");
      try {
         List<IssueTransaction> issueTransactions = new ArrayList<>();

         try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
            JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

            JsonArray transitionsArray = jsonObject.getAsJsonArray("transitions");

            for (JsonElement transitionElement : transitionsArray) {
               JsonObject transitionObject = transitionElement.getAsJsonObject();

               int transitionId = transitionObject.getAsJsonPrimitive("id").getAsInt();
               String transitionFinalStatus = transitionObject.getAsJsonObject("to")
                  .getAsJsonPrimitive("name").getAsString();

               issueTransactions.add(new IssueTransaction()
                  .setId(transitionId)
                  .setFinalStatus(transitionFinalStatus));
            }
         }

         return issueTransactions.toArray(IssueTransaction[]::new);
      } finally {
         connection.disconnect();
      }
   }

   @Override
   protected Issue parseIssue(JsonObject issueObject, DateFormat dateFormat) throws Exception {
      Issue issue = super.parseIssue(issueObject, dateFormat);

      JsonObject issueFields = issueObject.getAsJsonObject("fields");

      for (String upstreamIssueKey : parseUpstreamIssues(issueFields.get(UPSTREAM_JIRA_FIELD))) {
         logger.debug("linking issue " + upstreamIssueKey);
         issue.getIssues().add(upstreamIssueKey);
      }

      JsonElement targetReleaseElement = issueFields.get(TARGET_RELEASE_FIELD);
      if (targetReleaseElement != null && !targetReleaseElement.isJsonNull()) {
         issue.setTargetRelease(targetReleaseElement.getAsJsonObject().get("name").getAsString());
      }

      JsonElement linksElement = issueFields.get("issuelinks");
      issue.setPatch(linksElement != null && !linksElement.isJsonNull() && linksElement.toString().matches(".*PATCH-[0-9]+.*"));

      //"id":"customfield_12312340","name":"GSS Priority"
      //"id":"customfield_12310120","name":"Help Desk Ticket Reference"
      //"id":"customfield_12310021","name":"Support Case Reference"
      JsonElement gssPriorityElement = issueFields.get("customfield_12312340");
      JsonElement helpDeskTicketReferenceElement = issueFields.get("customfield_12310120");
      JsonElement supportCaseReferenceElement = issueFields.get("customfield_12310021");
      issue.setCustomer(issue.isPatch() || (gssPriorityElement != null && !gssPriorityElement.isJsonNull()) ||
         (helpDeskTicketReferenceElement != null && !helpDeskTicketReferenceElement.isJsonNull()) ||
         (supportCaseReferenceElement != null && !supportCaseReferenceElement.isJsonNull()));
      issue.setCustomerPriority(gssPriorityElement != null && !gssPriorityElement.isJsonNull() ? IssueCustomerPriority.fromName(
         gssPriorityElement.getAsJsonObject().get("value").getAsString()) : IssueCustomerPriority.NONE);

      //"id":"customfield_12311640","name":"Security Sensitive Issue"
      JsonElement securitySensitiveIssueElement = issueFields.get("customfield_12311640");
      issue.setSecurity(securitySensitiveIssueElement != null && !securitySensitiveIssueElement.isJsonNull());
      if (issue.getDescription() != null && issue.getDescription().startsWith("Security Tracking Issue")) {
         Matcher securityImpactMatcher = securityImpactPattern.matcher(issue.getDescription());
         if (securityImpactMatcher.find()) {
            issue.setSecurityImpact(IssueSecurityImpact.fromName(securityImpactMatcher.group(1)));
         }
      }

      return issue;
   }


   private List<String> parseUpstreamIssues(JsonElement upstreamJiraElement) {
      if (upstreamJiraElement != null && !upstreamJiraElement.isJsonNull()) {
         String upstreamJira = upstreamJiraElement.getAsString();
         return upstreamIssueManager.parseIssueKeys(upstreamJira);
      }

      return new ArrayList<>();
   }
}
