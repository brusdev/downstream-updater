package dev.brus.downstream.updater.project;

import java.util.ArrayList;
import java.util.List;

public class ProjectStream {
   private String name;
   private String assignee;
   private String release;
   private String upstreamBranch;
   private String downstreamBranch;
   private List<String> excludedDownstreamIssues;
   private List<String> excludedUpstreamIssues;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getAssignee() {
      return assignee;
   }

   public void setAssignee(String assignee) {
      this.assignee = assignee;
   }

   public String getRelease() {
      return release;
   }

   public void setRelease(String release) {
      this.release = release;
   }

   public String getUpstreamBranch() {
      return upstreamBranch;
   }

   public void setUpstreamBranch(String upstreamBranch) {
      this.upstreamBranch = upstreamBranch;
   }

   public String getDownstreamBranch() {
      return downstreamBranch;
   }

   public void setDownstreamBranch(String downstreamBranch) {
      this.downstreamBranch = downstreamBranch;
   }

   public List<String> getExcludedDownstreamIssues() {
      return excludedDownstreamIssues;
   }

   public void setExcludedDownstreamIssues(List<String> excludedDownstreamIssues) {
      this.excludedDownstreamIssues = excludedDownstreamIssues;
   }

   public List<String> getExcludedUpstreamIssues() {
      return excludedUpstreamIssues;
   }

   public void setExcludedUpstreamIssues(List<String> excludedUpstreamIssues) {
      this.excludedUpstreamIssues = excludedUpstreamIssues;
   }

   public ProjectStream() {
      this.excludedDownstreamIssues = new ArrayList<>();
      this.excludedUpstreamIssues = new ArrayList<>();
   }
}
