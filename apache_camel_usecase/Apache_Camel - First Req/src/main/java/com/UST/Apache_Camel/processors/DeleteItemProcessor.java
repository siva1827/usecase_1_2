package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DeleteItemProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(DeleteItemProcessor.class);

    public void setItemId(Exchange exchange) {
        String itemId = exchange.getIn().getHeader("itemId", String.class);
        exchange.getIn().setBody(itemId);
        logger.debug("Set itemId for deletion: {}", itemId);
    }

    public void handleItemNotFound(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
        exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_ITEM_NOT_FOUND));
        logger.warn("Item not found for deletion: {}", exchange.getIn().getHeader("itemId"));
    }

    public void handleDeleteSuccess(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        exchange.getIn().setBody(Map.of("message", "Item deleted successfully"));
        logger.info("Successfully deleted item: {}", exchange.getIn().getHeader("itemId"));
    }

    @Override
    public void process(Exchange exchange) throws Exception {
    }
}