package dev.brus.midstream.updater.util;

import dev.brus.downstream.updater.util.ReleaseVersion;
import org.junit.Assert;
import org.junit.Test;

public class ReleaseVersionTest {

   @Test
   public void testReleaseVersionToString() {
      testReleaseVersionToString("7.10.0.CR1");
      testReleaseVersionToString("7.10.0.OPR.1.CR1");
   }

   private void testReleaseVersionToString(String releaseVersionText) {
      ReleaseVersion releaseVersion = ReleaseVersion.fromString(releaseVersionText);
      Assert.assertEquals(releaseVersionText, releaseVersion.toString());
   }

   @Test
   public void testCompare() {
      Assert.assertEquals(0, ReleaseVersion.compare("7.10.0.CR1", "7.10.0.CR1"));
      Assert.assertTrue(ReleaseVersion.compare("7.10.1.CR1", "7.10.0.CR1") > 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.CR1", "7.10.1.CR1") < 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.CR2", "7.10.0.CR1") > 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.CR1", "7.10.0.CR2") < 0);
      Assert.assertEquals(0, ReleaseVersion.compare("7.10.0.OPR.1.CR1", "7.10.0.OPR.1.CR1"));
      Assert.assertTrue(ReleaseVersion.compare("7.10.1.OPR.1.CR1", "7.10.0.OPR.1.CR1") > 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.OPR.1.CR1", "7.10.1.OPR.1.CR1") < 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.OPR.2.CR1", "7.10.0.OPR.1.CR1") > 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.OPR.1.CR1", "7.10.0.OPR.2.CR1") < 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.OPR.1.CR2", "7.10.0.OPR.1.CR1") > 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.OPR.1.CR1", "7.10.0.OPR.1.CR2") < 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.1.CR1", "7.10.0.OPR.1.CR1") > 0);
      Assert.assertTrue(ReleaseVersion.compare("7.10.0.CR1", "7.10.1.OPR.1.CR1") < 0);
   }

   @Test
   public void testCompareWithoutCandidateTo() {
      Assert.assertEquals(0, ReleaseVersion.fromString("7.10.0.CR1").compareWithoutCandidateTo(ReleaseVersion.fromString("7.10.0.CR1")));
   }

   @Test
   public void testGetQualifierPrefix() {
      Assert.assertEquals("OPR.", ReleaseVersion.fromString("7.10.0.OPR.1.CR1").getQualifierPrefix());
      Assert.assertNull(ReleaseVersion.fromString("7.10.0.CR1").getQualifierPrefix());
   }
}
