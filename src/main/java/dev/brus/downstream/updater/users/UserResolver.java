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

package dev.brus.downstream.updater.users;

public class UserResolver {

   private User defaultUser;
   private User[] users;

   public User[] getUsers() {
      return users;
   }

   public UserResolver(User[] users) {
      this.users = users;
   }

   public User getDefaultUser() {
      return defaultUser;
   }

   public UserResolver setDefaultUser(User defaultUser) {
      this.defaultUser = defaultUser;
      return this;
   }

   public User getUserFromUsername(String username) {
      for (User user : users) {
         if (user.getUsername().equals(username)) {
            return user;
         }
      }

      return null;
   }

   public User getUserFromUpstreamUsername(String username) {
      for (User user : users) {
         if (user.getUpstreamUsername().equals(username)) {
            return user;
         }
      }

      return null;
   }

   public User getUserFromDownstreamUsername(String username) {
      for (User user : users) {
         if (user.getDownstreamUsername().equals(username)) {
            return user;
         }
      }

      return null;
   }

   public User getUserFromEmailAddress(String emailAddress) {
      for (User user : users) {
         for (String userEmailAddress : user.getEmailAddresses()) {
            if (userEmailAddress.equals(emailAddress)) {
               return user;
            }
         }
      }

      return null;
   }
}
