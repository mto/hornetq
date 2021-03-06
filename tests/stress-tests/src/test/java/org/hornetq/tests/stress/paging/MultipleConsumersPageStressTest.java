/*
 * Copyright 2010 Red Hat, Inc.
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

package org.hornetq.tests.stress.paging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.impl.QueueImpl;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.tests.unit.UnitTestLogger;
import org.hornetq.tests.util.ServiceTestBase;

/**
 * A MultipleConsumersPageStressTest
 *
 * @author clebertsuconic
 *
 *
 */
public class MultipleConsumersPageStressTest extends ServiceTestBase
{

   private final UnitTestLogger log = UnitTestLogger.LOGGER;

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private final static int TIME_TO_RUN = 60 * 1000;

   private static final SimpleString ADDRESS = new SimpleString("page-adr");

   private int numberOfProducers;

   private int numberOfConsumers;

   private QueueImpl pagedServerQueue;

   private boolean shareConnectionFactory = true;

   private boolean openConsumerOnEveryLoop = true;

   private HornetQServer messagingService;

   private ServerLocator sharedLocator;

   private ClientSessionFactory sharedSf;

   final AtomicInteger messagesAvailable = new AtomicInteger(0);

   private volatile boolean runningProducer = true;

   private volatile boolean runningConsumer = true;

   ArrayList<TestProducer> producers = new ArrayList<TestProducer>();

   ArrayList<TestConsumer> consumers = new ArrayList<TestConsumer>();

   ArrayList<Throwable> exceptions = new ArrayList<Throwable>();

   public void testOpenConsumerEveryTimeDefaultFlowControl0() throws Throwable
   {
      shareConnectionFactory = true;
      openConsumerOnEveryLoop = true;
      numberOfProducers = 1;
      numberOfConsumers = 1;

      sharedLocator = createInVMNonHALocator();
      sharedLocator.setConsumerWindowSize(0);

      sharedSf = sharedLocator.createSessionFactory();

      internalMultipleConsumers();
   }

   @Override
   public void setUp() throws Exception
   {
      super.setUp();
      Configuration config = createDefaultConfig();

      HashMap<String, AddressSettings> settings = new HashMap<String, AddressSettings>();

      messagingService = createServer(true, config, 10024, 200024, settings);
      messagingService.start();

      pagedServerQueue = (QueueImpl)messagingService.createQueue(ADDRESS, ADDRESS, null, true, false);

   }

   @Override
   public void tearDown() throws Exception
   {
      for (Tester tst : producers)
      {
         tst.close();
      }
      for (Tester tst : consumers)
      {
         tst.close();
      }
      sharedSf.close();
      sharedLocator.close();
      messagingService.stop();
      super.tearDown();
   }

   public void testOpenConsumerEveryTimeDefaultFlowControl() throws Throwable
   {
      shareConnectionFactory = true;
      openConsumerOnEveryLoop = true;
      numberOfProducers = 1;
      numberOfConsumers = 1;

      sharedLocator = createInVMNonHALocator();

      sharedSf = sharedLocator.createSessionFactory();

      System.out.println(pagedServerQueue.debug());

      internalMultipleConsumers();

   }

   public void testReuseConsumersFlowControl0() throws Throwable
   {
      shareConnectionFactory = true;
      openConsumerOnEveryLoop = false;
      numberOfProducers = 1;
      numberOfConsumers = 1;

      sharedLocator = createInVMNonHALocator();
      sharedLocator.setConsumerWindowSize(0);

      sharedSf = sharedLocator.createSessionFactory();

      try
      {
         internalMultipleConsumers();
      }
      catch (Throwable e)
      {
         TestConsumer tstConsumer = consumers.get(0);
         System.out.println("first retry: " + tstConsumer.consumer.receive(1000));

         System.out.println(pagedServerQueue.debug());

         pagedServerQueue.forceDelivery();
         System.out.println("Second retry: " + tstConsumer.consumer.receive(1000));

         System.out.println(pagedServerQueue.debug());


         tstConsumer.session.commit();
         System.out.println("Third retry:" + tstConsumer.consumer.receive(1000));

         tstConsumer.close();

         ClientSession session = sharedSf.createSession();
         session.start();
         ClientConsumer consumer = session.createConsumer(ADDRESS);

         pagedServerQueue.forceDelivery();

         System.out.println("Fourth retry: " + consumer.receive(1000));

         System.out.println(pagedServerQueue.debug());
         
         throw e;
      }

   }

   public void internalMultipleConsumers() throws Throwable
   {
      for (int i = 0; i < numberOfProducers; i++)
      {
         producers.add(new TestProducer());
      }

      for (int i = 0; i < numberOfConsumers; i++)
      {
         consumers.add(new TestConsumer());
      }

      for (Tester test : producers)
      {
         test.start();
      }

      Thread.sleep(2000);

      for (Tester test : consumers)
      {
         test.start();
      }

      for (Tester test : consumers)
      {
         test.join();
      }

      runningProducer = false;

      for (Tester test : producers)
      {
         test.join();
      }

      for (Throwable e : exceptions)
      {
         throw e;
      }

   }

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   abstract class Tester extends Thread
   {
      Random random = new Random();

      public abstract void close();

      protected abstract boolean enabled();

      protected void exceptionHappened(final Throwable e)
      {
         runningConsumer = false;
         runningProducer = false;
         e.printStackTrace();
         exceptions.add(e);
      }

      public int getNumberOfMessages() throws Exception
      {
         int numberOfMessages = random.nextInt(20);
         if (numberOfMessages <= 0)
         {
            return 1;
         }
         else
         {
            return numberOfMessages;
         }
      }
   }

   class TestConsumer extends Tester
   {

      public ClientConsumer consumer = null;

      public ClientSession session = null;

      public ServerLocator locator = null;

      public ClientSessionFactory sf = null;

      @Override
      public void close()
      {
         try
         {

            if (!openConsumerOnEveryLoop)
            {
               consumer.close();
            }
            session.rollback();
            session.close();

            if (!shareConnectionFactory)
            {
               sf.close();
               locator.close();
            }
         }
         catch (Exception ignored)
         {
         }

      }

      @Override
      protected boolean enabled()
      {
         return runningConsumer;
      }

      @Override
      public int getNumberOfMessages() throws Exception
      {
         while (enabled())
         {
            int numberOfMessages = super.getNumberOfMessages();

            int resultMessages = messagesAvailable.addAndGet(-numberOfMessages);

            if (resultMessages < 0)
            {
               messagesAvailable.addAndGet(-numberOfMessages);
               numberOfMessages = 0;
               System.out.println("Negative, giving a little wait");
               Thread.sleep(1000);
            }

            if (numberOfMessages > 0)
            {
               return numberOfMessages;
            }
         }

         return 0;
      }

      @Override
      public void run()
      {
         try
         {
            if (shareConnectionFactory)
            {
               session = sharedSf.createSession(false, false);
            }
            else
            {
               locator = createInVMNonHALocator();
               sf = locator.createSessionFactory();
               session = sf.createSession(false, false);
            }

            long timeOut = System.currentTimeMillis() + MultipleConsumersPageStressTest.TIME_TO_RUN;

            session.start();

            if (!openConsumerOnEveryLoop)
            {
               consumer = session.createConsumer(MultipleConsumersPageStressTest.ADDRESS);
            }

            int count = 0;

            while (enabled() && timeOut > System.currentTimeMillis())
            {

               if (openConsumerOnEveryLoop)
               {
                  consumer = session.createConsumer(MultipleConsumersPageStressTest.ADDRESS);
               }

               int numberOfMessages = getNumberOfMessages();

               for (int i = 0; i < numberOfMessages; i++)
               {
                  ClientMessage msg = consumer.receive(10000);
                  if (msg == null)
                  {
                     log.warn("msg " + count +
                              " was null, currentBatchSize=" +
                              numberOfMessages +
                              ", current msg being read=" +
                              i);
                  }
                  Assert.assertNotNull("msg " + count +
                                       " was null, currentBatchSize=" +
                                       numberOfMessages +
                                       ", current msg being read=" +
                                       i, msg);

                  if (numberOfConsumers == 1 && numberOfProducers == 1)
                  {
                     Assert.assertEquals(count, msg.getIntProperty("count").intValue());
                  }

                  count++;

                  msg.acknowledge();
               }

               session.commit();

               if (openConsumerOnEveryLoop)
               {
                  consumer.close();
               }

            }
         }
         catch (Throwable e)
         {
            exceptionHappened(e);
         }

      }
   }

   class TestProducer extends Tester
   {
      ClientSession session = null;

      ClientSessionFactory sf = null;

      ServerLocator locator = null;

      @Override
      public void close()
      {
         try
         {
            session.rollback();
            session.close();
         }
         catch (Exception ignored)
         {
         }

      }

      @Override
      protected boolean enabled()
      {
         return runningProducer;
      }

      @Override
      public void run()
      {
         try
         {
            if (shareConnectionFactory)
            {
               session = sharedSf.createSession(false, false);
            }
            else
            {
               locator = createInVMNonHALocator();
               sf = locator.createSessionFactory();
               session = sf.createSession(false, false);
            }

            ClientProducer prod = session.createProducer(MultipleConsumersPageStressTest.ADDRESS);

            int count = 0;

            while (enabled())
            {
               int numberOfMessages = getNumberOfMessages();

               for (int i = 0; i < numberOfMessages; i++)
               {
                  ClientMessage msg = session.createMessage(true);
                  msg.putStringProperty("Test", "This is a simple test");
                  msg.putIntProperty("count", count++);
                  prod.send(msg);
               }

               messagesAvailable.addAndGet(numberOfMessages);
               session.commit();
            }
         }
         catch (Throwable e)
         {
            exceptionHappened(e);
         }
      }
   }

}
