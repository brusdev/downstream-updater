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
import java.io.OutputStream;
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
import java.util.stream.Collectors;

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

   private final boolean useOptimizedLoading;

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

      public int getLoadedCount() {
         return loadedCount;
      }

      public String getNextPageToken() {
         return nextPageToken;
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

   @Override
   public String getIssueBaseUrl() {
      return issueBaseUrl;
   }

   public JiraIssueManager(String serverURL, String authString, String projectKey) {
      this(serverURL, authString, projectKey, false);
   }

   public JiraIssueManager(String serverURL, String authString, String projectKey, boolean useOptimizedLoading) {
      this.serverURL = serverURL;
      this.authString = authString;
      this.projectKey = projectKey;
      this.useOptimizedLoading = useOptimizedLoading;
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

   public void loadIssues(Date lastUpdated) throws Exception {
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

      if (useOptimizedLoading) {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("jql", jql);

         HttpURLConnection searchConnection = createConnection(REST_API_PATH + "/search/approximate-count", connection -> {
            try {
               connection.setRequestMethod("POST");
               connection.setDoOutput(true);
               byte[] payload = requestBody.toString().getBytes(StandardCharsets.UTF_8);
               connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
               try (OutputStream outputStream = connection.getOutputStream()) {
                  outputStream.write(payload);
               }
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
         HttpURLConnection searchConnection = createConnection(REST_API_PATH + "/search?maxResults=0" + query, null);
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

      if (useOptimizedLoading) {
         logger.info("Loading " + total + " issues using continuation token pagination");

         String nextPageToken = null;
         do {
            SearchPageResult pageResult = loadIssuesWithBulkFetch(jql, MAX_RESULTS, nextPageToken);
            count += pageResult.getLoadedCount();
            nextPageToken = pageResult.getNextPageToken();
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
            executorService.shutdown();
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
   private class SearchIdsPagePayload {
      private final List<String> issueIdsOrKeys;
      private final String nextPageToken;

      public SearchIdsPagePayload(List<String> issueIdsOrKeys, String nextPageToken) {
         this.issueIdsOrKeys = issueIdsOrKeys;
         this.nextPageToken = nextPageToken;
      }

      public List<String> getIssueIdsOrKeys() {
         return issueIdsOrKeys;
      }

      public String getNextPageToken() {
         return nextPageToken;
      }
    }

   private SearchPageResult loadIssuesWithBulkFetch(String jql, int maxResults, String nextPageToken) throws Exception {
      SearchIdsPagePayload idsPagePayload = searchIssueIdsJQL(jql, maxResults, nextPageToken);
      int loadedCount = bulkFetchIssues(idsPagePayload.getIssueIdsOrKeys());
      return new SearchPageResult(loadedCount, idsPagePayload.getNextPageToken());
   }

   private SearchIdsPagePayload searchIssueIdsJQL(String jql, int maxResults, String nextPageToken) throws Exception {
      SearchPagePayload searchPagePayload = searchIssuesJQL(jql, maxResults, nextPageToken);
      List<String> issueIdsOrKeys = new ArrayList<>();

      JsonArray issuesArray = searchPagePayload.getIssuesArray();
      if (issuesArray != null) {
         for (JsonElement issueElement : issuesArray) {
            if (issueElement == null || issueElement.isJsonNull()) {
               continue;
            }

            JsonObject issueObject = issueElement.getAsJsonObject();
            if (issueObject.has("id") && !issueObject.get("id").isJsonNull()) {
               issueIdsOrKeys.add(issueObject.get("id").getAsString());
            } else if (issueObject.has("key") && !issueObject.get("key").isJsonNull()) {
               issueIdsOrKeys.add(issueObject.get("key").getAsString());
            }
         }
      }

      return new SearchIdsPagePayload(issueIdsOrKeys, searchPagePayload.getNextPageToken());
   }

   private int bulkFetchIssues(List<String> issueIdsOrKeys) throws Exception {
      if (issueIdsOrKeys == null || issueIdsOrKeys.isEmpty()) {
         return 0;
      }

      JsonObject requestBody = new JsonObject();
      JsonArray idsOrKeysArray = new JsonArray();
      for (String idOrKey : issueIdsOrKeys) {
         if (idOrKey != null && !idOrKey.trim().isEmpty()) {
            idsOrKeysArray.add(idOrKey);
         }
      }
      requestBody.add("issueIdsOrKeys", idsOrKeysArray);
      requestBody.add("fields", buildRequiredIssueFields());

      HttpURLConnection connection = createConnection(REST_API_PATH + "/issue/bulkfetch", configuredConnection -> {
         try {
            configuredConnection.setRequestMethod("POST");
            configuredConnection.setDoOutput(true);
            byte[] payload = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            configuredConnection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            try (OutputStream outputStream = configuredConnection.getOutputStream()) {
               outputStream.write(payload);
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });

      try {
         try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
            JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();
            JsonArray issuesArray = jsonObject.getAsJsonArray("issues");
            return loadIssuesFromSearchResults(issuesArray);
         }
      } finally {
         connection.disconnect();
      }
   }

   private JsonArray buildRequiredIssueFields() {
      List<String> requiredFields = List.of(
         "assignee",
         "components",
         "created",
         "creator",
         "description",
         "issuetype",
         "labels",
         "reporter",
         "resolution",
         "status",
         "summary",
         "updated"
      );

      JsonArray fields = new JsonArray();
      requiredFields.stream().sorted().collect(Collectors.toList()).forEach(fields::add);
      return fields;
   }

   private SearchPagePayload searchIssuesJQL(String jql, int maxResults, String nextPageToken) throws Exception {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("jql", jql);
      requestBody.addProperty("maxResults", maxResults);

      JsonArray fields = new JsonArray();
      fields.add("id");
      requestBody.add("fields", fields);

      // Handle blank/empty tokens: normalize to null to prevent infinite loops
      if (nextPageToken != null && !nextPageToken.trim().isEmpty()) {
         requestBody.addProperty("nextPageToken", nextPageToken);
      }

      HttpURLConnection connection = createConnection(REST_API_PATH + "/search/jql", configuredConnection -> {
         try {
            configuredConnection.setRequestMethod("POST");
            configuredConnection.setDoOutput(true);
            byte[] payload = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            configuredConnection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            try (OutputStream outputStream = configuredConnection.getOutputStream()) {
               outputStream.write(payload);
            }
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

   private int loadIssuesFromSearchResults(JsonArray issuesArray) throws Exception {
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
         executor.shutdown();
      }
   }

   private int loadIssues(String query, int start, int maxResults) throws Exception {
      int result = 0;

      HttpURLConnection connection = createConnection(REST_API_PATH + "/search?fields=*all&maxResults=" + maxResults + "&startAt=" + start + query, null);
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
         // Jira Cloud may return Atlassian Document Format objects.
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
