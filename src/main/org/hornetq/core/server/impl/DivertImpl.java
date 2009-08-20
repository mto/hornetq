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

package org.hornetq.core.server.impl;

import static org.hornetq.core.message.impl.MessageImpl.HDR_ORIGINAL_DESTINATION;

import org.hornetq.core.filter.Filter;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.paging.PagingManager;
import org.hornetq.core.paging.PagingStore;
import org.hornetq.core.persistence.StorageManager;
import org.hornetq.core.postoffice.PostOffice;
import org.hornetq.core.server.Divert;
import org.hornetq.core.server.ServerMessage;
import org.hornetq.core.server.cluster.Transformer;
import org.hornetq.core.transaction.Transaction;
import org.hornetq.utils.SimpleString;

/**
 * A DivertImpl simply diverts a message to a different forwardAddress
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 19 Dec 2008 10:57:49
 *
 *
 */
public class DivertImpl implements Divert
{
   private static final Logger log = Logger.getLogger(DivertImpl.class);

   private final PostOffice postOffice;

   private final SimpleString forwardAddress;

   private final SimpleString uniqueName;

   private final SimpleString routingName;

   private final boolean exclusive;

   private final Filter filter;

   private final Transformer transformer;
   
   private final PagingManager pagingManager;
   
   private final StorageManager storageManager;

   public DivertImpl(final SimpleString forwardAddress,
                     final SimpleString uniqueName,
                     final SimpleString routingName,
                     final boolean exclusive,
                     final Filter filter,
                     final Transformer transformer,
                     final PostOffice postOffice,
                     final PagingManager pagingManager,
                     final StorageManager storageManager)
   {
      this.forwardAddress = forwardAddress;

      this.uniqueName = uniqueName;

      this.routingName = routingName;

      this.exclusive = exclusive;

      this.filter = filter;

      this.transformer = transformer;

      this.postOffice = postOffice;
      
      this.pagingManager = pagingManager;
      
      this.storageManager = storageManager;
   }

   public void preroute(final ServerMessage message, final Transaction tx) throws Exception
   {
      //We need to increment ref count here to ensure that the message doesn't get stored, deleted and stored again in a single route which
      //can occur if the message is routed to a queue, then acked before it's routed here
      
      //TODO - combine with similar code in QueueImpl.accept()
      
      int count = message.incrementRefCount();
      
      if (count == 1)
      {
         PagingStore store = pagingManager.getPageStore(message.getDestination());
         
         store.addSize(message.getMemoryEstimate());
      }
    
      if (message.isDurable())
      {
         message.incrementDurableRefCount();
      }     
   }

   public void route(ServerMessage message, final Transaction tx) throws Exception
   {      
      SimpleString originalDestination = message.getDestination();

      message.setDestination(forwardAddress);

      message.putStringProperty(HDR_ORIGINAL_DESTINATION, originalDestination);

      if (transformer != null)
      {
         message = transformer.transform(message);
      }

      postOffice.route(message, tx);
      
      //Decrement the ref count here - and delete the message if necessary
      
      //TODO combine this with code in QueueImpl::postAcknowledge
      
      if (message.isDurable())
      {
         int count = message.decrementDurableRefCount();

         if (count == 0)
         {
            storageManager.deleteMessage(message.getMessageID());
         }
      }

      // TODO: We could optimize this by storing the paging-store for the address on the Queue. We would need to know
      // the Address for the Queue
      PagingStore store = pagingManager.getPageStore(message.getDestination());

      if (message.decrementRefCount() == 0)
      {
         store.addSize(-message.getMemoryEstimate());         
      }
   }

   public SimpleString getRoutingName()
   {
      return routingName;
   }

   public SimpleString getUniqueName()
   {
      return uniqueName;
   }

   public boolean isExclusive()
   {
      return exclusive;
   }
   
   public Filter getFilter()
   {
      return filter;
   }
}