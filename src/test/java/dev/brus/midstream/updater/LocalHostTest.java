package dev.brus.midstream.updater;

import java.net.InetAddress;

import org.junit.Assert;
import org.junit.Test;

public class LocalHostTest {

   @Test
   public void testLocalHostName() throws Exception {
      Assert.assertNotNull(InetAddress.getLocalHost().getHostName());
   }

   @Test
   public void testLocalHostAddress() throws Exception {
      Assert.assertNotNull(InetAddress.getLocalHost().getHostAddress());
      Assert.assertTrue(InetAddress.getLocalHost().getHostAddress().matches("[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}"));
   }
}
