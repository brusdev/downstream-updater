package dev.brus.midstream.updater.util;

import dev.brus.downstream.updater.util.Network;
import org.junit.Assert;
import org.junit.Test;

public class NetworkTest {

   @Test
   public void testHostAddress() throws Exception {
      String hostAddress = Network.getHostAddress();
      Assert.assertNotNull(hostAddress);
      Assert.assertNotEquals("127.0.0.1", hostAddress);
      Assert.assertTrue(hostAddress.matches("[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}"));
   }
}
