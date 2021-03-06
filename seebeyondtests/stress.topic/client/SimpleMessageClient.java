/**
 * Copyright (c) 2004-2005 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.sun.s1peqe.connector.mq.simplestress.client;

import javax.jms.*;
import javax.naming.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Simple stress test with a Topic. The test starts NUM_CLIENT threads. Each thread sends
 * NUM_CYCLES messages to a Topic. An MDB consumes these messages concurrently
 * and responds with a reply Message to a ReplyQueue. While the threads are being exercised,
 * an attempt to read NUM_CLIENT*NUM_CYCLES messages is performed. An attempt to
 * read an extra message is also done to ascertain that no more than
 * NUM_CLIENT*NUM_CYCLES messages are available.
 */

public class SimpleMessageClient implements Runnable{
    static int NUM_CLIENTS = 2;
    static int NUM_CYCLES = 2;
    static int TIME_OUT = 60000;
    static long MDB_SLEEP_TIME = 2000;
    static boolean debug = true;
    private int id =0;

    public SimpleMessageClient(int i) {
        this.id = i;
    }

    public static void main(String[] args) {
        /**
	 * Start the threads that will send messages to MDB
	 */
        ArrayList al = new ArrayList();
        try {
            for (int i =0; i < NUM_CLIENTS; i++) {
                Thread client = new Thread(new SimpleMessageClient(i));
		al.add(client);
                client.start();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        Context                 jndiContext = null;
        QueueConnectionFactory  queueConnectionFactory = null;
        QueueConnection         queueConnection = null;
        QueueSession            queueSession = null;
        Queue                   queue = null;
        QueueReceiver           queueReceiver = null;
        TextMessage             message = null;
        Topic                   deadMessageTopic = null;
        TopicSubscriber           dmdSubscriber = null;
        TopicConnectionFactory  dmdConnectionFactory = null;
        TopicConnection         dmdConnection = null;
        TopicSession            dmdSession = null;

        try {
            jndiContext = new InitialContext();
            queueConnectionFactory = (QueueConnectionFactory)
                jndiContext.lookup
                ("java:comp/env/jms/QCFactory");
            dmdConnectionFactory = (TopicConnectionFactory)
                jndiContext.lookup
                ("java:comp/env/jms/TCFactory");
            queue = (Queue) jndiContext.lookup("java:comp/env/jms/clientQueue");
            deadMessageTopic = (Topic) jndiContext.lookup("java:comp/env/jms/deadMessageTopic");

            queueConnection =
                queueConnectionFactory.createQueueConnection();
            queueSession =
                queueConnection.createQueueSession(false,
                    Session.AUTO_ACKNOWLEDGE);
            dmdConnection =
                dmdConnectionFactory.createTopicConnection();
            dmdSession =
                dmdConnection.createTopicSession(false,
                    Session.AUTO_ACKNOWLEDGE);
            queueConnection.start();
            dmdConnection.start();
            queueReceiver = queueSession.createReceiver(queue);
            dmdSubscriber = dmdSession.createSubscriber(deadMessageTopic);

	    HashMap map = new HashMap();

            long startTime = System.currentTimeMillis();
            boolean pass = true;
	    //
	    // Receives all the messages and keep in the data structure
	    //
            for (int i =0; i < ((NUM_CLIENTS * NUM_CYCLES)/2); i++) {
                TextMessage msg = (TextMessage) queueReceiver.receive(TIME_OUT);
                System.out.print("." + i);
                if (msg == null) {
                    pass = false;
                    System.out.println("Reecived only " + i + " messages");
                    break;
                }
		Integer id = new Integer(msg.getIntProperty("replyid"));
		if (map.containsKey(id)) {
		    pass = false;
		    debug("Duplicate :" + id);
		}
		map.put(id, msg.getText());
            }
            long totalTime = System.currentTimeMillis() - startTime;
	    System.out.println("Received the messages from the actual reply " +
                "queue in :" + totalTime + " milliseconds");
	    System.out.println("------------------------------------------------------");

            //Now attempting to read the rest from the DMQ
            for (int i =0; i < ((NUM_CLIENTS * NUM_CYCLES)/2); i++) {
                TextMessage msg = (TextMessage) dmdSubscriber.receive(TIME_OUT);
                System.out.print("." + i);
                if (msg == null) {
                    pass = false;
                    System.out.println("Reecived only " + i + " messages");
                    break;
                }
                Integer id = new Integer(msg.getIntProperty("id"));
                if (map.containsKey(id)) {
                    pass = false;
                    debug("Duplicate :" + id);
                }
                map.put(id, msg.getText());
            }
            totalTime = System.currentTimeMillis() - startTime;
            if (pass) {
	            System.out.println("Received the other half the messages from the " +
                    "DMD queue in :" + totalTime + " milliseconds");
            } else {
		System.out.println("Received less that expected messages from DMD");
            }
            System.out.println("------------------------------------------------------");
        

	    // Try to receive one more message than expected.
            System.out.println("Try to receive one more message than expected.");
            TextMessage msg = (TextMessage) queueReceiver.receive(TIME_OUT);
            
	    if (msg != null) {
	       pass = false;
	       System.out.println("Received more than expected number of " +
                "messages :" + msg.getText());
	    }

        
            if (pass) {
                System.out.println("Concurrent message delivery test - Topic " +
                        "Stress : PASS");
            } else {
                System.out.println("Concurrent message delivery test - Topic " +
                        "Stress : FAIL");
	    }
        }catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Concurrent message delivery test - Topic Stress " +
                    ": FAIL");
        }finally {
	    for (int i=0; i <al.size(); i++) {
	       Thread client = (Thread) al.get(i);
	       try {
	          client.join();
	       } catch (Exception e) {
	          System.out.println(e.getMessage());
	       }
	    }
            System.exit(0);
        }
    }

    public void run() {

        Context                 jndiContext = null;
        TopicConnectionFactory  topicConnectionFactory = null;
        TopicConnection         topicConnection = null;
        TopicSession            topicSession = null;
        Topic                   topic = null;
        TopicPublisher             topicPublisher = null;
        TextMessage             message = null;

        try {
            jndiContext = new InitialContext();
            topicConnectionFactory = 
                (TopicConnectionFactory)jndiContext.lookup(
                                    "java:comp/env/jms/TCFactory");
            topic = (Topic) jndiContext.lookup("java:comp/env/jms/SampleTopic");

	    int startId = id * NUM_CYCLES;
	    int endId = (id * NUM_CYCLES) + NUM_CYCLES;
	    for (int i= startId;i < endId; i ++) {
                try {
                    topicConnection =
                    topicConnectionFactory.createTopicConnection();
                    topicSession =
                    topicConnection.createTopicSession(false,
                    Session.AUTO_ACKNOWLEDGE);
                    topicConnection.start();
                    topicPublisher = topicSession.createPublisher(topic);
                    message = topicSession.createTextMessage();
                    message.setText("CLIENT");
	            message.setIntProperty("id",i);
	            message.setLongProperty("sleeptime",MDB_SLEEP_TIME);
                    topicPublisher.publish(message);
		    debug("Send the message :" + message.getIntProperty("id") 
                            + ":" + message.getText());
                } catch (Exception e) {
                    System.out.println("Exception occurred: " + e.toString());
                } finally {
                    if (topicConnection != null) {
                        try {
                            topicConnection.close();
                        } catch (JMSException e) {}
                    } // if
               } // finally
            }
        } catch (Throwable e) {
            System.out.println("Exception occurred: " + e.toString());
        } // finally
    } // main

    static void debug(String msg) {
        if (debug) {
	   System.out.println(msg);
	}
    }
} // class

