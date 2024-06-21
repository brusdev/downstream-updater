package dev.brus.downstream.updater.project;

public class ExcludedIssue {
   private String key;
   private String until;

   public String getKey() {
      return key;
   }

   public void setKey(String key) {
      this.key = key;
   }

   public String getUntil() {
      return until;
   }

   public void setUntil(String until) {
      this.until = until;
   }

   public ExcludedIssue() {
   }

   public ExcludedIssue(String key) {
      this.key = key;
   }
}
