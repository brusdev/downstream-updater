package dev.brus.midstream.updater.git;

import java.util.Date;
import java.util.TimeZone;

import dev.brus.downstream.updater.git.GitCommit;

public class MockGitCommit implements GitCommit {
   private String fullMessage;
   private String name;
   private String authorName;
   private String authorEmail;
   private Date authorWhen;
   private TimeZone authorTimeZone;
   private String committerName;
   private String committerEmail;
   private Date committerWhen;
   private TimeZone committerTimeZone;
   private String shortMessage;

   @Override
   public String getFullMessage() {
      return fullMessage;
   }

   public MockGitCommit setFullMessage(String fullMessage) {
      this.fullMessage = fullMessage;
      return this;
   }

   @Override
   public String getName() {
      return name;
   }

   public MockGitCommit setName(String name) {
      this.name = name;
      return this;
   }

   @Override
   public String getAuthorName() {
      return authorName;
   }

   public MockGitCommit setAuthorName(String authorName) {
      this.authorName = authorName;
      return this;
   }

   @Override
   public String getAuthorEmail() {
      return authorEmail;
   }

   public MockGitCommit setAuthorEmail(String authorEmail) {
      this.authorEmail = authorEmail;
      return this;
   }

   @Override
   public Date getAuthorWhen() {
      return authorWhen;
   }

   public MockGitCommit setAuthorWhen(Date authorWhen) {
      this.authorWhen = authorWhen;
      return this;
   }

   @Override
   public TimeZone getAuthorTimeZone() {
      return authorTimeZone;
   }

   public MockGitCommit setAuthorTimeZone(TimeZone authorTimeZone) {
      this.authorTimeZone = authorTimeZone;
      return this;
   }

   @Override
   public String getCommitterName() {
      return committerName;
   }

   public MockGitCommit setCommitterName(String committerName) {
      this.committerName = committerName;
      return this;
   }

   @Override
   public String getCommitterEmail() {
      return committerEmail;
   }

   public MockGitCommit setCommitterEmail(String committerEmail) {
      this.committerEmail = committerEmail;
      return this;
   }

   @Override
   public Date getCommitterWhen() {
      return committerWhen;
   }

   public MockGitCommit setCommitterWhen(Date committerWhen) {
      this.committerWhen = committerWhen;
      return this;
   }

   @Override
   public TimeZone getCommitterTimeZone() {
      return committerTimeZone;
   }

   public MockGitCommit setCommitterTimeZone(TimeZone committerTimeZone) {
      this.committerTimeZone = committerTimeZone;
      return this;
   }

   @Override
   public String getShortMessage() {
      return shortMessage;
   }

   public MockGitCommit setShortMessage(String shortMessage) {
      this.shortMessage = shortMessage;
      return this;
   }
}
