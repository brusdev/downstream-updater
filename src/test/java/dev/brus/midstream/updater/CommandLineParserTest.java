package dev.brus.midstream.updater;

import dev.brus.downstream.updater.util.CommandLine;
import dev.brus.downstream.updater.util.CommandLineParser;
import org.junit.Assert;
import org.junit.Test;

public class CommandLineParserTest {

   @Test
   public void testNoOptions() throws Exception {
      String testOptionName = "test-option";
      String testOptionValue = "TEST";
      CommandLineParser parser = new CommandLineParser();
      parser.addOption(null, testOptionName, false, true, false, "test option description");

      CommandLine emptyLine = parser.parse(new String[0]);
      Assert.assertNull(emptyLine.getOptionValue(testOptionName));
      Assert.assertNull(emptyLine.getOptionValue(testOptionName, null));

      CommandLine fullLine = parser.parse(new String[] { "--" + testOptionName, testOptionValue });
      Assert.assertEquals(testOptionValue, fullLine.getOptionValue(testOptionName));
      Assert.assertEquals(testOptionValue, fullLine.getOptionValue(testOptionName, null));
   }
}
