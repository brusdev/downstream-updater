package dev.brus.downstream.updater.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.tools.ant.types.Commandline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandExecutor {

   private final static Logger logger = LoggerFactory.getLogger(CommandExecutor.class);

   public static int execute(String commandLine, File directory, Writer outputWriter) throws IOException, InterruptedException {
      int exitCode;

      if (outputWriter == null) {
         outputWriter = new OutputStreamWriter(OutputStream.nullOutputStream());
      }
      try (BufferedWriter outputBufferedWriter = new BufferedWriter(outputWriter)) {
         logger.debug(commandLine);

         String[] command = Commandline.translateCommandline(commandLine);
         ProcessBuilder commandProcessBuilder = new ProcessBuilder(command)
            .redirectErrorStream(true);

         if (directory != null) {
            commandProcessBuilder.directory(directory);
         }

         Process commandProcess = commandProcessBuilder.start();

         String commandOutputLine;
         try (BufferedReader commandOutputReader = new BufferedReader(new InputStreamReader(commandProcess.getInputStream()))) {
            while ((commandOutputLine = commandOutputReader.readLine ()) != null) {
               logger.debug(commandOutputLine);
               outputBufferedWriter.write(commandOutputLine);
               outputBufferedWriter.newLine();
            }

            exitCode = commandProcess.waitFor();
            logger.debug("Process finished with exit code " + exitCode);
         }

         return exitCode;
      }
   }
}
