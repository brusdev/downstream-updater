package dev.brus.downstream.updater.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;

public final class Network {
   public static String getHostAddress() throws UnknownHostException, SocketException {
      InetAddress localHost = InetAddress.getLocalHost();

      if (!localHost.isLoopbackAddress()) {
         return InetAddress.getLocalHost().getHostAddress();
      }

      for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
         if (!networkInterface.isUp() || networkInterface.isLoopback()) {
            continue;
         }
         for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
            if (!address.isLoopbackAddress()) {
               return address.getHostAddress();
            }
         }
      }

      return null;
   }
}
