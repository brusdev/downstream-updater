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

import java.util.Collections;
import java.util.Map;

public class CommitTask {
   public enum Type {
      SET_DOWNSTREAM_ISSUE_TARGET_RELEASE,
      ADD_LABEL_TO_DOWNSTREAM_ISSUE,
      ADD_UPSTREAM_ISSUE_TO_DOWNSTREAM_ISSUE,
      TRANSITION_DOWNSTREAM_ISSUE,
      CHERRY_PICK_UPSTREAM_COMMIT,
      CLONE_DOWNSTREAM_ISSUE,
      CLONE_UPSTREAM_ISSUE,
      EXCLUDE_UPSTREAM_ISSUE,
   }

   public enum State {
      NEW,
      DONE,
      FAILED,
   }

   private CommitTask.Type type;
   private CommitTask.State state;
   private Commit.Action action;
   private Map<String, String> args = Collections.emptyMap();
   private String command;
   private String result;


   public CommitTask.Type getType() {
      return type;
   }

   public CommitTask setType(CommitTask.Type type) {
      this.type = type;
      return this;
   }

   public Map<String, String> getArgs() {
      return args;
   }

   public CommitTask setArgs(Map<String, String> args) {
      this.args = args;
      return this;
   }

   public CommitTask.State getState() {
      return state;
   }

   public CommitTask setState(CommitTask.State state) {
      this.state = state;
      return this;
   }

   public Commit.Action getAction() {
      return action;
   }

   public CommitTask setAction(Commit.Action action) {
      this.action = action;
      return this;
   }

   public String getCommand() {
      return command;
   }

   public CommitTask setCommand(String command) {
      this.command = command;
      return this;
   }

   public String getResult() {
      return result;
   }

   public CommitTask setResult(String result) {
      this.result = result;
      return this;
   }

   public CommitTask() {
      state = State.NEW;
      action = Commit.Action.NONE;
   }
}
