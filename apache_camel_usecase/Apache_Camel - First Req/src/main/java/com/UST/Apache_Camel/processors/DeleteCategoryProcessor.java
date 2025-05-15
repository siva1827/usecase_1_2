package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DeleteCategoryProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(DeleteCategoryProcessor.class);

    public void setCategoryId(Exchange exchange) {
        String categoryId = exchange.getIn().getHeader("categoryId", String.class);
        exchange.getIn().setBody(categoryId);
        logger.debug("Set categoryId for deletion: {}", categoryId);
    }

    public void handleCategoryNotFound(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_CATEGORY_NOT_FOUND));
        logger.warn("Category not found for deletion: {}", exchange.getIn().getHeader("categoryId"));
    }

    public void handleDeleteSuccess(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        exchange.getIn().setBody(Map.of("message", "Category deleted successfully"));
        logger.info("Successfully deleted category: {}", exchange.getIn().getHeader("categoryId"));
    }

    @Override
    public void process(Exchange exchange) throws Exception {
    }
}