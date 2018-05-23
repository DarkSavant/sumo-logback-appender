package com.auctionedge.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.sumologic.logback.json.CustomJsonLayout;

import java.util.Map;

public class EdgeConnectJsonLayout extends CustomJsonLayout {
	private String auctionId = "No Auction ID set in logback.xml -> <auctionId>the_id</auctionId>";
	private String auctionIdLogKey = "pipeline_id";

	@Override
	protected void addCustomDataToJsonMap(Map<String, Object> map, ILoggingEvent iLoggingEvent) {
		map.put(auctionIdLogKey, auctionId);
	}

	public void setAuctionId(String auctionId) {
		this.auctionId = auctionId;
	}

	public void setAuctionIdLogKey(String auctionIdLogKey) {
		this.auctionIdLogKey = auctionIdLogKey;
	}
}
