/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gtully.jms;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThroughPut {

    private static final Logger LOG = LoggerFactory.getLogger(ThroughPut.class);

    public static final int payload = 64 * 1024;
    public static final int ioBuffer = 2 * payload;
    public static final int socketBuffer = 64 * payload;
    private static String protocol = "amqp";
    private static int size = 1024 * 10;
    private static int count = 100000;
    private static long sleep;
    private static String destination = "TestQueue";
    private static String brokerUrl;
    private static String user;
    private static String password;
    private static String clientId;
    private static boolean persistent = true;

    private String payloadString;
    private final int parallelProducer = 1;
    private final int parallelConsumer = 1;
    private final Vector<Exception> exceptions = new Vector<Exception>();
    private ConnectionFactory factory;

    public static void main(String[] args) throws Exception {

        processArgs(args);

        ThroughPut throughPut = new ThroughPut();
        throughPut.produceConsume();
    }

    public void produceConsume() throws Exception {
        this.factory = createConnectionFactory(protocol);
        this.payloadString = new String(new byte[size]);

        final AtomicLong sharedSendCount = new AtomicLong(count);
        final AtomicLong sharedReceiveCount = new AtomicLong(count);

        long start = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(parallelConsumer + parallelProducer);

        for (int i = 0; i < parallelConsumer; i++) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        consumeMessages(sharedReceiveCount);
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                }
            });
        }

        //TimeUnit.SECONDS.sleep(1);
        for (int i = 0; i < parallelProducer; i++) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        publishMessages(sharedSendCount);
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.MINUTES);

        for (Exception ex : exceptions) {
            LOG.error("unexpected: " + ex);
        }
        double duration = System.currentTimeMillis() - start;
        LOG.info("Duration:            " + duration + "ms");
        LOG.info("Rate:                " + (count * 1000 / duration) + "m/s");
    }

    private ConnectionFactory createConnectionFactory(String protocol) {
        ConnectionFactory connectionFactory = null;
        if ("amqp".equals(protocol)) {
            connectionFactory = new io.hawtjms.jms.JmsConnectionFactory(brokerUrl);
        } else {
            connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        }
        return connectionFactory;
    }

    private void consumeMessages(AtomicLong count) throws Exception {
        Connection connection = factory.createConnection(user, password);
        connection.start();
        Session session = connection.createSession(false, ActiveMQSession.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(destination);
        MessageConsumer consumer = session.createConsumer(queue);
        while (count.decrementAndGet() > 0) {
            consumer.receive(10000);
        }
        consumer.close();
    }

    private void publishMessages(AtomicLong count) throws Exception {
        Connection connection = factory.createConnection(user, password);
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(destination);

        MessageProducer producer = session.createProducer(queue);
        if (!persistent) {
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        }
        Message message = session.createBytesMessage();
        ((BytesMessage) message).writeBytes(payloadString.getBytes());

        while (count.getAndDecrement() > 0) {
            producer.send(message);
        }
        producer.close();
        connection.close();
    }

    private static void processArgs(String[] args) {
        LinkedList<String> arg1 = new LinkedList<String>(Arrays.asList(args));
        ThroughPut.protocol = shift(arg1);
        while (!arg1.isEmpty()) {
            try {
                String arg = arg1.removeFirst();
                if ("--size".equals(arg)) {
                    ThroughPut.size = Integer.parseInt(shift(arg1));
                } else if ("--count".equals(arg)) {
                    ThroughPut.count = Integer.parseInt(shift(arg1));
                } else if ("--sleep".equals(arg)) {
                    ThroughPut.sleep = Integer.parseInt(shift(arg1));
                } else if ("--destination".equals(arg)) {
                    ThroughPut.destination = shift(arg1);
                } else if ("--brokerUrl".equals(arg)) {
                    ThroughPut.brokerUrl = shift(arg1);
                } else if ("--user".equals(arg)) {
                    ThroughPut.user = shift(arg1);
                } else if ("--password".equals(arg)) {
                    ThroughPut.password = shift(arg1);
                } else if ("--clientId".equals(arg)) {
                    ThroughPut.clientId = shift(arg1);
                } else if ("--persistent".equals(arg)) {
                    ThroughPut.persistent = Boolean.valueOf(shift(arg1)).booleanValue();
                } else {
                    System.err.println("Invalid usage: unknown option: " + arg);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid usage: argument not a number");
            }
        }
    }

    private static String shift(LinkedList<String> argl) {
        if (argl.isEmpty()) {
            System.out.println("Invalid usage: Missing argument");
        }
        return argl.removeFirst();
    }

}
