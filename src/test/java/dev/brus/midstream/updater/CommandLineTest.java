package dev.brus.midstream.updater;

import org.apache.tools.ant.types.Commandline;
import org.junit.Assert;
import org.junit.Test;

public class CommandLineTest {

   @Test
   public void testTestCommand() {
      String testCommand = "mvn --show-version --activate-profiles=dev,tests,redhat-indy --define=failIfNoTests=false --define=test=Test0 clean package";
      String[] testCommandTokens = Commandline.translateCommandline(testCommand);
      Assert.assertEquals(7, testCommandTokens.length);
   }

   @Test
   public void testTestCommandWithQuotedArgs() {
      String testCommand = "mvn --show-version --activate-profiles=dev,tests,redhat-indy --define=\"failIfNoTests=false\" --define=test=\"Test0, Test1\" clean package";
      String[] testCommandTokens = Commandline.translateCommandline(testCommand);
      Assert.assertEquals(7, testCommandTokens.length);
   }
}
