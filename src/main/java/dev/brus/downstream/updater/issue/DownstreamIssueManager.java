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

import java.util.List;

public interface DownstreamIssueManager extends IssueManager {

   IssueStateMachine getIssueStateMachine();

   String getIssueStateDevComplete();

   String getIssueLabelNoBackportNeeded();

   String getIssueLabelNoTestingNeeded();

   String getIssueLabelUpstreamTestCoverage();

   void addIssueLabels(String issueKey, String... labels) throws Exception;

   void addIssueUpstreamIssues(String issueKey, String... upstreamIssues) throws Exception;

   void copyIssueUpstreamIssues(String fromIssueKey, String toIssueKey) throws Exception;

   void setIssueTargetRelease(String issueKey, String targetRelease) throws Exception;

   void transitionIssue(String issueKey, String finalStatus) throws Exception;

   Issue createIssue(String summary, String description, String type, String assignee, String targetRelease, List<String> labels) throws Exception;

   void linkIssue(String issueKey, String cloningIssueKey, String linkType) throws Exception;
}
