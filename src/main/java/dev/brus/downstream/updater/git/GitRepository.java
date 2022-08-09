package dev.brus.downstream.updater.git;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.eclipse.jgit.revwalk.RevCommit;

public interface GitRepository {
   File getDirectory();

   GitRepository open(File dir) throws Exception;

   GitRepository clone(String uri, File dir) throws Exception;

   GitCommit resolveCommit(String name) throws Exception;

   boolean cherryPick(GitCommit commit) throws Exception;

   void resetHard() throws Exception;

   List<String> getChangedFiles(GitCommit commit) throws Exception;

   GitCommit commit(String message, String authorName, String authorEmail, Date authorWhen, TimeZone authorTimezone, String committerName, String committerEmail) throws Exception;

   void push(String remote, String name) throws Exception;

   void fetch(String remote) throws Exception;

   void remoteAdd(String name, String uri) throws Exception;

   boolean branchExists(String name) throws Exception;

   void branchCreate(String name, String startPoint) throws Exception;

   void branchDelete(String name) throws Exception;

   void checkout(String name) throws Exception;

   Iterable<GitCommit> log(String addStart, String notStart) throws Exception;

}
