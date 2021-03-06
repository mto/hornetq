/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.integration;

import java.util.ArrayList;
import java.util.List;

import org.hornetq.core.server.management.Notification;
import org.hornetq.core.server.management.NotificationListener;
import org.hornetq.core.server.management.NotificationService;

/**
 * A SimpleNotificationService
 *
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil
 *
 *
 */
public class SimpleNotificationService implements NotificationService
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private final List<NotificationListener> listeners = new ArrayList<NotificationListener>();

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // NotificationService implementation ----------------------------

   public void addNotificationListener(final NotificationListener listener)
   {
      listeners.add(listener);
   }

   public void enableNotifications(final boolean enable)
   {
   }

   public void removeNotificationListener(final NotificationListener listener)
   {
      listeners.remove(listener);
   }

   public void sendNotification(final Notification notification) throws Exception
   {
      for (NotificationListener listener : listeners)
      {
         listener.onNotification(notification);
      }
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   public static class Listener implements NotificationListener
   {

      private final List<Notification> notifications = new ArrayList<Notification>();

      public void onNotification(final Notification notification)
      {
         System.out.println(">>>>>>>>" + notification);
         notifications.add(notification);
      }

      public List<Notification> getNotifications()
      {
         return notifications;
      }

   }
}
