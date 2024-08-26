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

import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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

   public final static String REST_API_PATH = "/rest/api/2";
   public final static String BROWSE_API_PATH = "/browse";

   private final static String ISSUE_TYPE_BUG = "Bug";

   private static final String ISSUE_RESOLUTION_FIXED = "Fixed";

   private final static String dateFormatPattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

   private final static String queryDateFormatPattern = "yyyy-MM-dd HH:mm";

   private String serverURL;
   private String authString;
   private String projectKey;
   private String issueBaseUrl;

   protected Map<String, Issue> issues;

   protected final SimpleDateFormat defaultDateFormat = new SimpleDateFormat(dateFormatPattern);
   protected final SimpleDateFormat defaultQueryDateFormat = new SimpleDateFormat(queryDateFormatPattern);
   private Gson gson = new GsonBuilder().setDateFormat(dateFormatPattern).setPrettyPrinting().create();

   private Pattern issueKeyPattern;

   public String getServerURL() {
      return serverURL;
   }

   public String getAuthString() {
      return authString;
   }

   public String getProjectKey() {
      return projectKey;
   }

   @Override
   public String getIssueBaseUrl() {
      return issueBaseUrl;
   }

   public JiraIssueManager(String serverURL, String authString, String projectKey) {
      this.serverURL = serverURL;
      this.authString = authString;
      this.projectKey = projectKey;
      this.issues = new HashMap<>();

      this.issueBaseUrl = serverURL + BROWSE_API_PATH;
      this.issueKeyPattern = Pattern.compile(projectKey + "-[0-9]+");
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
   public String getIssueTypeBug() {
      return ISSUE_TYPE_BUG;
   }

   @Override
   public String getIssueResolutionDone() {
      return ISSUE_RESOLUTION_FIXED;
   }

   @Override
   public void loadIssues() throws Exception {
      loadIssues((Date)null);
   }

   private void loadIssues(Date lastUpdated) throws Exception {
      int total = 0;
      final int MAX_RESULTS = 250;

      String lastUpdatedQuery = "";
      if (lastUpdated != null) {
         Calendar calendar = Calendar.getInstance();
         calendar.setTime(lastUpdated);
         calendar.add(Calendar.DATE, -1);
         lastUpdatedQuery = " AND updated >= '" + defaultQueryDateFormat.format(calendar.getTime()) + "'";
      }
      String query = "&jql=" + URLEncoder.encode("project = '" + projectKey + "'" + lastUpdatedQuery, StandardCharsets.UTF_8);

      HttpURLConnection searchConnection = createConnection(REST_API_PATH + "/search?maxResults=0" + query);
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

      logger.info("Loading " + total + " issues with " + taskCount + "tasks");

      for (int i = 0; i < taskCount; i++) {
         final int start = i * MAX_RESULTS;
         final int maxResults = i < taskCount - 1 ? MAX_RESULTS : total - start;

         tasks.add(() -> loadIssues(query, start, maxResults));
      }

      int count = 0;
      long beginTimestamp = System.nanoTime();

      ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
      try {
          List<Future<Integer>> taskFutures = executorService.invokeAll(tasks);
          long endTimestamp = System.nanoTime();

          for (Future<Integer> taskFuture : taskFutures) {
              count += taskFuture.get();
          }

          logger.info("Loaded " + count + "/" + total + " issues in " + (endTimestamp - beginTimestamp) / 1000000 + " milliseconds");
      } finally {
          executorService.shutdownNow();
      }

      int diff = total - count;

      if (diff > 3) {
         throw new IllegalStateException("Error loading " + count + "/" + total + " issues");
      } else if (diff > 0) {
         logger.warn("Error loading " + count + "/" + total + " issues");
      }
   }


   private int loadIssues(String query, int start, int maxResults) throws Exception {
      int result = 0;

      HttpURLConnection connection = createConnection(REST_API_PATH + "/search?fields=*all&maxResults=" + maxResults + "&startAt=" + start + query);
      try {
         try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
            JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

            JsonArray issuesArray = jsonObject.getAsJsonArray("issues");

            int diff = maxResults - issuesArray.size();

            if (diff > 3) {
               throw new IllegalStateException("Error getting from " + start + " - " + issuesArray.size() + "/" + maxResults + " issues");
            } else if (diff > 0) {
               logger.warn("Error getting from " + start + " - " + issuesArray.size() + "/" + maxResults + " issues");
            }

            DateFormat dateFormat = new SimpleDateFormat(dateFormatPattern);
            for (JsonElement issueElement : issuesArray) {
               JsonObject issueObject = issueElement.getAsJsonObject();

               Issue issue = parseIssue(issueObject, dateFormat);

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
   public void loadIssues(File file) throws Exception {
      Date lastUpdated = null;

      Issue[] issuesArray = gson.fromJson(FileUtils.readFileToString(
         file, Charset.defaultCharset()), Issue[].class);

      if (issuesArray != null) {
         for (Issue issue : issuesArray) {
            issues.put(issue.getKey(), issue);

            if (lastUpdated == null || lastUpdated.before(issue.getUpdated())) {
               lastUpdated = issue.getUpdated();
            }
         }
      }

      loadIssues(lastUpdated);
   }

   @Override
   public void storeIssues(File file) throws Exception {
      FileUtils.writeStringToFile(file, gson.toJson(issues.values()), Charset.defaultCharset());
   }

   @Override
   public List<String> parseIssueKeys(String s) {
      List <String> issueKeys = new ArrayList<>();
      Matcher issueKeyMatcher = issueKeyPattern.matcher(s);

      while (issueKeyMatcher.find()) {
         String upstreamIssueKey = issueKeyMatcher.group();
         issueKeys.add(upstreamIssueKey);
      }

      return issueKeys;
   }

   protected Issue parseIssue(JsonObject issueObject, DateFormat dateFormat) throws Exception {
      String issueKey = issueObject.getAsJsonPrimitive("key").getAsString();
      logger.debug("loading issue " + issueKey);

      if (dateFormat == null) {
         dateFormat = defaultDateFormat;
      }

      JsonObject issueFields = issueObject.getAsJsonObject("fields");

      JsonElement issueAssigneeElement = issueFields.get("assignee");
      String issueAssignee = issueAssigneeElement != null && !issueAssigneeElement.isJsonNull() ?
         issueFields.getAsJsonObject("assignee").getAsJsonPrimitive("name").getAsString() : null;
      String issueCreator = issueFields.getAsJsonObject("creator").getAsJsonPrimitive("name").getAsString();
      String issueReporter = issueFields.getAsJsonObject("reporter").getAsJsonPrimitive("name").getAsString();
      String issueStatus = issueFields.getAsJsonObject("status").getAsJsonPrimitive("name").getAsString();
      JsonElement issueResolutionElement = issueFields.get("resolution");
      String issueResolution = issueResolutionElement != null && !issueResolutionElement.isJsonNull() ?
         issueResolutionElement.getAsJsonObject().getAsJsonPrimitive("name").getAsString() : null;
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
         .setResolution(issueResolution)
         .setSummary(issueSummary)
         .setDescription(issueDescription)
         .setCreated(issueCreated)
         .setUpdated(issueUpdated)
         .setUrl(issueBaseUrl + "/" + issueKey)
         .setType(issueType);

      for (String component : parseComponents(issueFields.get("components"))) {
         issue.getComponents().add(component);
      }

      for (String label : parseLabels(issueFields.get("labels"))) {
         issue.getLabels().add(label);
      }

      return issue;
   }

   protected List<String> parseComponents(JsonElement componentsElement) {
      List<String> components = new ArrayList<>();

      if (componentsElement != null && !componentsElement.isJsonNull()) {
         for (JsonElement issueComponentElement : componentsElement.getAsJsonArray()) {
            if (issueComponentElement != null && !issueComponentElement.isJsonNull()) {
               components.add(issueComponentElement.getAsJsonObject().get("name").getAsString());
            }
         }
      }

      return components;
   }

   protected List<String> parseLabels(JsonElement labelsElement) {
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

   protected HttpURLConnection createConnection(String url) throws Exception {
      URL upstreamJIRA = new URL(serverURL + url);
      logger.info("Connecting to " + upstreamJIRA);
      HttpURLConnection connection = (HttpURLConnection)upstreamJIRA.openConnection();
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Accept", "application/json");

      if (authString != null) {
         connection.setRequestProperty("Authorization", authString);
      }

      return connection;
   }
}
