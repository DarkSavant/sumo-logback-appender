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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * Appender that sends log messages to Sumo Logic.
 *
 * author Stefan Zier (stefan@sumologic.com)
 * author Scott Bessler (scott@relateiq.com) adapted log4j appender for logback
 */
@Slf4j
public class SumoLogicAppender extends AppenderBase<ILoggingEvent> {
    private Layout<ILoggingEvent> layout;

    private String url = null;
    private int connectionTimeout = 1000;
    private int socketTimeout = 60000;

    private HttpClient httpClient = null;

    public void setUrl(String url) {
        this.url = url;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    @Override
    public void start() {
        super.start();
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, connectionTimeout);
        HttpConnectionParams.setSoTimeout(params, socketTimeout);
        httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager(), params);
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

        sendToSumo(builder.toString());
    }

    @Override
    public void stop() {
        super.stop();
        httpClient.getConnectionManager().shutdown();
        httpClient = null;
    }

    // Private bits.

    private boolean checkEntryConditions() {
        if (httpClient == null) {
            return false;
        }

        return true;
    }

    private void sendToSumo(String data) {
        HttpPost post = null;
        try {
            post = new HttpPost(url);
            post.setEntity(new StringEntity(data, HTTP.PLAIN_TEXT_TYPE, HTTP.UTF_8));
            HttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
            }
            //need to consume the body if you want to re-use the connection.
            EntityUtils.consume(response.getEntity());
        } catch (IOException e) {
            try {
                post.abort();
            } catch (Exception ignore) {
            }
        }
    }

    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }
}
