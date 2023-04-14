package dev.brus.downstream.updater.issue;

public class IssueReference {

   private String key;
   private String url;

   public String getKey() {
      return key;
   }

   public IssueReference setKey(String key) {
      this.key = key;
      return this;
   }

   public String getUrl() {
      return url;
   }

   public IssueReference setUrl(String url) {
      this.url = url;
      return this;
   }

   public IssueReference() {
   }

   public IssueReference(Issue issue) {
      this(issue.getKey(), issue.getUrl());
   }

   public IssueReference(String key, String url) {
      this.key = key;
      this.url = url;
   }
}
