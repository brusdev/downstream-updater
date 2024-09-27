package dev.brus.downstream.updater.git;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import dev.brus.downstream.updater.util.CommandExecutor;
import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGitRepository implements GitRepository {

   private final static Logger logger = LoggerFactory.getLogger(JGitRepository.class);

   private Git git;

   private String userName;
   private String userEmail;

   private Map<String, String> remoteAuthStrings;

   public String getUserName() {
      return userName;
   }

   public JGitRepository setUserName(String userName) {
      this.userName = userName;
      return this;
   }

   public String getUserEmail() {
      return userEmail;
   }

   public JGitRepository setUserEmail(String userEmail) {
      this.userEmail = userEmail;
      return this;
   }

   public Map<String, String> getRemoteAuthStrings() {
      return remoteAuthStrings;
   }

   public JGitRepository() {
      this.remoteAuthStrings = new HashMap<>();
   }

   public File getDirectory() {
      return git.getRepository().getDirectory().getParentFile();
   }

   @Override
   public GitRepository open(File dir) throws Exception {
      git = Git.open(dir);
      return this;
   }

   @Override
   public GitRepository clone(String uri, File dir) throws Exception {
      git = Git.cloneRepository()
         .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
         .setURI(uri)
         .setDirectory(dir)
         .call();
      return this;
   }

   @Override
   public void close() throws Exception {
      git.close();
   }

   public GitCommit resolveCommit(String name) throws Exception {
      RevCommit revCommit = git.getRepository().parseCommit(git.getRepository().resolve(name));

      return new JGitCommit(revCommit);
   }

   public void cherryPick(GitCommit commit) throws Exception {
      CherryPickResult cherryPickResult = git.cherryPick().include(((JGitCommit)commit).getRevCommit()).setNoCommit(true).call();

      // Try the git command if JGit fails
      if (cherryPickResult.getStatus() == CherryPickResult.CherryPickStatus.CONFLICTING) {
         resetHard();

         CommandExecutor.execute("git cherry-pick --no-commit " + commit.getName(), getDirectory(), null);
      } else if (cherryPickResult.getStatus() != CherryPickResult.CherryPickStatus.OK) {
         throw new IllegalStateException("Cherry pick failed: " + cherryPickResult.getStatus());
      }
   }

   public void resetHard() throws Exception {
      git.reset().setMode(ResetCommand.ResetType.HARD).call();
   }

   public List<String> getChangedFiles(GitCommit commit) throws Exception {
      List<String> changedFiles = new ArrayList<>();
      try (ObjectReader reader = git.getRepository().newObjectReader()) {
         CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
         CanonicalTreeParser newTreeIter = new CanonicalTreeParser();

         oldTreeIter.reset(reader, ((JGitCommit)commit).getRevCommit().getParent(0).getTree());
         newTreeIter.reset(reader, ((JGitCommit)commit).getRevCommit().getTree());

         List<DiffEntry> diffList = git.diff().setOldTree(oldTreeIter).setNewTree(newTreeIter).call();

         for (DiffEntry diffEntry : diffList) {
            String diffEntryPath = diffEntry.getNewPath();
            if (diffEntryPath != null && !diffEntryPath.isEmpty()) {
               changedFiles.add(diffEntryPath);
            }
         }
      }

      return changedFiles;
   }

   @Override
   public void add(String filePattern) throws Exception {
      git.add().addFilepattern(filePattern).call();
   }

   @Override
   public GitCommit commit(String message) throws Exception {
      Calendar calendar = Calendar.getInstance();
      return commit(
         message,
         userName,
         userEmail,
         calendar.getTime(),
         calendar.getTimeZone(),
         userName,
         userEmail);
   }

   @Override
   public GitCommit commit(String message,
      String authorName,
      String authorEmail,
      Date authorWhen,
      TimeZone authorTimezone) throws Exception {
      return commit(
         message,
         authorName,
         authorEmail,
         authorWhen,
         authorTimezone,
         userName,
         userEmail);
   }

   @Override
   public GitCommit commit(String message,
                           String authorName,
                           String authorEmail,
                           Date authorWhen,
                           TimeZone authorTimezone,
                           String committerName,
                           String committerEmail) throws Exception {
      RevCommit revCommit = git.commit().setMessage(message)
         .setAuthor(new PersonIdent(authorName, authorEmail, authorWhen, authorTimezone))
         .setCommitter(committerName, committerEmail)
         .call();

      return new JGitCommit(revCommit);
   }

   @Override
   public void push(String remote, String name) throws Exception {
      if (name == null) {
         name = git.getRepository().getBranch();
      }

      CredentialsProvider credentialsProvider = null;
      String remoteAuthString = remoteAuthStrings.get(remote);
      if (remoteAuthString != null) {
         String[] authStringTokens = remoteAuthString.split(":");
         if (authStringTokens.length > 1) {
            credentialsProvider = new UsernamePasswordCredentialsProvider(authStringTokens[0], authStringTokens[1]);
         } else {
            credentialsProvider = new UsernamePasswordCredentialsProvider(remoteAuthString, "");
         }
      } else {
         URIish remoteUrl = new URIish(git.getRepository().getConfig().getString("remote", remote, "url"));
         if ("https".equals(remoteUrl.getScheme()) && remoteUrl.getUser() != null) {
            credentialsProvider = new UsernamePasswordCredentialsProvider(
               remoteUrl.getUser(), remoteUrl.getPass() != null ? remoteUrl.getPass() : "");
         }
      }

      Iterable<PushResult> pushResults = git.push().setRemote(remote).setRefSpecs(new RefSpec(name)).setCredentialsProvider(credentialsProvider).call();

      for (PushResult pushResult : pushResults) {
         for (RemoteRefUpdate remoteRefUpdate : pushResult.getRemoteUpdates()) {
            if (remoteRefUpdate.getStatus() != RemoteRefUpdate.Status.OK) {
               throw new IllegalStateException("Update failed with status: " + remoteRefUpdate.getStatus() + " and message: " + remoteRefUpdate.getMessage());
            }
         }
      }
   }

   @Override
   public void fetch(String remote) throws Exception {
      git.fetch().setRemote(remote).call();
   }

   @Override
   public void remoteAdd(String name, String uri) throws Exception {
      git.remoteAdd().setName(name).setUri(new URIish(uri)).call();
   }

   @Override
   public String remoteGet(String name) throws Exception {
      URIish remoteUri = new URIish(git.getRepository().getConfig().getString("remote", name, "url"));
      return remoteUri.getScheme() + "://" + remoteUri.getHost() + remoteUri.getPath();
   }

   @Override
   public boolean branchExists(String name) throws Exception {
      return git.getRepository().resolve(name) != null;
   }

   @Override
   public void branchCreate(String name, String startPoint) throws Exception {
      git.branchCreate().setName(name).setForce(true).setStartPoint(startPoint).call();
   }

   @Override
   public void branchDelete(String name) throws Exception {
      git.branchDelete().setBranchNames(name).setForce(true).call();
   }

   @Override
   public void checkout(String name) throws Exception {
      git.checkout().setName(name).setForced(true).call();
   }

   @Override
   public Iterable<GitCommit> log(String addStart, String notStart) throws Exception {
      Iterable<RevCommit> logIterable = git.log()
         .not(git.getRepository().resolve(notStart))
         .add(git.getRepository().resolve(addStart))
         .call();

      return () -> {
         Iterator<RevCommit> logIterator = logIterable.iterator();
         return new Iterator<>() {
            @Override
            public boolean hasNext() {
               return logIterator.hasNext();
            }

            @Override
            public GitCommit next() {
               return new JGitCommit(logIterator.next());
            }
         };
      };
   }
}
