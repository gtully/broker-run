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

import java.security.Permission;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.fabric8.mq.Main;

public class ProducerConsumerPair {


    public static void main(final String[] args) {

        final AtomicBoolean done = new AtomicBoolean(false);

        final SecurityManager securityManager = new SecurityManager() {
           public void checkPermission( Permission permission ) {
             if( !done.get() && "exitVM.0".equals( permission.getName() ) ) {
               throw new SecurityException("Exit trapped") ;
             }
           }
         } ;
         System.setSecurityManager( securityManager ) ;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Main.main(addArg("consumer", args));
                } catch (SecurityException ignored) {
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });

        long start = System.currentTimeMillis();
        try {
            Main.main(addArg("producer", args));
        } catch (SecurityException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }

        executorService.shutdown();
        done.set(true);

        System.out.println("Duration seconds: " + TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
        System.out.flush();
    }

    private static String[] addArg(String arg0, String[] args) {
        LinkedList<String> runArgs = new LinkedList<String>();
        runArgs.add(arg0);
        runArgs.addAll(Arrays.asList(args));
        return runArgs.toArray(new String[]{});
    }
}
