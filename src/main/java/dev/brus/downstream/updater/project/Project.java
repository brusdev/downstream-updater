package dev.brus.downstream.updater.project;

import java.util.ArrayList;
import java.util.List;

public class Project {

   private String name;
   private String upstreamIssuesProjectKey;
   private String upstreamIssuesServerType;
   private String upstreamIssuesServer;
   private String upstreamRepository;
   private String downstreamIssuesProjectKey;
   private String downstreamIssuesServerType;
   private String downstreamIssuesServer;
   private String downstreamRepository;
   private String targetReleaseFormat;
   private String checkCommand;
   private String checkTestCommand;
   private List<ProjectStream> streams;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getUpstreamIssuesProjectKey() {
      return upstreamIssuesProjectKey;
   }

   public void setUpstreamIssuesProjectKey(String upstreamIssuesProjectKey) {
      this.upstreamIssuesProjectKey = upstreamIssuesProjectKey;
   }

   public String getUpstreamIssuesServerType() {
      return upstreamIssuesServerType;
   }

   public void setUpstreamIssuesServerType(String upstreamIssuesServerType) {
      this.upstreamIssuesServerType = upstreamIssuesServerType;
   }

   public String getUpstreamIssuesServer() {
      return upstreamIssuesServer;
   }

   public void setUpstreamIssuesServer(String upstreamIssuesServer) {
      this.upstreamIssuesServer = upstreamIssuesServer;
   }

   public String getUpstreamRepository() {
      return upstreamRepository;
   }

   public void setUpstreamRepository(String upstreamRepository) {
      this.upstreamRepository = upstreamRepository;
   }

   public String getDownstreamIssuesProjectKey() {
      return downstreamIssuesProjectKey;
   }

   public void setDownstreamIssuesProjectKey(String downstreamIssuesProjectKey) {
      this.downstreamIssuesProjectKey = downstreamIssuesProjectKey;
   }

   public String getDownstreamIssuesServerType() {
      return downstreamIssuesServerType;
   }

   public void setDownstreamIssuesServerType(String downstreamIssuesServerType) {
      this.downstreamIssuesServerType = downstreamIssuesServerType;
   }

   public String getDownstreamIssuesServer() {
      return downstreamIssuesServer;
   }

   public void setDownstreamIssuesServer(String downstreamIssuesServer) {
      this.downstreamIssuesServer = downstreamIssuesServer;
   }

   public String getDownstreamRepository() {
      return downstreamRepository;
   }

   public void setDownstreamRepository(String downstreamRepository) {
      this.downstreamRepository = downstreamRepository;
   }

   public String getTargetReleaseFormat() {
      return targetReleaseFormat;
   }

   public void setTargetReleaseFormat(String targetReleaseFormat) {
      this.targetReleaseFormat = targetReleaseFormat;
   }

   public String getCheckCommand() {
      return checkCommand;
   }

   public void setCheckCommand(String checkCommand) {
      this.checkCommand = checkCommand;
   }

   public String getCheckTestCommand() {
      return checkTestCommand;
   }

   public void setCheckTestCommand(String checkTestCommand) {
      this.checkTestCommand = checkTestCommand;
   }

   public List<ProjectStream> getStreams() {
      return streams;
   }

   public void setStreams(List<ProjectStream> streams) {
      this.streams = streams;
   }

   public Project() {
      this.streams = new ArrayList<>();
   }

   public ProjectStream getStream(String name) {
      return streams.stream().filter(stream -> name.equals(stream.getName())).findFirst().get();
   }
}
