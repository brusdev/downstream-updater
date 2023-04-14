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

package dev.brus.downstream.updater;

import java.util.ArrayList;
import java.util.List;

import dev.brus.downstream.updater.issue.IssueReference;

public class Commit {
   public enum State {
      /**
       * The upstream commit is a bug but the downstream issue doesn't exist or its target release doesn't match,
       * clone the downstream or the upstream issues to confirm or add the NO_BACKPORT_NEEDED label to reject
       */
      NEW,

      /**
       * The downstream issue exists, cherry-pick the upstream commit to go ahead
       */
      TODO,

      /**
       * The upstream commit is cherry-picked and the downstream issues are not updated (labels),
       * update the downstream issues to go ahead
       */
      INCOMPLETE,

      /**
       * The upstream commit is cherry-picked and downstream issues are updated, nothing else to do
       */
      DONE,

      /**
       * The upstream commit doesn't match release criteria
       */
      SKIPPED,

      /**
       * The upstream commit points to an invalid upstream issue
       */
      INVALID,
   }

   private String assignee;
   private Commit.State state;
   private IssueReference upstreamIssue;
   private List<IssueReference> downstreamIssues;
   private String upstreamCommit;
   private String upstreamCommitDir;
   private String upstreamCommitUrl;
   private String downstreamCommit;
   private String downstreamCommitUrl;
   private String author;
   private String summary;
   private String reason;
   private String release;
   private List<String> tests;
   private List<CommitTask> tasks;

   public Commit() {
      downstreamIssues = new ArrayList<>();
      tests = new ArrayList<>();
      tasks = new ArrayList<>();
   }

   public String getAssignee() {
      return assignee;
   }

   public Commit setAssignee(String assignee) {
      this.assignee = assignee;
      return this;
   }
   public List<String> getTests() {
      return tests;
   }

   public Commit setTests(List<String> tests) {
      this.tests = tests;
      return this;
   }

   public List<CommitTask> getTasks() {
      return tasks;
   }

   public Commit setTasks(List<CommitTask> tasks) {
      this.tasks = tasks;
      return this;
   }

   public String getReason() {
      return reason;
   }

   public Commit setReason(String reason) {
      this.reason = reason;
      return this;
   }

   public String getAuthor() {
      return author;
   }

   public Commit setAuthor(String author) {
      this.author = author;
      return this;
   }

   public String getSummary() {
      return summary;
   }

   public Commit setSummary(String summary) {
      this.summary = summary;
      return this;
   }

   public Commit.State getState() {
      return state;
   }

   public Commit setState(Commit.State state) {
      this.state = state;
      return this;
   }

   public IssueReference getUpstreamIssue() {
      return upstreamIssue;
   }

   public Commit setUpstreamIssue(IssueReference upstreamIssue) {
      this.upstreamIssue = upstreamIssue;
      return this;
   }

   public List<IssueReference> getDownstreamIssues() {
      return downstreamIssues;
   }

   public String getUpstreamCommit() {
      return upstreamCommit;
   }

   public Commit setUpstreamCommit(String upstreamCommit) {
      this.upstreamCommit = upstreamCommit;
      return this;
   }

   public String getDownstreamCommit() {
      return downstreamCommit;
   }

   public Commit setDownstreamCommit(String downstreamCommit) {
      this.downstreamCommit = downstreamCommit;
      return this;
   }

   public String getRelease() {
      return release;
   }

   public Commit setRelease(String release) {
      this.release = release;
      return this;
   }

   public String getUpstreamCommitUrl() {
      return upstreamCommitUrl;
   }

   public Commit setUpstreamCommitUrl(String upstreamCommitUrl) {
      this.upstreamCommitUrl = upstreamCommitUrl;
      return this;
   }

   public String getDownstreamCommitUrl() {
      return downstreamCommitUrl;
   }

   public Commit setDownstreamCommitUrl(String downstreamCommitUrl) {
      this.downstreamCommitUrl = downstreamCommitUrl;
      return this;
   }

   public String getUpstreamCommitDir() {
      return upstreamCommitDir;
   }

   public Commit setUpstreamCommitDir(String upstreamCommitDir) {
      this.upstreamCommitDir = upstreamCommitDir;
      return this;
   }
}
