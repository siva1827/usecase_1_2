package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class GetItemProcessor  {
    private static final Logger logger = LoggerFactory.getLogger(GetItemProcessor.class);

    public void setItemId(Exchange exchange) {
        String itemId = exchange.getIn().getHeader("itemId", String.class);
        exchange.getIn().setBody(itemId);
        logger.debug("Set itemId for findById: {}", itemId);
    }

    public void processResult(Exchange exchange) {
        if (exchange.getIn().getBody() == null) {
            logger.info("Item not found for ID: {}", exchange.getIn().getHeader("itemId"));
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_ITEM_NOT_FOUND));
        } else {
            logger.info("Item found: {}", exchange.getIn().getBody());
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        }
    }

}