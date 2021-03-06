/**
 * _____ _____ _____ _____    __    _____ _____ _____ _____
 * |   __|  |  |     |     |  |  |  |     |   __|     |     |
 * |__   |  |  | | | |  |  |  |  |__|  |  |  |  |-   -|   --|
 * |_____|_____|_|_|_|_____|  |_____|_____|_____|_____|_____|
 * <p>
 * UNICORNS AT WARP SPEED SINCE 2010
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.logback.aggregation;

import java.util.ArrayList;
import java.util.List;

import com.sumologic.logback.queue.BufferWithEviction;

import lombok.extern.slf4j.Slf4j;

/**
 * Task to perform a single flushing check
 *
 * author: Jose Muniz (jose@sumologic.com)
 */
@Slf4j
public abstract class BufferFlushingTask<In, Out> implements Runnable {

	private long timeOfLastFlush = System.currentTimeMillis();
	private BufferWithEviction<In> messageQueue;

	private boolean needsFlushing() {
		long currentTime = System.currentTimeMillis();
		long dateOfNextFlush = timeOfLastFlush + getMaxFlushInterval();

		return (messageQueue.size() >= getMessagesPerRequest()) ||
				(currentTime >= dateOfNextFlush);
	}

	private void flushAndSend() {
		List<In> messages = new ArrayList<In>(messageQueue.size());
		messageQueue.drainTo(messages);

		if (messages.size() > 0) {
			log.debug(String.format("%s - Flushing and sending out %d messages (%d messages left)",
					new java.util.Date(),
					messages.size(),
					messageQueue.size()));
			Out body = aggregate(messages);
			sendOut(body, getName());
		}
	}


    /* Subclasses should define from here */

	abstract protected long getMaxFlushInterval();

	abstract protected long getMessagesPerRequest();

	abstract protected String getName();

	protected BufferFlushingTask(BufferWithEviction<In> messageQueue) {
		this.messageQueue = messageQueue;
	}

	// Given the list of messages, aggregate them into a single Out object
	abstract protected Out aggregate(List<In> messages);

	// Send aggregated message out. Block until we've successfully sent it.
	abstract protected void sendOut(Out body, String name);



    /* Public interface */

	@Override
	public void run() {
		if (needsFlushing()) {
			try {
				flushAndSend();
			} catch (Exception e) {
				log.warn("Exception while attempting to flush and send", e);
			}
		}
	}

}
