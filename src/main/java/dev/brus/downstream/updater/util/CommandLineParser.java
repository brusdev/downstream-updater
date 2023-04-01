package dev.brus.downstream.updater.util;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class CommandLineParser {

   private Options options;
   private org.apache.commons.cli.CommandLineParser parser;

   public CommandLineParser() {
      this.options = new Options();
      this.parser = new DefaultParser();
   }

   public CommandLine parse(String[] args) throws Exception {
      return new CommandLine(parser.parse(options, args));
   }

   public void addOption(String opt, String longOpt, boolean required, boolean hasArg, boolean hasOptionalArg, String description) {
      Option option = new Option(opt, longOpt, hasArg, description);
      option.setRequired(required);
      option.setOptionalArg(hasOptionalArg);

      options.addOption(option);
   }

   public void clearOptions() {
      options = new Options();
   }
}
