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

package org.hornetq.jmstests.message;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;

/**
 * A test that sends/receives object messages to the JMS provider and verifies their integrity.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class ObjectMessageTest extends MessageTestBase
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   // Public ---------------------------------------------------------------------------------------

   public void setUp() throws Exception
   {
      super.setUp();
      message = session.createObjectMessage();
   }

   public void tearDown() throws Exception
   {
      message = null;
      super.tearDown();
   }


   public void testClassLoaderIsolation() throws Exception
   {

      ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      try
      {
         queueProd.setDeliveryMode(DeliveryMode.PERSISTENT);

         ObjectMessage om = (ObjectMessage) message;

         SomeObject testObject = new SomeObject(3, 7);

         ClassLoader testClassLoader = newClassLoader(testObject.getClass());

         om.setObject(testObject);

         queueProd.send(message);

         Thread.currentThread().setContextClassLoader(testClassLoader);

         ObjectMessage r = (ObjectMessage) queueCons.receive();

         Object testObject2 = r.getObject();

         assertEquals("org.hornetq.jmstests.message.SomeObject",
            testObject2.getClass().getName());
         assertNotSame(testObject, testObject2);
         assertNotSame(testObject.getClass(), testObject2.getClass());
         assertNotSame(testObject.getClass().getClassLoader(),
            testObject2.getClass().getClassLoader());
         assertSame(testClassLoader,
            testObject2.getClass().getClassLoader());
      }
      finally
      {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }

   }


   public void testVectorOnObjectMessage() throws Exception
   {
      java.util.Vector vectorOnMessage = new java.util.Vector();
      vectorOnMessage.add("world!");
      ((ObjectMessage)message).setObject(vectorOnMessage);

      queueProd.send(message);

      ObjectMessage r = (ObjectMessage) queueCons.receive(5000);
      assertNotNull(r);

      java.util.Vector v2 = (java.util.Vector) r.getObject();

      assertEquals(vectorOnMessage.get(0), v2.get(0));
   }
   
   public void testObjectIsolation() throws Exception
   {
      ObjectMessage msgTest = session.createObjectMessage();
      ArrayList list = new ArrayList();
      list.add("hello");
      msgTest.setObject(list);
      
      list.clear();
      
      list = (ArrayList) msgTest.getObject();
      
      assertEquals(1, list.size());
      assertEquals("hello", list.get(0));
      
      list.add("hello2");
      
      msgTest.setObject(list);
      
      list.clear();
      
      list = (ArrayList) msgTest.getObject();
      
      assertEquals(2, list.size());
      assertEquals("hello", list.get(0));
      assertEquals("hello2", list.get(1));
      
      msgTest.setObject(list);
      list.add("hello3");
      msgTest.setObject(list);
      
      list = (ArrayList) msgTest.getObject();
      assertEquals(3, list.size());
      assertEquals("hello", list.get(0));
      assertEquals("hello2", list.get(1));
      assertEquals("hello3", list.get(2));
      
      list = (ArrayList) msgTest.getObject();
      
      list.clear();
      
      queueProd.send(msgTest);
      
      msgTest = (ObjectMessage) queueCons.receive(5000);
      
      list = (ArrayList) msgTest.getObject();
      
      assertEquals(3, list.size());
      assertEquals("hello", list.get(0));
      assertEquals("hello2", list.get(1));
      assertEquals("hello3", list.get(2));
      
   }
   
   public void testReadOnEmptyObjectMessage() throws Exception
   {
      ObjectMessage obm = (ObjectMessage) message;
      assertNull(obm.getObject());
      
      queueProd.send(message);
      ObjectMessage r = (ObjectMessage) queueCons.receive();
      
      assertNull(r.getObject());
      
   }
   
   // Protected ------------------------------------------------------------------------------------

   protected void prepareMessage(Message m) throws JMSException
   {
      super.prepareMessage(m);

      ObjectMessage om = (ObjectMessage)m;
      om.setObject("this is the serializable object");

   }

   protected void assertEquivalent(Message m, int mode, boolean redelivery) throws JMSException
   {
      super.assertEquivalent(m, mode, redelivery);

      ObjectMessage om = (ObjectMessage)m;
      assertEquals("this is the serializable object", om.getObject());
   }

   protected static ClassLoader newClassLoader(Class anyUserClass) throws Exception
   {
      URL classLocation = anyUserClass.getProtectionDomain().getCodeSource().getLocation();
      StringTokenizer tokenString = new StringTokenizer(System.getProperty("java.class.path"),
         File.pathSeparator);
      String pathIgnore = System.getProperty("java.home");
      if (pathIgnore == null)
      {
         pathIgnore = classLocation.toString();
      }

      ArrayList urls = new ArrayList();
      while (tokenString.hasMoreElements())
      {
         String value = tokenString.nextToken();
         URL itemLocation = new File(value).toURI().toURL();
         if (!itemLocation.equals(classLocation) &&
                      itemLocation.toString().indexOf(pathIgnore) >= 0)
         {
            urls.add(itemLocation);
         }
      }

      URL[] urlArray = (URL[]) urls.toArray(new URL[urls.size()]);

      ClassLoader masterClassLoader = URLClassLoader.newInstance(urlArray, null);


      ClassLoader appClassLoader = URLClassLoader.newInstance(new URL[]{classLocation},
                                      masterClassLoader);

      return appClassLoader;
   }

}