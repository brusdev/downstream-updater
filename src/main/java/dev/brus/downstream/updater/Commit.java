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

public class Commit {
   public enum State {
      TODO,
      DONE, // cherry-pick eseguito // verificare stato downstream issues // si può solo fare il revert e tornare a TODO
      INCOMPLETE, // cherry-pick eseguito ma lo stato e/o le labels delle downstream issues non sono valide // aggiornare le downstream issue oppure fare il revert e tornare a TODO
      BLOCKED, // cherry-pick non eseguito perchè il commit è relativo ad un upstream bug issue ma non esiste la relativa downstream issue oppure è esiste la downstream issue ma la target release non corrisponde // clonare issue da upstream issue o downstream issues oppure aggiungere alla downstream issue con NO_BACKPORT_NEEDED label per ignorarej
      SKIPPED, // rimuove NO_BACKPORT_NEEDED
      FAILED,
   }

   private String assignee;
   private Commit.State state;
   private String upstreamIssue;
   private List<String> downstreamIssues;
   private String upstreamCommit;
   private String downstreamCommit;
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

   public String getUpstreamIssue() {
      return upstreamIssue;
   }

   public Commit setUpstreamIssue(String upstreamIssue) {
      this.upstreamIssue = upstreamIssue;
      return this;
   }

   public List<String> getDownstreamIssues() {
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
}
