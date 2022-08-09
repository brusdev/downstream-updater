package dev.brus.downstream.updater.git;

import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.revwalk.RevCommit;

public class JGitCommit implements GitCommit {
   private RevCommit revCommit;

   public JGitCommit(RevCommit revCommit) {
      this.revCommit = revCommit;
   }

   public RevCommit getRevCommit() {
      return revCommit;
   }

   @Override
   public String getFullMessage() {
      return revCommit.getFullMessage();
   }

   @Override
   public String getName() {
      return revCommit.getName();
   }

   @Override
   public String getAuthorName() {
      return revCommit.getAuthorIdent().getName();
   }

   @Override
   public String getAuthorEmail() {
      return revCommit.getAuthorIdent().getEmailAddress();
   }

   @Override
   public Date getAuthorWhen() {
      return revCommit.getAuthorIdent().getWhen();
   }

   @Override
   public TimeZone getAuthorTimeZone() {
      return revCommit.getAuthorIdent().getTimeZone();
   }

   @Override
   public String getCommitterName() {
      return revCommit.getCommitterIdent().getName();
   }

   @Override
   public String getCommitterEmail() {
      return revCommit.getCommitterIdent().getEmailAddress();
   }

   @Override
   public Date getCommitterWhen() {
      return revCommit.getCommitterIdent().getWhen();
   }

   @Override
   public TimeZone getCommitterTimeZone() {
      return revCommit.getCommitterIdent().getTimeZone();
   }

   @Override
   public String getShortMessage() {
      return revCommit.getShortMessage();
   }
}
