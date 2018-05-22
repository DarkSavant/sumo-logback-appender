/**
 *    _____ _____ _____ _____    __    _____ _____ _____ _____
 *   |   __|  |  |     |     |  |  |  |     |   __|     |     |
 *   |__   |  |  | | | |  |  |  |  |__|  |  |  |  |-   -|   --|
 *   |_____|_____|_|_|_|_____|  |_____|_____|_____|_____|_____|
 *
 *                UNICORNS AT WARP SPEED SINCE 2010
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.sumologic.logback;

import com.sumologic.logback.aggregation.SumoBufferFlusher;
import com.sumologic.logback.http.SumoHttpSender;
import com.sumologic.logback.queue.BufferWithEviction;
import com.sumologic.logback.queue.BufferWithFifoEviction;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import lombok.extern.slf4j.Slf4j;

import static com.sumologic.logback.queue.CostBoundedConcurrentQueue.CostAssigner;


/**
 * Appender that sends log messages to Sumo Logic.
 *
 * author Jose Muniz (jose@sumologic.com)
 */
@Slf4j
public class BufferedSumoLogicAppender extends AppenderBase<ILoggingEvent> {
    private Layout<ILoggingEvent> layout;

    private String url = null;
    private int connectionTimeout = 1000;
    private int socketTimeout = 60000;
    private int retryInterval = 10000;        // Once a request fails, how often until we retry.

    private long messagesPerRequest = 100;    // How many messages need to be in the queue before we flush
    private long maxFlushInterval = 10000;    // Maximum interval between flushes (ms)
    private long flushingAccuracy = 250;      // How often the flushed thread looks into the message queue (ms)
    private String sourceName = "sumo-logback-appender"; // Name to stamp for querying with _sourceName

    private long maxQueueSizeBytes = 1000000;

    private SumoHttpSender sender;
    private SumoBufferFlusher flusher;
    volatile private BufferWithEviction<String> queue;

    /* All the parameters */

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMaxQueueSizeBytes(long maxQueueSizeBytes) {
        this.maxQueueSizeBytes = maxQueueSizeBytes;
    }

    public void setMessagesPerRequest(long messagesPerRequest) {
        this.messagesPerRequest = messagesPerRequest;
    }


    public void setMaxFlushInterval(long maxFlushInterval) {
        this.maxFlushInterval = maxFlushInterval;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public void setFlushingAccuracy(long flushingAccuracy) {
        this.flushingAccuracy = flushingAccuracy;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public void setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
    }

    @Override
    public void start() {
        super.start();

        /* Initialize queue */
        if (queue == null) {
            queue = new BufferWithFifoEviction<String>(maxQueueSizeBytes, new CostAssigner<String>() {
              @Override
              public long cost(String e) {
                  // Note: This is only an estimate for total byte usage, since in UTF-8 encoding,
                  // the size of one character may be > 1 byte.
                 return e.length();
              }
            });
        } else {
            queue.setCapacity(maxQueueSizeBytes);
        }

        /* Initialize sender */
        if (sender == null)
            sender = new SumoHttpSender();

        sender.setRetryInterval(retryInterval);
        sender.setConnectionTimeout(connectionTimeout);
        sender.setSocketTimeout(socketTimeout);
        sender.setUrl(url);

        sender.init();

        /* Initialize flusher  */
        if (flusher != null)
            flusher.stop();

        flusher = new SumoBufferFlusher(flushingAccuracy,
                    messagesPerRequest,
                    maxFlushInterval,
                    sourceName,
                    sender,
                    queue);
        flusher.start();

    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!checkEntryConditions()) {
            return;
        }

        StringBuilder builder = new StringBuilder(1024);
        builder.append(layout.doLayout(event));

        // Append stack trace if present
        IThrowableProxy error = event.getThrowableProxy();
        if (error != null) {
//            formattedEvent += ExceptionFormatter.formatException(error);
        }

        try {
            queue.add(builder.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        super.stop();
        sender.close();
        sender = null;

        flusher.stop();
        flusher = null;
    }

    // Private bits.

    private boolean checkEntryConditions() {
        return sender != null && sender.isInitialized();
    }

    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }
}
