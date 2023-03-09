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

package dev.brus.downstream.updater.issues;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraIssueManager implements IssueManager {
   private final static Logger logger = LoggerFactory.getLogger(JiraIssueManager.class);

   //"id":"customfield_12311240","name":"Target Release"
   private final static String TARGET_RELEASE_FIELD = "customfield_12311240";

   //"id":"customfield_12314640","name":"Upstream Jira"
   private final static String UPSTREAM_ISSUE_FIELD = "customfield_12314640";

   private final static Pattern upstreamIssuePattern = Pattern.compile("ARTEMIS-[0-9]+");
   private final static Pattern securityImpactPattern = Pattern.compile("Impact: (Critical|Important|Moderate|Low)");

   private final static String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

   private String serverURL;
   private String authString;
   private String projectKey;

   private Map<String, Issue> issues;

   private Gson gson = new GsonBuilder().setDateFormat(dateFormat).setPrettyPrinting().create();

   private IssueStateMachine issueStateMachine;

   public IssueStateMachine getIssueStateMachine() {
      return issueStateMachine;
   }

   public JiraIssueManager(String serverURL, String authString, String projectKey, IssueStateMachine issueStateMachine) {
      this.serverURL = serverURL;
      this.authString = authString;
      this.projectKey = projectKey;
      this.issueStateMachine = issueStateMachine;
      this.issues = new HashMap<>();
   }

   @Override
   public Issue getIssue(String key) {
      return issues.get(key);
   }

   @Override
   public Collection<Issue> getIssues() {
      return issues.values();
   }

   @Override
   public Issue createIssue(String summary, String description, String type, String assignee, String upstreamIssue, String targetRelease, List<String> labels) throws Exception {

      JsonObject issueObject = new JsonObject();
      {
         JsonObject fieldsObject = new JsonObject();
         JsonObject projectObject = new JsonObject();
         projectObject.addProperty("key", projectKey);
         fieldsObject.add("project", projectObject);
         JsonObject issueTypeObject = new JsonObject();
         issueTypeObject.addProperty("name", type);
         fieldsObject.add("issuetype", issueTypeObject);
         fieldsObject.addProperty("summary", summary);
         if (description != null) {
            fieldsObject.addProperty("description", description);
         }
         fieldsObject.addProperty(UPSTREAM_ISSUE_FIELD, upstreamIssue);
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

      Issue issue = parseIssue(loadIssue(issueKey), true, createDateFormat());

      issues.put(issue.getKey(), issue);

      return issue;
   }

   private DateFormat createDateFormat() {
      return new SimpleDateFormat(dateFormat, Locale.ENGLISH);
   }

   private String postIssue(JsonObject issueObject) throws Exception {
      HttpURLConnection connection = createConnection("/issue/");
      try {
         connection.setDoOutput(true);
         connection.setRequestMethod("POST");

         try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(connection.getOutputStream())) {
            outputStreamWriter.write(issueObject.toString());
         }

         try {
            try (InputStreamReader inputStreamReader = new InputStreamReader(getConnectionInputStream(connection))) {
               JsonObject responseObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

               String issueKey = responseObject.getAsJsonPrimitive("key").getAsString();

               return issueKey;
            }
         } catch (Exception e) {
            logger.error(new String(connection.getErrorStream().readAllBytes()), e);
            throw e;
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
      List<String> newUpstreamIssuesList = new ArrayList<>();
      for (String label : upstreamIssues) {
         newUpstreamIssuesList.add(label);
      }

      JsonObject issueObject = loadIssue(issueKey);
      JsonObject issueFields = issueObject.getAsJsonObject("fields");
      List<String> upstreamIssuesList = parseUpstreamIssues(issueFields.get(UPSTREAM_ISSUE_FIELD));

      for (String upstreamIssue : upstreamIssuesList) {
         newUpstreamIssuesList.remove(upstreamIssue);
      }

      if (newUpstreamIssuesList.size() > 0) {
         for (String newUpstreamIssue : newUpstreamIssuesList) {
            upstreamIssuesList.add(newUpstreamIssue);
         }

         JsonObject updatingIssueObject = new JsonObject();
         {
            JsonObject updatingFieldsObject = new JsonObject();
            updatingFieldsObject.addProperty(UPSTREAM_ISSUE_FIELD, String.join(", ", upstreamIssuesList));
            updatingIssueObject.add("fields", updatingFieldsObject);
         }

         putIssue(issueKey, updatingIssueObject);
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

      HttpURLConnection connection = createConnection("/issueLink");
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
               logger.error(new String(errorStream.readAllBytes()), e);
               errorStream.close();
            } else {
               logger.error("ConnectionException: ", e);
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

   private void putIssue(String issueKey, JsonObject issueObject) throws Exception {
      HttpURLConnection connection = createConnection("/issue/" + issueKey);
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
         String nextStatus = issueStateMachine.getNextState(status, finalStatus);

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
      HttpURLConnection connection = createConnection("/issue/" + issueKey + "/transitions");
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");

      try (OutputStreamWriter outputStreamWriter= new OutputStreamWriter(connection.getOutputStream())) {
         outputStreamWriter.write("{\"transition\":{\"id\":\"" + transitionId + "\"}}");
      }

      connection.getInputStream().close();
   }

   private JsonObject loadIssue(String issueKey) throws Exception {
      HttpURLConnection connection = createConnection("/issue/" + issueKey);
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
      HttpURLConnection connection = createConnection("/issue/" + issueKey + "/transitions?expand=transitions.fields");
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
   public void loadIssues(boolean parseCustomFields) throws Exception {
      loadIssues(parseCustomFields, (Date)null);
   }

   private void loadIssues(boolean parseCustomFields, Date lastUpdated) throws Exception {
      int total = 0;
      final int MAX_RESULTS = 250;

      String lastUpdatedQuery = "";
      if (lastUpdated != null) {
         SimpleDateFormat queryDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
         lastUpdatedQuery = " AND updated >= '" + queryDateFormat.format(lastUpdated) + "'";
      }
      String jql = URLEncoder.encode("project = '" + projectKey + "'" + lastUpdatedQuery, StandardCharsets.UTF_8);

      HttpURLConnection searchConnection = createConnection("/search?jql=" + jql + "&maxResults=0");
      try {
         try (InputStreamReader inputStreamReader = new InputStreamReader(searchConnection.getInputStream())) {
            JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

            total = jsonObject.getAsJsonPrimitive("total").getAsInt();
         }
      } finally {
         searchConnection.disconnect();
      }

      int taskCount = (int)Math.ceil((double)total / (double)MAX_RESULTS);
      List<Callable<Integer>> tasks = new ArrayList<>();

      for (int i = 0; i < taskCount; i++) {
         final int start = i * MAX_RESULTS;

         tasks.add(() -> loadIssues(parseCustomFields, jql, start, MAX_RESULTS));
      }

      long beginTimestamp = System.nanoTime();
      ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
      List<Future<Integer>> taskFutures = executorService.invokeAll(tasks);
      long endTimestamp = System.nanoTime();

      int count = 0;
      for (Future<Integer> taskFuture : taskFutures) {
         count += taskFuture.get();
      }

      executorService.shutdown();
      logger.info("Loaded " + count + "/" + total + " issues in " + (endTimestamp - beginTimestamp) / 1000000 + " milliseconds");

      if (count != total) {
         throw new IllegalStateException("Error loading " + count + "/" + total + " issues");
      }
   }


   private int loadIssues(boolean parseCustomFields, String jql, int start, int maxResults) throws Exception {
      int result = 0;

      HttpURLConnection connection = createConnection("/search?jql=" + jql + "&fields=*all&maxResults=" + maxResults + "&startAt=" + start);
      try {
         try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
            JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

            JsonArray issuesArray = jsonObject.getAsJsonArray("issues");

            DateFormat dateFormat = createDateFormat();
            for (JsonElement issueElement : issuesArray) {
               JsonObject issueObject = issueElement.getAsJsonObject();

               Issue issue = parseIssue(issueObject, parseCustomFields, dateFormat);

               issues.put(issue.getKey(), issue);

               result++;
            }
         }
      } finally {
         connection.disconnect();
      }

      return result;
   }

   @Override
   public void loadIssues(boolean parseCustomFields, File file) throws Exception {
      Issue[] issuesArray = gson.fromJson(FileUtils.readFileToString(file, Charset.defaultCharset()), Issue[].class);

      Date lastUpdated = new Date(0);
      for (Issue issue : issuesArray) {
         issues.put(issue.getKey(), issue);

         if (lastUpdated.before(issue.getUpdated())) {
            lastUpdated = issue.getUpdated();
         }
      }

      loadIssues(parseCustomFields, lastUpdated);
   }

   @Override
   public void storeIssues(File file) throws Exception {
      FileUtils.writeStringToFile(file, gson.toJson(issues.values()), Charset.defaultCharset());
   }

   private Issue parseIssue(JsonObject issueObject, boolean parseCustomFields, DateFormat dateFormat) throws Exception {
      String issueKey = issueObject.getAsJsonPrimitive("key").getAsString();
      logger.debug("loading issue " + issueKey);

      JsonObject issueFields = issueObject.getAsJsonObject("fields");

      JsonElement issueAssigneeElement = issueFields.get("assignee");
      String issueAssignee = issueAssigneeElement != null && !issueAssigneeElement.isJsonNull() ?
         issueFields.getAsJsonObject("assignee").getAsJsonPrimitive("name").getAsString() : null;
      String issueCreator = issueFields.getAsJsonObject("creator").getAsJsonPrimitive("name").getAsString();
      String issueReporter = issueFields.getAsJsonObject("reporter").getAsJsonPrimitive("name").getAsString();
      String issueStatus = issueFields.getAsJsonObject("status").getAsJsonPrimitive("name").getAsString();
      JsonElement issueDescriptionElement = issueFields.get("description");
      String issueDescription = issueDescriptionElement == null || issueDescriptionElement.isJsonNull() ?
         null : issueDescriptionElement.getAsString();
      String issueType = issueFields.getAsJsonObject("issuetype").getAsJsonPrimitive("name").getAsString();
      String issueSummary = issueFields.getAsJsonPrimitive("summary").getAsString();
      Date issueCreated = dateFormat.parse(issueFields.getAsJsonPrimitive("created").getAsString());
      Date issueUpdated = dateFormat.parse(issueFields.getAsJsonPrimitive("updated").getAsString());

      Issue issue = new Issue()
         .setKey(issueKey)
         .setAssignee(issueAssignee)
         .setCreator(issueCreator)
         .setReporter(issueReporter)
         .setState(issueStatus)
         .setSummary(issueSummary)
         .setDescription(issueDescription)
         .setCreated(issueCreated)
         .setUpdated(issueUpdated)
         .setType(issueType);

      for (String label : parseLabels(issueFields.get("labels"))) {
         issue.getLabels().add(label);
      }

      issue.setCustomerPriority(IssueCustomerPriority.NONE);
      issue.setSecurityImpact(IssueSecurityImpact.NONE);

      if (parseCustomFields) {
         for (String upstreamIssueKey : parseUpstreamIssues(issueFields.get(UPSTREAM_ISSUE_FIELD))) {
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
         if (issueDescription != null && issueDescription.startsWith("Security Tracking Issue")) {
            Matcher securityImpactMatcher = securityImpactPattern.matcher(issueDescription);
            if (securityImpactMatcher.find()) {
               issue.setSecurityImpact(IssueSecurityImpact.fromName(securityImpactMatcher.group(1)));
            }
         }
      }

      return issue;
   }


   private List<String> parseUpstreamIssues(JsonElement upstreamJiraElement) {
      List<String> upstreamIssues = new ArrayList<>();

      if (upstreamJiraElement != null && !upstreamJiraElement.isJsonNull()) {
         String upstreamIssue = upstreamJiraElement.getAsString();
         Matcher upstreamIssueMatcher = upstreamIssuePattern.matcher(upstreamIssue);

         while (upstreamIssueMatcher.find()) {
            String upstreamIssueKey = upstreamIssueMatcher.group();
            upstreamIssues.add(upstreamIssueKey);
         }
      }

      return upstreamIssues;
   }

   private List<String> parseLabels(JsonElement labelsElement) {
      List<String> labels = new ArrayList<>();

      if (labelsElement != null && !labelsElement.isJsonNull()) {
         for (JsonElement issueLabelElement : labelsElement.getAsJsonArray()) {
            if (issueLabelElement != null && !issueLabelElement.isJsonNull()) {
               labels.add(issueLabelElement.getAsString());
            }
         }
      }

      return labels;
   }

   private HttpURLConnection createConnection(String url) throws Exception {
      URL upstreamJIRA = new URL(serverURL + url);
      HttpURLConnection connection = (HttpURLConnection)upstreamJIRA.openConnection();
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Accept", "application/json");

      if (authString != null) {
         connection.setRequestProperty("Authorization", authString);
      }

      return connection;
   }
}
