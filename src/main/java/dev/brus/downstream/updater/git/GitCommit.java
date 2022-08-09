package dev.brus.downstream.updater.git;

import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.revwalk.RevCommit;

public interface GitCommit {

   String getFullMessage();

   String getName();

   String getAuthorName();
   String getAuthorEmail();
   Date getAuthorWhen();
   TimeZone getAuthorTimeZone();

   String getCommitterName();
   String getCommitterEmail();
   Date getCommitterWhen();
   TimeZone getCommitterTimeZone();

   String getShortMessage();
}
