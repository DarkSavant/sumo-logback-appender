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
package com.sumologic.logback.http;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * author: Jose Muniz (jose@sumologic.com)
 */
@Slf4j
public class SumoHttpSender {

    private long retryInterval = 10000L;

    private volatile String url = null;
    private int connectionTimeout = 1000;
    private int socketTimeout = 60000;
    private volatile HttpClient httpClient = null;


    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public boolean isInitialized() {
        return httpClient != null;
    }

    public void init() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, connectionTimeout);
        HttpConnectionParams.setSoTimeout(params, socketTimeout);
        httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager(), params);
    }

    public void close() {
        httpClient.getConnectionManager().shutdown();
        httpClient = null;
    }

    public void send(String body, String name) {
        keepTrying(body, name);
    }

    private void keepTrying(String body, String name) {
        boolean success = false;
        do {
            try {
                trySend(body, name);
                success = true;
            } catch (Exception e) {
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e1) {
                    break;
                }
            }
        } while (!success && !Thread.currentThread().isInterrupted());
    }

    private void trySend(String body, String name) throws IOException {
        HttpPost post = null;
        try {
            if (url == null)
                throw new IOException("Unknown endpoint");

            post = new HttpPost(url);
            post.setHeader("X-Sumo-Name", name);
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            HttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                // Not success. Only retry if status is unavailable.
                if (statusCode == 503 || statusCode == 429) {
                    throw new IOException("Server unavailable");
                }
                log.warn(String.format("Received HTTP error from Sumo Service: %d", statusCode));
            }
            //need to consume the body if you want to re-use the connection.
            log.debug("Successfully sent log request to Sumo Logic");
            EntityUtils.consume(response.getEntity());
        } catch (IOException e) {
            log.warn("Could not send log to Sumo Logic");
            log.debug("Reason:", e);
            try {
                post.abort();
            } catch (Exception ignore) {
            }
            throw e;
        }
    }

}
