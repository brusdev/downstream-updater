package dev.brus.midstream.updater.util;

import java.io.StringWriter;

import dev.brus.downstream.updater.util.CommandExecutor;
import org.junit.Assert;
import org.junit.Test;

public class CommandExecutorTest {

   @Test
   public void testEchoCommand() throws Exception {
      StringWriter outputWriter = new StringWriter();

      int exitCode = CommandExecutor.execute("echo hello world", null, outputWriter);

      Assert.assertEquals(0, exitCode);
      Assert.assertEquals("hello world\n", outputWriter.toString());
   }
}
