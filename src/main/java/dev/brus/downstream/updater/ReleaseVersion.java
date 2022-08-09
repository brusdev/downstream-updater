/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.brus.downstream.updater;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReleaseVersion implements Comparable<ReleaseVersion> {
   private final static Pattern versionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+).([0-9A-Za-z]+)");

   private int major;
   private int minor;
   private int patch;
   private String qualifier;

   public int getMajor() {
      return major;
   }

   public int getMinor() {
      return minor;
   }

   public int getPatch() {
      return patch;
   }

   public String getQualifier() {
      return qualifier;
   }

   public ReleaseVersion(String release) {
      Matcher releaseVersionMatcher = versionPattern.matcher(release);
      if (!releaseVersionMatcher.find()) {
         throw new IllegalArgumentException("Invalid release: " + release);
      }

      major = Integer.parseInt(releaseVersionMatcher.group(1));
      minor = Integer.parseInt(releaseVersionMatcher.group(2));
      patch = Integer.parseInt(releaseVersionMatcher.group(3));
      qualifier = releaseVersionMatcher.group(4);
   }

   public static int compare(String releaseX, String releaseY) {
      if (Objects.equals(releaseX, releaseY)) {
         return 0;
      } else if (releaseX == null || releaseX.isEmpty()) {
         return -1;
      } else if (releaseY == null || releaseY.isEmpty()) {
         return 1;
      } else {
         ReleaseVersion releaseVersionX = new ReleaseVersion(releaseX);
         ReleaseVersion releaseVersionY = new ReleaseVersion(releaseY);

         return releaseVersionX.compareTo(releaseVersionY);
      }
   }

   public static int compareWithoutQualifier(String releaseX, String releaseY) {
      if (Objects.equals(releaseX, releaseY)) {
         return 0;
      } else if (releaseX == null || releaseX.isEmpty()) {
         return -1;
      } else if (releaseY == null || releaseY.isEmpty()) {
         return 1;
      } else {
         ReleaseVersion releaseVersionX = new ReleaseVersion(releaseX);
         ReleaseVersion releaseVersionY = new ReleaseVersion(releaseY);

         return releaseVersionX.compareWithoutQualifierTo(releaseVersionY);
      }
   }

   @Override
   public String toString() {
      return major + "." + minor + "." + patch + "." + qualifier;
   }

   @Override
   public int compareTo(ReleaseVersion releaseVersion) {
      if (this.getMajor() == releaseVersion.getMajor() &&
         this.getMinor() == releaseVersion.getMinor() &&
         this.getPatch() == releaseVersion.getPatch() &&
         this.getQualifier().compareTo(releaseVersion.getQualifier()) == 0) {
         return 0;
      } else if (this.getMajor() > releaseVersion.getMajor() ||
         this.getMajor() == releaseVersion.getMajor() && this.getMinor() > releaseVersion.getMinor() ||
         this.getMajor() == releaseVersion.getMajor() && this.getMinor() == releaseVersion.getMinor() && this.getPatch() > releaseVersion.getPatch() ||
         this.getMajor() == releaseVersion.getMajor() && this.getMinor() == releaseVersion.getMinor() && this.getPatch() == releaseVersion.getPatch() && this.getQualifier().compareTo(releaseVersion.getQualifier()) > 0) {
         return 1;
      } else {
         return -1;
      }
   }

   public int compareWithoutQualifierTo(ReleaseVersion releaseVersion) {
      if (this.getMajor() == releaseVersion.getMajor() &&
         this.getMinor() == releaseVersion.getMinor() &&
         this.getPatch() == releaseVersion.getPatch()) {
         return 0;
      } else if (this.getMajor() > releaseVersion.getMajor() ||
         this.getMajor() == releaseVersion.getMajor() && this.getMinor() > releaseVersion.getMinor() ||
         this.getMajor() == releaseVersion.getMajor() && this.getMinor() == releaseVersion.getMinor() && this.getPatch() > releaseVersion.getPatch()) {
         return 1;
      } else {
         return -1;
      }
   }
}
