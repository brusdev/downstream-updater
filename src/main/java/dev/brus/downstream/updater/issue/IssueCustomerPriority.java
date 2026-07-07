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

package dev.brus.downstream.updater.issue;

public enum IssueCustomerPriority {
   SCHEDULED,
   LOW,
   MEDIUM,
   HIGH,
   URGENT,
   NONE;

   private static final String SCHEDULED_VALUE = "rfv1:k1:029013018015014031022015:FjFI4vRDXIGtYHewzEzZdU1dgdwkKuaL1kpsV+8qs4VkM69Q08g3azY9gWeoEMzDERoIT30mTRjYQim9j5WlsA==";
   private static final String LOW_VALUE = "rfv1:k1:022025033000000000000000:FjFI4vRDXIGtYHewzEzZdUHDacvqZz7pekO+Hnpzcze6vqvOzEYh5Uj0dJZw+YCxmadTIQSKIHMcnEWoJLyp1w==";
   private static final String MEDIUM_VALUE = "rfv1:k1:023015014019031023000000:FjFI4vRDXIGtYHewzEzZdb6IxEVHUNEINgDkDdO4AwBzgalldVn46GYLZSUiLV1o2yxsXEXLux6YXHFlOy3Wxg==";
   private static final String HIGH_VALUE = "rfv1:k1:018019017018000000000000:FjFI4vRDXIGtYHewzEzZdcUDPvFtqjnYfpSayidPxjL7eU6jjw1f+AQLiyxKj9hyblHKYFjSEXUFHCdp+WYWzg==";
   private static final String URGENT_VALUE = "rfv1:k1:031028017015024030000000:FjFI4vRDXIGtYHewzEzZdTBVm8yCrS9g/ezwGxGLzXULcq3nw5aAoUJyA7kioImDBX+ewWMMYhCbOtyhe/yGSQ==";

   public static IssueCustomerPriority fromName(String name) {
      if (name == null) {
         return null;
      }
      return IssueCustomerPriority.valueOf(name.toUpperCase());
   }

   public static IssueCustomerPriority fromValue(String value) {
      if (value == null) {
         return null;
      }

      if (SCHEDULED_VALUE.equals(value)) {
         return SCHEDULED;
      } else if (LOW_VALUE.equals(value)) {
         return LOW;
      } else if (MEDIUM_VALUE.equals(value)) {
         return MEDIUM;
      } else if (HIGH_VALUE.equals(value)) {
         return HIGH;
      } else if (URGENT_VALUE.equals(value)) {
         return URGENT;
      } else {
         throw new IllegalArgumentException("Customer priority value not supported: " + value);
      }
   }
}
