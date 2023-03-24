package dev.brus.midstream.updater.util;

import java.io.IOException;
import java.io.StringWriter;

import dev.brus.downstream.updater.util.CommandExecutor;
import org.junit.Assert;
import org.junit.Test;

public class CommandExecutorTest {

   @Test
   public void testExecute() throws Exception {
      StringWriter outputWriter = new StringWriter();

      CommandExecutor.execute("echo hello world", null, outputWriter);

      Assert.assertEquals("hello world\n", outputWriter.toString());

      try {
         CommandExecutor.execute("abcdef", null, outputWriter);
         Assert.fail();
      } catch (Exception e) {
         Assert.assertEquals(IOException.class, e.getClass());
      }
   }

   @Test
   public void testTryExecute() throws Exception {
      StringWriter outputWriter = new StringWriter();

      int exitCode = CommandExecutor.tryExecute("echo hello world", null, outputWriter);

      Assert.assertEquals(0, exitCode);
      Assert.assertEquals("hello world\n", outputWriter.toString());

      try {
         CommandExecutor.tryExecute("abcdef", null, outputWriter);
         Assert.fail();
      } catch (Exception e) {
         Assert.assertEquals(IOException.class, e.getClass());
      }
   }
}
