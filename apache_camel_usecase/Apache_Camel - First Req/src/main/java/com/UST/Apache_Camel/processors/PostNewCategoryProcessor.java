package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PostNewCategoryProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(PostNewCategoryProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
    }

    public void validateCategory(Exchange exchange) throws Exception {
        Map<String, Object> category = exchange.getIn().getBody(Map.class);
        exchange.setProperty("newCategory", category);

        String categoryId = (String) category.get("_id");
        String categoryName = (String) category.get("categoryName");

        if (categoryId == null || categoryId.isBlank() || categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("Category ID and Category Name must not be empty");
        }

        exchange.getIn().setBody(categoryId);
        logger.debug("Validated category and set categoryId for findById: {}", categoryId);
    }

    public void prepareCategoryForInsert(Exchange exchange) {
        Map<String, Object> category = exchange.getProperty("newCategory", Map.class);
        exchange.getIn().setBody(category);
        logger.debug("Prepared category for insert: {}", category.get("_id"));
    }

    public void handleInsertSuccess(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
        exchange.getIn().setBody(Map.of("message", "Category inserted successfully"));
        logger.info("Successfully inserted category: {}", exchange.getProperty("newCategory", Map.class).get("_id"));
    }

    public void handleExistingCategory(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
        exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_CATEGORY_ALREADY_EXISTS));
        logger.warn("Category already exists: {}", exchange.getProperty("newCategory", Map.class).get("_id"));
    }
}