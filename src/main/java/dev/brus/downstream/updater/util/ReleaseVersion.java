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

package dev.brus.downstream.updater.util;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class ReleaseVersion implements Comparable<ReleaseVersion> {
   private final static Pattern versionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)(\\.(.*))*\\.(CR[0-9]+|GA)");

   private final int major;
   private final int minor;
   private final int patch;
   private final String qualifier;
   private final String candidate;

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

   public String getCandidate() {
      return candidate;
   }

   public ReleaseVersion(String release) {
      Matcher releaseVersionMatcher = versionPattern.matcher(release);
      if (!releaseVersionMatcher.find()) {
         throw new IllegalArgumentException("Invalid release: " + release);
      }

      this.major = Integer.parseInt(releaseVersionMatcher.group(1));
      this.minor = Integer.parseInt(releaseVersionMatcher.group(2));
      this.patch = Integer.parseInt(releaseVersionMatcher.group(3));
      this.qualifier = releaseVersionMatcher.group(5);
      this.candidate = releaseVersionMatcher.group(6);
   }

   public ReleaseVersion(int major, int minor, int patch, String qualifier, String candidate) {
      this.major = major;
      this.minor = minor;
      this.patch = patch;
      this.qualifier = qualifier;
      this.candidate = candidate;
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

   @Override
   public String toString() {
      if (qualifier == null) {
         return major + "." + minor + "." + patch + "." + candidate;
      }
      return major + "." + minor + "." + patch + "." + qualifier + "." + candidate;
   }

   @Override
   public int compareTo(ReleaseVersion releaseVersion) {
      if (this.getMajor() == releaseVersion.getMajor() &&
         this.getMinor() == releaseVersion.getMinor() &&
         this.getPatch() == releaseVersion.getPatch() &&
         Objects.equals(this.getQualifier(), releaseVersion.getQualifier()) &&
         Objects.equals(this.getCandidate(), releaseVersion.getCandidate())) {
         return 0;
      } else if ((this.getMajor() > releaseVersion.getMajor()) ||
         (this.getMajor() == releaseVersion.getMajor() && this.getMinor() > releaseVersion.getMinor()) ||
         (this.getMajor() == releaseVersion.getMajor() && this.getMinor() == releaseVersion.getMinor() &&
            this.getPatch() > releaseVersion.getPatch()) ||
         (this.getMajor() == releaseVersion.getMajor() && this.getMinor() == releaseVersion.getMinor() &&
            this.getPatch() == releaseVersion.getPatch() &&
            StringUtils.compare(this.getQualifier(), releaseVersion.getQualifier()) > 0) ||
         (this.getMajor() == releaseVersion.getMajor() && this.getMinor() == releaseVersion.getMinor() &&
            this.getPatch() == releaseVersion.getPatch() &&
            StringUtils.compare(this.getQualifier(), releaseVersion.getQualifier()) == 0 &&
            StringUtils.compare(this.getCandidate(), releaseVersion.getCandidate()) > 0)) {
         return 1;
      } else {
         return -1;
      }
   }

   public int compareWithoutCandidateTo(ReleaseVersion releaseVersion) {
      ReleaseVersion comparingReleaseVersion = new ReleaseVersion(
         releaseVersion.getMajor(),
         releaseVersion.getMinor(),
         releaseVersion.getPatch(),
         releaseVersion.getQualifier(),
         this.getCandidate());

      return compareTo(comparingReleaseVersion);
   }
}
