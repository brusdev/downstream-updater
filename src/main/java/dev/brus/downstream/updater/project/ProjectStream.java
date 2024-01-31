package dev.brus.downstream.updater.project;

import java.util.ArrayList;
import java.util.List;

public class ProjectStream {

   public enum Mode {
      VIEWING,
      MANAGING,
      UPDATING
   }

   private String name;
   private String assignee;
   private String release;
   private Mode mode;
   private String upstreamBranch;
   private String downstreamBranch;
   private String downstreamIssuesCustomerPriority;
   private String downstreamIssuesSecurityImpact;
   private Boolean downstreamIssuesRequired;
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

   public Mode getMode() {
      return mode;
   }

   public void setMode(Mode mode) {
      this.mode = mode;
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

   public String getDownstreamIssuesCustomerPriority() {
      return downstreamIssuesCustomerPriority;
   }

   public void setDownstreamIssuesCustomerPriority(String downstreamIssuesCustomerPriority) {
      this.downstreamIssuesCustomerPriority = downstreamIssuesCustomerPriority;
   }

   public String getDownstreamIssuesSecurityImpact() {
      return downstreamIssuesSecurityImpact;
   }

   public void setDownstreamIssuesSecurityImpact(String downstreamIssuesSecurityImpact) {
      this.downstreamIssuesSecurityImpact = downstreamIssuesSecurityImpact;
   }

   public Boolean getDownstreamIssuesRequired() {
      return downstreamIssuesRequired;
   }

   public void setDownstreamIssuesRequired(Boolean downstreamIssuesRequired) {
      this.downstreamIssuesRequired = downstreamIssuesRequired;
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
      this.mode = Mode.VIEWING;
      this.downstreamIssuesRequired = false;
      this.excludedDownstreamIssues = new ArrayList<>();
      this.excludedUpstreamIssues = new ArrayList<>();
   }
}
