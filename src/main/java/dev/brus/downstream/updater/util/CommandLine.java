package dev.brus.downstream.updater.util;

public class CommandLine {

   private org.apache.commons.cli.CommandLine commandLine;

   public CommandLine(org.apache.commons.cli.CommandLine commandLine) {
      this.commandLine = commandLine;
   }

   public String getOptionValue(String option) {
      return commandLine.getOptionValue(option);
   }

   public String getOptionValue(String option, String defaultValue) {
      return commandLine.getOptionValue(option, defaultValue);
   }

   public boolean hasOption(String option) {
      return commandLine.hasOption(option);
   }
}
