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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedHatJiraIssueManager extends JiraIssueManager implements DownstreamIssueManager {
   private final static Logger logger = LoggerFactory.getLogger(RedHatJiraIssueManager.class);

   // Red Hat issue fields
   public final static String FIELD_TARGET_RELEASE = "Target Release";
   public final static String FIELD_UPSTREAM_JIRA = "Upstream Jira";
   public final static String FIELD_GSS_PRIORITY = "GSS Priority";
   public final static String FIELD_HELP_DESK_TICKET_REFERENCE = "Help Desk Ticket Reference";
   public final static String FIELD_SUPPORT_CASE_REFERENCE = "Support Case Reference";
   public final static String FIELD_SFDC_CASES_LINKS = "SFDC Cases Links";
   public final static String FIELD_SFDC_CASES_COUNTER = "SFDC Cases Counter";
   public final static String FIELD_SEVERITY = "Severity";

   public final static Set<String> FIELDS = Set.of(
      FIELD_TARGET_RELEASE,
      FIELD_UPSTREAM_JIRA,
      FIELD_GSS_PRIORITY,
      FIELD_HELP_DESK_TICKET_REFERENCE,
      FIELD_SUPPORT_CASE_REFERENCE,
      FIELD_SFDC_CASES_LINKS,
      FIELD_SFDC_CASES_COUNTER,
      FIELD_SEVERITY
   );


   private final static String ISSUE_TYPE_BUG = "Bug";
   private final static String ISSUE_TYPE_VULNERABILITY = "Vulnerability";

   private static final String ISSUE_RESOLUTION_DONE = "Done";
   private static final String ISSUE_RESOLUTION_DUPLICATE = "Duplicate";

   private static final String ISSUE_LABEL_NO_BACKPORT_NEEDED = "NO-BACKPORT-NEEDED";
   private static final String ISSUE_LABEL_NO_TESTING_NEEDED = "no-testing-needed";
   private static final String ISSUE_LABEL_UPSTREAM_TEST_COVERAGE = "upstream-test-coverage";

   private final static Pattern securityImpactPattern = Pattern.compile("Impact: (Critical|Important|Moderate|Low)");

   private DownstreamIssueStateMachine issueStateMachine;

   private IssueManager upstreamIssueManager;

   private Map<String, String> fields;

   public DownstreamIssueStateMachine getIssueStateMachine() {
      return issueStateMachine;
   }

   public RedHatJiraIssueManager(String serverURL, String authString, String projectKey, DownstreamIssueStateMachine issueStateMachine, IssueManager upstreamIssueManager) {
      super(serverURL, authString, projectKey, true);

      this.issueStateMachine = issueStateMachine;
      this.upstreamIssueManager = upstreamIssueManager;
   }

   @Override
   public void load() throws Exception {
      loadFields();
   }

   private void loadFields() throws Exception {
      Map<String, String> loadedFields = new ConcurrentHashMap<>();
      HttpURLConnection connection = createConnection(REST_API_PATH + "/field", null);
      try {
         try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
            JsonArray fieldsArray = JsonParser.parseReader(inputStreamReader).getAsJsonArray();

            for (JsonElement fieldElement : fieldsArray) {
               JsonObject fieldObject = fieldElement.getAsJsonObject();
               String fieldName = fieldObject.getAsJsonPrimitive("name").getAsString();
               if (FIELDS.contains(fieldName)) {
                  String fieldId = fieldObject.getAsJsonPrimitive("id").getAsString();
                  loadedFields.put(fieldName, fieldId);
               }
            }
         }

         FIELDS.forEach(fieldName -> Objects.requireNonNull(
            loadedFields.get(fieldName), "Field " + fieldName + " not loaded"));

         fields = loadedFields;
      } finally {
         connection.disconnect();
      }
   }

   public String getFieldIdByName(String name) throws Exception {
      return fields.get(name);
   }

   @Override
   protected JsonArray buildRequiredIssueFields() throws Exception {
      JsonArray requiredIssueFields = super.buildRequiredIssueFields();

      fields.forEach((key, value) -> requiredIssueFields.add(value));

      return requiredIssueFields;
   }

   @Override
   public String getIssueTypeBug() {
      return ISSUE_TYPE_BUG;
   }

   @Override
   public String getIssueResolutionDone() {
      return ISSUE_RESOLUTION_DONE;
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
         fieldsObject.add(getFieldIdByName(FIELD_TARGET_RELEASE), targetReleaseObject);
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
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/", httpConnection -> {
         try {
            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod("POST");

            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpConnection.getOutputStream())) {
               outputStreamWriter.write(issueObject.toString());
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });

      try {
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

      Issue issue = issues.get(issueKey);
      if (issue != null) {
         for (String label : labels) {
            if (!issue.getLabels().contains(label)) {
               issue.getLabels().add(label);
            }
         }
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
      JsonElement currentUpstreamJiraField = issueFields.get(getFieldIdByName(FIELD_UPSTREAM_JIRA));
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
            updatingFieldsObject.addProperty(getFieldIdByName(FIELD_UPSTREAM_JIRA), newUpstreamJiraFieldValue.toString());
            updatingIssueObject.add("fields", updatingFieldsObject);
         }

         putIssue(issueKey, updatingIssueObject, 3);
      }

      Issue issue = issues.get(issueKey);
      if (issue != null) {
         for (String upstreamIssue : upstreamIssues) {
            if (!issue.getIssues().contains(upstreamIssue)) {
               issue.getIssues().add(upstreamIssue);
            }
         }
      }
   }

   @Override
   public void copyIssueUpstreamIssues(String fromIssueKey, String toIssueKey) throws Exception {
      JsonObject issueObject = loadIssue(fromIssueKey);
      JsonObject issueFields = issueObject.getAsJsonObject("fields");
      JsonElement upstreamJiraField = issueFields.get(getFieldIdByName(FIELD_UPSTREAM_JIRA));
      String upstreamJiraFieldValue = "";
      if (upstreamJiraField != null && !upstreamJiraField.isJsonNull()) {
         upstreamJiraFieldValue = upstreamJiraField.getAsString();
      }

      JsonObject updatingIssueObject = new JsonObject();
      {
         JsonObject updatingFieldsObject = new JsonObject();
         updatingFieldsObject.addProperty(getFieldIdByName(FIELD_UPSTREAM_JIRA), upstreamJiraFieldValue);
         updatingIssueObject.add("fields", updatingFieldsObject);
      }

      putIssue(toIssueKey, updatingIssueObject);

      Issue fromIssue = issues.get(fromIssueKey);
      Issue toIssue = issues.get(toIssueKey);
      if (fromIssue != null && toIssue != null) {
         toIssue.getIssues().clear();
         toIssue.getIssues().addAll(fromIssue.getIssues());
      }
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

      HttpURLConnection connection = createConnection(REST_API_PATH + "/issueLink", httpConnection -> {
         try {
            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod("POST");

            try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(httpConnection.getOutputStream())) {
               outputStreamWriter.write(issueLinkObject.toString());
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });
      try {
         try (InputStream inputStream = getConnectionInputStream(connection)) {
            logger.debug("linkIssueResponse: " + new String(inputStream.readAllBytes()));
         }
      } finally {
         connection.disconnect();
      }
   }

   @Override
   public boolean isDuplicateIssue(String issueKey) {
      return ISSUE_RESOLUTION_DUPLICATE.equals(issues.get(issueKey).getResolution());
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
         updatingFieldsObject.add(getFieldIdByName(FIELD_TARGET_RELEASE), targetReleaseObject);
         updatingIssueObject.add("fields", updatingFieldsObject);
      }

      putIssue(issueKey, updatingIssueObject);

      Issue issue = issues.get(issueKey);
      if (issue != null) {
         issue.setTargetRelease(targetRelease);
      }
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
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/" + issueKey, httpConnection -> {
         try {
            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod("PUT");

            try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(httpConnection.getOutputStream())) {
               outputStreamWriter.write(issueObject.toString());
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });
      try {
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

      Issue issue = issues.get(issueKey);
      if (issue != null) {
         issue.setState(finalStatus);
      }
   }

   public void transitionIssue(String issueKey, int transitionId) throws Exception {
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/" + issueKey + "/transitions", httpConnection -> {
         try {
            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod("POST");

            try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(httpConnection.getOutputStream())) {
               outputStreamWriter.write("{\"transition\":{\"id\":\"" + transitionId + "\"}}");
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });

      connection.getInputStream().close();
   }

   private JsonObject loadIssue(String issueKey) throws Exception {
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/" + issueKey, null);
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
      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/" + issueKey + "/transitions?expand=transitions.fields", null);
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
   protected String parseUserId(JsonObject userObject) {
      if (userObject == null || userObject.isJsonNull()) {
         return null;
      }
      if (userObject.has("accountId") && !userObject.get("accountId").isJsonNull()) {
         return userObject.getAsJsonPrimitive("accountId").getAsString();
      }
      return null;
   }

   @Override
   protected Issue parseIssue(JsonObject issueObject, DateFormat dateFormat) throws Exception {
      Issue issue = super.parseIssue(issueObject, dateFormat);

      JsonObject issueFields = issueObject.getAsJsonObject("fields");

      for (String upstreamIssueKey : parseUpstreamIssues(issueFields.get(getFieldIdByName(FIELD_UPSTREAM_JIRA)))) {
         logger.debug("linking issue " + upstreamIssueKey);
         issue.getIssues().add(upstreamIssueKey);
      }

      JsonElement targetReleaseElement = issueFields.get(getFieldIdByName(FIELD_TARGET_RELEASE));
      if (targetReleaseElement != null && !targetReleaseElement.isJsonNull()) {
         issue.setTargetRelease(targetReleaseElement.getAsJsonObject().get("name").getAsString());
      }

      JsonElement linksElement = issueFields.get("issuelinks");
      issue.setPatch(linksElement != null && !linksElement.isJsonNull() && linksElement.toString().matches(".*PATCH-[0-9]+.*"));

      JsonElement gssPriorityElement = issueFields.get(getFieldIdByName(FIELD_GSS_PRIORITY));
      JsonElement helpDeskTicketReferenceElement = issueFields.get(getFieldIdByName(FIELD_HELP_DESK_TICKET_REFERENCE));
      JsonElement supportCaseReferenceElement = issueFields.get(getFieldIdByName(FIELD_SUPPORT_CASE_REFERENCE));
      JsonElement sfdcCasesLinksElement = issueFields.get(getFieldIdByName(FIELD_SFDC_CASES_LINKS));
      JsonElement sfdcCasesCounterElement = issueFields.get(getFieldIdByName(FIELD_SFDC_CASES_COUNTER));
      issue.setCustomer(issue.isPatch() || (gssPriorityElement != null && !gssPriorityElement.isJsonNull()) ||
         (helpDeskTicketReferenceElement != null && !helpDeskTicketReferenceElement.isJsonNull()) ||
         (supportCaseReferenceElement != null && !supportCaseReferenceElement.isJsonNull()) ||
         (sfdcCasesLinksElement != null && !sfdcCasesLinksElement.isJsonNull()) ||
         (sfdcCasesCounterElement != null && !sfdcCasesCounterElement.isJsonNull()));
      if (gssPriorityElement != null && !gssPriorityElement.isJsonNull()) {
         if (gssPriorityElement.isJsonObject()) {
            issue.setCustomerPriority(IssueCustomerPriority.fromName(
               gssPriorityElement.getAsJsonObject().get("value").getAsString()));
         } else {
            issue.setCustomerPriority(IssueCustomerPriority.fromValue(
               gssPriorityElement.getAsString()));
         }
      }


      if (ISSUE_TYPE_BUG.equals(issue.getType()) && issue.getDescription() != null && issue.getDescription().startsWith("Security Tracking Issue")) {
         issue.setSecurity(true);

         Matcher securityImpactMatcher = securityImpactPattern.matcher(issue.getDescription());
         if (securityImpactMatcher.find()) {
            issue.setSecurityImpact(IssueSecurityImpact.fromName(securityImpactMatcher.group(1)));
         }
      } else if (ISSUE_TYPE_VULNERABILITY.equals(issue.getType())) {
         JsonElement severityElement = issueFields.get(getFieldIdByName(FIELD_SEVERITY));
         issue.setSecurityImpact(IssueSecurityImpact.fromName(
            severityElement != null && !severityElement.isJsonNull() ?
            severityElement.getAsJsonObject().get("value").getAsString() : null));
      }

      issue.setDocumentation(issue.getSummary().startsWith("[Docs]") ||
         issue.getComponents().contains("documentation") ||
         issue.getLabels().contains("documentation"));

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
