package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class GetItemProcessor {
    private static final Logger logger = LoggerFactory.getLogger(GetItemProcessor.class);

    public void setItemId(Exchange exchange) {
        String itemId = exchange.getIn().getHeader("itemId", String.class);
        exchange.getIn().setBody(itemId);
        logger.debug("Set itemId for findById: {}", itemId);
    }

    public void setCategoryId(Exchange exchange) {
        Map<String, Object> item = exchange.getIn().getBody(Map.class);
        if (item == null || !item.containsKey("categoryId")) {
            logger.info("Item not found or missing categoryId for ID: {}", exchange.getIn().getHeader("itemId"));
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_ITEM_NOT_FOUND));
            exchange.setProperty("itemNotFound", true);
            return;
        }
        exchange.setProperty("item", item);
        String categoryId = (String) item.get("categoryId");
        exchange.getIn().setBody(categoryId);
        logger.debug("Set categoryId for findById: {}", categoryId);
    }

    public void processCategoryResult(Exchange exchange) {
        if (exchange.getProperty("itemNotFound", Boolean.class) != null && exchange.getProperty("itemNotFound", Boolean.class)) {
            return; // Skip if item was not found
        }

        Map<String, Object> category = exchange.getIn().getBody(Map.class);
        Map<String, Object> item = exchange.getProperty("item", Map.class);

        logger.debug("Category document for item ID {} with categoryId {}: {}", 
                item.get("_id"), item.get("categoryId"), category);

        if (category == null || !category.containsKey("categoryName")) {
            logger.info("Category not found or missing categoryName for item ID: {}, categoryId: {}", 
                    item.get("_id"), item.get("categoryId"));
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_CATEGORY_NOT_FOUND));
            return;
        }

        // Transform item to include categoryName and remove categoryId
        item.put("categoryName", category.get("categoryName"));
        item.remove("categoryId");

        // Filter stockDetails to include only availableStock and unitOfMeasure
        Map<String, Object> filteredStockDetails = new LinkedHashMap<>();
        Map<String, Object> stockDetails = (Map<String, Object>) item.get("stockDetails");
        if (stockDetails != null) {
            filteredStockDetails.put("availableStock", stockDetails.getOrDefault("availableStock", 0));
            filteredStockDetails.put("unitOfMeasure", stockDetails.get("unitOfMeasure"));
            logger.debug("Filtered stockDetails for item ID {}: {}", item.get("_id"), filteredStockDetails);
        } else {
            logger.debug("No stockDetails for item ID: {}", item.get("_id"));
        }

        // Create response with specified field order
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("_id", item.get("_id"));
        response.put("itemName", item.get("itemName"));
        response.put("categoryName", item.get("categoryName"));
        response.put("itemPrice", item.get("itemPrice"));
        response.put("stockDetails", filteredStockDetails);
        response.put("specialProduct", item.getOrDefault("specialProduct", false));

        exchange.getIn().setBody(response);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        logger.info("Item found with categoryName: {}", response);
    }

    public void processResult(Exchange exchange) {
        if (exchange.getIn().getBody() == null) {
            logger.info("Item not found for ID: {}", exchange.getIn().getHeader("itemId"));
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_ITEM_NOT_FOUND));
            exchange.setProperty("itemNotFound", true);
        } else {
            logger.debug("Item found, proceeding to fetch category: {}", exchange.getIn().getBody());
        }
    }
}