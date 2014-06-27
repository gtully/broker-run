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
    private static int destMod = 0;
    private static String brokerUrl;
    private static String user;
    private static String password;
    private static String clientId;
    private static boolean persistent = true;
    private static int parallelProducers = 1;
    private static int parallelConsumers = 1;

    private String payloadString;

    private final Vector<Exception> exceptions = new Vector<Exception>();
    private ConnectionFactory factory;
    final AtomicLong sharedReceivedCount = new AtomicLong();
    final AtomicLong sharedSentCount = new AtomicLong();

    public static void main(String[] args) throws Exception {

        processArgs(args);

        LOG.info("Doing {} {}byte messages across {} producer(s) and {} consumer(s)", count, size, parallelProducers, parallelConsumers);
        ThroughPut throughPut = new ThroughPut();
        throughPut.produceConsume();
    }

    public void produceConsume() throws Exception {
        this.factory = createConnectionFactory(protocol);
        this.payloadString = new String(new byte[size]);

        final AtomicLong sharedSendCount = new AtomicLong(count);
        final AtomicLong sharedReceiveCount = new AtomicLong(count);
        long start = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(parallelConsumers + parallelProducers);

        for (int i = 0; i < parallelConsumers; i++) {
            final int id = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        consumeMessages(id, sharedReceiveCount);
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                }
            });
        }

        for (int i = 0; i < parallelProducers; i++) {
            final int id = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        publishMessages(id, sharedSendCount);
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

    private void consumeMessages(int id, AtomicLong count) throws Exception {
        Connection connection = factory.createConnection(user, password);
        connection.start();
        Session session = connection.createSession(false, ActiveMQSession.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(destinationName(id, destination));
        MessageConsumer consumer = session.createConsumer(queue);
        Message message;
        long c;
        while (count.get() > 0) {
            message = consumer.receive(10000);
            if (message != null) {
                count.decrementAndGet();
                c = sharedReceivedCount.incrementAndGet();
                if (c % 1000 == 0) {
                    LOG.info("Received {} messages", c);
                }
            }
        }
        consumer.close();
    }

    private void publishMessages(int id, AtomicLong count) throws Exception {
        Connection connection = factory.createConnection(user, password);
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(destinationName(id, destination));

        MessageProducer producer = session.createProducer(queue);
        producer.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
        Message message = session.createBytesMessage();
        ((BytesMessage) message).writeBytes(payloadString.getBytes());

        long c = 0;
        while (count.getAndDecrement() > 0) {
            producer.send(message);
            c = sharedSentCount.incrementAndGet();
            if (c % 1000 == 0) {
                LOG.info("Sent {}", c);
            }

        }
        producer.close();
        connection.close();
    }

    private String destinationName(int id, String destination) {
        if (destMod > 0) {
            return destination + "_" + (id % destMod);
        } else {
            return destination;
        }
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
                } else if ("--parallelParis".equals(arg)) {
                    ThroughPut.parallelConsumers = Integer.parseInt(shift(arg1));
                    ThroughPut.parallelProducers = ThroughPut.parallelConsumers;
                } else if ("--parallelProducers".equals(arg)) {
                    ThroughPut.parallelProducers = Integer.parseInt(shift(arg1));
                } else if ("--parallelConsumers".equals(arg)) {
                    ThroughPut.parallelConsumers = Integer.parseInt(shift(arg1));
                } else if ("--destMod".equals(arg)) {
                    ThroughPut.destMod = Integer.parseInt(shift(arg1));
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
