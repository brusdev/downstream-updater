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
import java.io.IOException;
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
import java.util.function.Consumer;
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

import dev.brus.downstream.updater.issue.Issue;
import dev.brus.downstream.updater.issue.IssueManager;

public class JiraIssueManager implements IssueManager {
   private final static Logger logger = LoggerFactory.getLogger(JiraIssueManager.class);

   public final static String REST_API_PATH_V2 = "/rest/api/2";
   public final static String REST_API_PATH_V3 = "/rest/api/3";
   protected final String apiVersion;
   public final static String BROWSE_API_PATH = "/browse";

   private final static String ISSUE_TYPE_BUG = "Bug";

   private static final String ISSUE_RESOLUTION_FIXED = "Fixed";

   private final static String dateFormatPattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

   private final static String queryDateFormatPattern = "yyyy-MM-dd HH:mm";

   private final String serverURL;
   private final String authString;
   private final String projectKey;
   private final String issueBaseUrl;

   protected final Map<String, Issue> issues;

   protected final SimpleDateFormat defaultDateFormat = new SimpleDateFormat(dateFormatPattern);
   protected final SimpleDateFormat defaultQueryDateFormat = new SimpleDateFormat(queryDateFormatPattern);
   private final Gson gson = new GsonBuilder().setDateFormat(dateFormatPattern).setPrettyPrinting().create();

   private final Pattern issueKeyPattern;

   private static class SearchPagePayload {
      private final JsonArray issuesArray;
      private final String nextPageToken;

      private SearchPagePayload(JsonArray issuesArray, String nextPageToken) {
         this.issuesArray = issuesArray;
         this.nextPageToken = nextPageToken;
      }

      public JsonArray getIssuesArray() {
         return issuesArray;
      }

      public String getNextPageToken() {
         return nextPageToken;
      }
   }

   private static class SearchPageResult {
      private final int loadedCount;
      private final String nextPageToken;

      private SearchPageResult(int loadedCount, String nextPageToken) {
         this.loadedCount = loadedCount;
         this.nextPageToken = nextPageToken;
      }
   }

   public String getServerURL() {
      return serverURL;
   }

   public String getAuthString() {
      return authString;
   }

   public String getProjectKey() {
      return projectKey;
   }

   public String getApiVersion() {
      return apiVersion;
   }

   @Override
   public String getIssueBaseUrl() {
      return issueBaseUrl;
   }

   // Falls back to v2 API if an unsupported version is provided.
   public String getRestApiPath() {
      if (REST_API_PATH_V2.equals(apiVersion) || REST_API_PATH_V3.equals(apiVersion)) {
         return apiVersion;
      } else {
         return REST_API_PATH_V2;
      }
   }

   // Prevents URL from ending with duplicated /rest/api/{2,3}.
   private static String normalizeServerURL(String url) {
      if (url.endsWith(REST_API_PATH_V2 + "/")) {
         return url.substring(0, url.length() - REST_API_PATH_V2.length() + 1);
      } else if (url.endsWith(REST_API_PATH_V3)) {
         return url.substring(0, url.length() - REST_API_PATH_V3.length());
      } else if (url.endsWith(REST_API_PATH_V3 + "/")) {
         return url.substring(0, url.length() - REST_API_PATH_V3.length() + 1);
      } else {
         return url;
      }
   }

   private static String resolveApiVersion(String url, String requested) {
      if (url.endsWith(REST_API_PATH_V2)) {
         return REST_API_PATH_V2;
      } else if (url.endsWith(REST_API_PATH_V3)) {
         return REST_API_PATH_V3;
      } else {
         return requested;
      }
   }

   public JiraIssueManager(String serverURL, String authString, String projectKey) {
      this(serverURL, authString, projectKey, REST_API_PATH_V2);
   }

   public JiraIssueManager(String serverURL, String authString, String projectKey, String apiVersion) {
      String normalizedServerURL = normalizeServerURL(serverURL);
      this.serverURL = normalizedServerURL;
      this.authString = authString;
      this.projectKey = projectKey;
      this.apiVersion = resolveApiVersion(serverURL, apiVersion);
      this.issues = new HashMap<>();

      this.issueBaseUrl = normalizedServerURL + BROWSE_API_PATH;
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
      int total;
      final int MAX_RESULTS = 250;

      String lastUpdatedQuery = "";
      if (lastUpdated != null) {
         Calendar calendar = Calendar.getInstance();
         calendar.setTime(lastUpdated);
         calendar.add(Calendar.DATE, -1);
         lastUpdatedQuery = " AND updated >= '" + defaultQueryDateFormat.format(calendar.getTime()) + "'";
      }
      String jql = "project = '" + projectKey + "'" + lastUpdatedQuery;

      if (REST_API_PATH_V3.equals(getRestApiPath())) {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("jql", jql);

         HttpURLConnection searchConnection = createConnection(getRestApiPath() + "/search/approximate-count", connection -> {
            try {
               connection.setRequestMethod("POST");
               connection.setDoOutput(true);
               byte[] payload = requestBody.toString().getBytes(StandardCharsets.UTF_8);
               connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
               connection.getOutputStream().write(payload);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
         try {
            try (InputStreamReader inputStreamReader = new InputStreamReader(searchConnection.getInputStream())) {
               JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();

               total = jsonObject.getAsJsonPrimitive("count").getAsInt();
            }
         } finally {
            searchConnection.disconnect();
         }
      } else {
         String query = "&jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8);
         HttpURLConnection searchConnection = createConnection(getRestApiPath() + "/search?maxResults=0" + query, null);
         try {
            try (InputStreamReader inputStreamReader = new InputStreamReader(searchConnection.getInputStream())) {
               JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();
               total = jsonObject.getAsJsonPrimitive("total").getAsInt();
            }
         } finally {
            searchConnection.disconnect();
         }
      }

      int count = 0;
      long beginTimestamp = System.nanoTime();

      if (REST_API_PATH_V3.equals(getRestApiPath())) {
         logger.info("Loading " + total + " issues using continuation token pagination");

         String nextPageToken = null;
         do {
            SearchPageResult pageResult = loadIssuesV3Page(jql, MAX_RESULTS, nextPageToken);
            count += pageResult.loadedCount;
            nextPageToken = pageResult.nextPageToken;
         } while (nextPageToken != null);
      } else {
         int taskCount = (int)Math.ceil((double)total / (double)MAX_RESULTS);
         List<Callable<Integer>> tasks = new ArrayList<>();

         logger.info("Loading " + total + " issues with " + taskCount + "tasks");

         String query = "&jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8);
         for (int i = 0; i < taskCount; i++) {
            final int start = i * MAX_RESULTS;
            final int maxResults = i < taskCount - 1 ? MAX_RESULTS : total - start;
            tasks.add(() -> loadIssues(query, start, maxResults));
         }

         ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
         try {
            List<Future<Integer>> taskFutures = executorService.invokeAll(tasks);
            for (Future<Integer> taskFuture : taskFutures) {
               count += taskFuture.get();
            }
         } finally {
            executorService.shutdownNow();
         }
      }

      long endTimestamp = System.nanoTime();
      logger.info("Loaded " + count + "/" + total + " issues in " + (endTimestamp - beginTimestamp) / 1000000 + " milliseconds");

      int diff = total - count;

      if (diff > 3) {
         throw new IllegalStateException("Error loading " + count + "/" + total + " issues");
      } else if (diff > 0) {
         logger.warn("Error loading " + count + "/" + total + " issues");
      }
   }

   private SearchPageResult loadIssuesV3Page(String jql, int maxResults, String nextPageToken) throws Exception {
      SearchPagePayload payload = searchIssuesV3Page(jql, maxResults, nextPageToken);
      int loadedCount = loadIssuesV3SearchResults(payload.getIssuesArray());
      return new SearchPageResult(loadedCount, payload.getNextPageToken());
   }

   private SearchPagePayload searchIssuesV3Page(String jql, int maxResults, String nextPageToken) throws Exception {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("jql", jql);
      requestBody.addProperty("maxResults", maxResults);

      JsonArray fields = new JsonArray();
      fields.add("*all");
      requestBody.add("fields", fields);

      // Handle blank/empty tokens: normalize to null to prevent infinite loops
      if (nextPageToken != null && !nextPageToken.trim().isEmpty()) {
         requestBody.addProperty("nextPageToken", nextPageToken);
      }

      HttpURLConnection connection = createConnection(getRestApiPath() + "/search/jql", configuredConnection -> {
         try {
            configuredConnection.setRequestMethod("POST");
            configuredConnection.setDoOutput(true);
            byte[] payload = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            configuredConnection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            configuredConnection.getOutputStream().write(payload);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });

      try {
         try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
            JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();
            JsonArray issuesArray = jsonObject.getAsJsonArray("issues");

            String returnedNextPageToken = null;
            JsonElement nextPageTokenElement = jsonObject.get("nextPageToken");
            if (nextPageTokenElement != null && !nextPageTokenElement.isJsonNull()) {
               String token = nextPageTokenElement.getAsString();
               // Normalize empty tokens to null
               if (token != null && !token.trim().isEmpty()) {
                  returnedNextPageToken = token;
               }
            }

            return new SearchPagePayload(issuesArray, returnedNextPageToken);
         }
      } finally {
         connection.disconnect();
      }
   }

   private int loadIssuesV3SearchResults(JsonArray issuesArray) throws Exception {
      if (issuesArray == null || issuesArray.size() == 0) {
         return 0;
      }

      int threadCount = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), issuesArray.size()));
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      try {
         List<Callable<Issue>> tasks = new ArrayList<>(issuesArray.size());
      for (JsonElement issueElement : issuesArray) {
         JsonObject issueObject = issueElement.getAsJsonObject();
         tasks.add(() -> parseIssue(issueObject, new SimpleDateFormat(dateFormatPattern)));
      }

      int loaded = 0;
         List<Future<Issue>> futures = executor.invokeAll(tasks);
      for (Future<Issue> future : futures) {
         Issue issue = future.get();
         issues.put(issue.getKey(), issue);
         loaded++;
      }
      return loaded;
      } finally {
         executor.shutdownNow();
      }
   }

   private int loadIssues(String query, int start, int maxResults) throws Exception {
      int result = 0;

      HttpURLConnection connection = createConnection(getRestApiPath() + "/search?fields=*all&maxResults=" + maxResults + "&startAt=" + start + query, null);
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

   private String parseUserId(JsonObject user) {
      if (user == null || user.isJsonNull()) {
         return null;
      }
      if (user.has("accountId") && !user.get("accountId").isJsonNull()) {
         return user.get("accountId").getAsString();
      } else if (user.has("name") && !user.get("name").isJsonNull()) {
         return user.get("name").getAsString();
      } else if (user.has("displayName") && !user.get("displayName").isJsonNull()) {
         return user.get("displayName").getAsString();
      } else {
         return null;
      }
   }

   private String parseIssueDescription(JsonElement descriptionElement) {
      if (descriptionElement == null || descriptionElement.isJsonNull()) {
         return null;
      } else if (descriptionElement.isJsonPrimitive()) {
         return descriptionElement.getAsString();
      } else {
         // Jira Cloud can return Atlassian Document Format objects for description.
         return descriptionElement.toString();
      }
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
         parseUserId(issueFields.getAsJsonObject("assignee")) : null;
      String issueCreator = parseUserId(issueFields.getAsJsonObject("creator"));
      String issueReporter = parseUserId(issueFields.getAsJsonObject("reporter"));
      String issueStatus = issueFields.getAsJsonObject("status").getAsJsonPrimitive("name").getAsString();
      JsonElement issueResolutionElement = issueFields.get("resolution");
      String issueResolution = issueResolutionElement != null && !issueResolutionElement.isJsonNull() ?
         issueResolutionElement.getAsJsonObject().getAsJsonPrimitive("name").getAsString() : null;
      JsonElement issueDescriptionElement = issueFields.get("description");
      String issueDescription = parseIssueDescription(issueDescriptionElement);
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

   protected HttpURLConnection createConnection(String url, Consumer<HttpURLConnection> connectionConsumer) throws Exception {
      URL upstreamJIRA = new URL(serverURL + url);

      for (int i = 0; i < 9; i++) {
         logger.info("Connecting to " + upstreamJIRA);
         HttpURLConnection connection = (HttpURLConnection)upstreamJIRA.openConnection();
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setRequestProperty("Accept", "application/json");

         if (authString != null) {
            connection.setRequestProperty("Authorization", authString);
         }

         if (connectionConsumer != null) {
            connectionConsumer.accept(connection);
         }

         if (connection.getResponseCode() == 429) {
            logger.debug("Rate limit reached, sleeping before retrying");
            Thread.sleep((long)(3000 * Math.random()));
         } else {
            return connection;
         }
      }

      throw new IOException("Failed to create a connection to " + upstreamJIRA + ". Maximum retries reached.");
   }
}
