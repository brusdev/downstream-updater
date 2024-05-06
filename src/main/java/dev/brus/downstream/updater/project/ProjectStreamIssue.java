package dev.brus.downstream.updater.project;

public class ProjectStreamIssue {
   private String key;
   private String end;

   public String getKey() {
      return key;
   }

   public void setKey(String key) {
      this.key = key;
   }

   public String getEnd() {
      return end;
   }

   public void setEnd(String end) {
      this.end = end;
   }

   public ProjectStreamIssue() {
   }

   public ProjectStreamIssue(String key) {
      this.key = key;
   }
}
