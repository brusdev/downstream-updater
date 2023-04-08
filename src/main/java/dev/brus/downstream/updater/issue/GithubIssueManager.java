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
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public class GithubIssueManager implements IssueManager {
   private final static Logger logger = LoggerFactory.getLogger(GithubIssueManager.class);

   private final static String ISSUE_TYPE_BUG = "Bug";

   private final static String dateFormatPattern = "yyyy-MM-dd'T'HH:mm:SS'Z'";

   private final static Pattern serverURLPattern = Pattern.compile("https://api.github.com/repos/([^/]+)/([^/]+)/issues");

   private String serverURL;
   private String authString;
   private String projectKey;
   private String issueBaseUrl;

   private String owner;

   private String repo;

   private Map<String, Issue> issues;

   protected final SimpleDateFormat defaultDateFormat = new SimpleDateFormat(dateFormatPattern);

   private Gson gson = new GsonBuilder().setDateFormat(dateFormatPattern).setPrettyPrinting().create();

   private Pattern issueKeyPattern;

   @Override
   public String getServerURL() {
      return serverURL;
   }

   @Override
   public String getAuthString() {
      return authString;
   }

   @Override
   public String getProjectKey() {
      return projectKey;
   }

   @Override
   public String getIssueBaseUrl() {
      return issueBaseUrl;
   }

   public GithubIssueManager(String serverURL, String authString, String projectKey) {
      Matcher serverURLMatcher = serverURLPattern.matcher(serverURL);

      if (!serverURLMatcher.find()) {
         throw new IllegalArgumentException("Server URL doesn't match required pattern");
      }

      this.serverURL = serverURL;
      this.authString = authString;
      this.projectKey = projectKey;
      this.issues = new HashMap<>();

      this.owner = serverURLMatcher.group(1);
      this.repo = serverURLMatcher.group(2);
      this.issueBaseUrl = "https://github.com/" + owner + "/" + repo + "/issues";
      this.issueKeyPattern = Pattern.compile("(https://github.com/" + this.owner + "/" + this.repo + "/issues/|" + projectKey + "-|\\[#)([0-9]+)");
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
      throw new IllegalStateException();
   }

   @Override
   public void loadIssues() throws Exception {
      loadIssues(new Date(0));
   }

   private void loadIssues(Date lastUpdated) throws Exception {
      final int MAX_RESULTS = 100;

      String lastUpdatedQuery = "";
      if (lastUpdated != null) {
         SimpleDateFormat queryDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
         lastUpdatedQuery = "&since=" + queryDateFormat.format(lastUpdated);
      }
      String query = "&state=all" + lastUpdatedQuery;

      int page = 1;
      int count = 0;
      int pageCount = MAX_RESULTS;

      long beginTimestamp = System.nanoTime();
      while (pageCount == MAX_RESULTS) {
         pageCount = loadIssues(query, page, MAX_RESULTS);

         page++;
         count += pageCount;
      }
      long endTimestamp = System.nanoTime();

      logger.info("Loaded " + count + " issues in " + (endTimestamp - beginTimestamp) / 1000000 + " milliseconds");
   }


   private int loadIssues(String query, int page, int maxResults) throws Exception {
      int result = 0;

      HttpURLConnection connection = createConnection("?page=" + page + "&per_page=" + maxResults + query);
      try {
         try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
            JsonArray issuesArray = JsonParser.parseReader(inputStreamReader).getAsJsonArray();

            for (JsonElement issueElement : issuesArray) {
               JsonObject issueObject = issueElement.getAsJsonObject();

               Issue issue = parseIssue(issueObject);

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
         String issueNumber = issueKeyMatcher.group(2);
         String upstreamIssueKey = projectKey + "-" + issueNumber;
         issueKeys.add(upstreamIssueKey);
      }

      return issueKeys;
   }

   private Issue parseIssue(JsonObject issueObject) throws Exception {
      int issueNumber = issueObject.getAsJsonPrimitive("number").getAsInt();
      String issueKey = projectKey + "-" + issueNumber;
      logger.debug("loading issue " + issueKey);

      JsonElement issueAssigneeElement = issueObject.get("assignee");
      String issueAssignee = issueAssigneeElement != null && !issueAssigneeElement.isJsonNull() ?
         issueAssigneeElement.getAsJsonObject().getAsJsonPrimitive("login").getAsString() : null;
      String issueUser = issueObject.getAsJsonObject("user").getAsJsonPrimitive("login").getAsString();
      String issueStatus = issueObject.getAsJsonPrimitive("state").getAsString();
      JsonElement issueBodyElement = issueObject.get("body");
      String issueDescription = issueBodyElement != null && !issueBodyElement.isJsonNull() ? issueBodyElement.getAsString() : null;
      String issueType = "PullRequest";
      List<String> issueLabels = parseLabels(issueObject.get("labels"));
      if (issueLabels.contains("bug")) {
         issueType = "Bug";
      } else if (issueLabels.contains("enhancement")) {
         issueType = "Enhancement";
      }
      String issueSummary = issueObject.getAsJsonPrimitive("title").getAsString();
      Date issueCreated = defaultDateFormat.parse(issueObject.getAsJsonPrimitive("created_at").getAsString());
      Date issueUpdated = defaultDateFormat.parse(issueObject.getAsJsonPrimitive("updated_at").getAsString());
      String issueUrl = issueObject.getAsJsonPrimitive("html_url").getAsString();

      Issue issue = new Issue()
         .setKey(issueKey)
         .setAssignee(issueAssignee)
         .setCreator(issueUser)
         .setReporter(issueUser)
         .setState(issueStatus)
         .setSummary(issueSummary)
         .setDescription(issueDescription)
         .setCreated(issueCreated)
         .setUpdated(issueUpdated)
         .setUrl(issueUrl)
         .setType(issueType);

      for (String label : issueLabels) {
         issue.getLabels().add(label);
      }

      issue.setCustomerPriority(IssueCustomerPriority.NONE);
      issue.setSecurityImpact(IssueSecurityImpact.NONE);

      return issue;
   }

   private List<String> parseLabels(JsonElement labelsElement) {
      List<String> labels = new ArrayList<>();

      if (labelsElement != null && !labelsElement.isJsonNull()) {
         for (JsonElement issueLabelElement : labelsElement.getAsJsonArray()) {
            if (issueLabelElement != null && !issueLabelElement.isJsonNull()) {
               labels.add(issueLabelElement.getAsJsonObject().getAsJsonPrimitive("name").getAsString());
            }
         }
      }

      return labels;
   }

   private HttpURLConnection createConnection(String url) throws Exception {
      URL upstreamJIRA = new URL(serverURL + url);
      HttpURLConnection connection = (HttpURLConnection)upstreamJIRA.openConnection();
      connection.setRequestProperty("Accept", "application/vnd.github+json");

      if (authString != null) {
         connection.setRequestProperty("Authorization", authString);
      }

      return connection;
   }
}
