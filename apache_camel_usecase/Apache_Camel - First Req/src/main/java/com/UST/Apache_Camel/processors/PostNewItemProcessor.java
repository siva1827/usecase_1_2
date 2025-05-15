package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import com.UST.Apache_Camel.exception.InventoryValidationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PostNewItemProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(PostNewItemProcessor.class);

    public void validateItem(Exchange exchange) throws Exception {
        Map<String, Object> item = exchange.getIn().getBody(Map.class);
        exchange.setProperty("newItem", item);
        if (item == null || item.get("_id") == null || item.get("itemPrice") == null || item.get("stockDetails") == null) {
            throw new InventoryValidationException("Item must have '_id', 'itemPrice', and 'stockDetails'");
        }

        String itemId = item.get("_id").toString();
        Map<String, Object> price = (Map<String, Object>) item.get("itemPrice");
        Map<String, Object> stock = (Map<String, Object>) item.get("stockDetails");

        // Validate itemPrice
        if (!price.containsKey("basePrice") || !price.containsKey("sellingPrice")) {
            throw new InventoryValidationException("itemPrice must contain 'basePrice' and 'sellingPrice' for item: " + itemId);
        }

        Object basePriceObj = price.get("basePrice");
        Object sellingPriceObj = price.get("sellingPrice");

        if (!(basePriceObj instanceof Number)) {
            throw new InventoryValidationException("basePrice must be a number for item: " + itemId + ", found: " + basePriceObj.getClass().getSimpleName());
        }
        if (!(sellingPriceObj instanceof Number)) {
            throw new InventoryValidationException("sellingPrice must be a number for item: " + itemId + ", found: " + sellingPriceObj.getClass().getSimpleName());
        }

        double basePrice = ((Number) basePriceObj).doubleValue();
        double sellingPrice = ((Number) sellingPriceObj).doubleValue();

        if (basePrice <= 0) {
            throw new InventoryValidationException("basePrice must be greater than zero for item: " + itemId);
        }
        if (sellingPrice <= 0) {
            throw new InventoryValidationException("sellingPrice must be greater than zero for item: " + itemId);
        }

        // Validate stockDetails
        if (!stock.containsKey("availableStock") || !stock.containsKey("soldOut") || !stock.containsKey("damaged")) {
            throw new InventoryValidationException("stockDetails must contain 'availableStock', 'soldOut', and 'damaged' for item: " + itemId);
        }

        Object availableStockObj = stock.get("availableStock");
        Object soldOutObj = stock.get("soldOut");
        Object damagedObj = stock.get("damaged");

        if (!(availableStockObj instanceof Integer)) {
            throw new InventoryValidationException("availableStock must be an integer for item: " + itemId + ", found: " + availableStockObj.getClass().getSimpleName());
        }
        if (!(soldOutObj instanceof Integer)) {
            throw new InventoryValidationException("soldOut must be an integer for item: " + itemId + ", found: " + soldOutObj.getClass().getSimpleName());
        }
        if (!(damagedObj instanceof Integer)) {
            throw new InventoryValidationException("damaged must be an integer for item: " + itemId + ", found: " + damagedObj.getClass().getSimpleName());
        }

        int availableStock = (Integer) availableStockObj;
        int soldOut = (Integer) soldOutObj;
        int damaged = (Integer) damagedObj;

        if (availableStock < 0) {
            throw new InventoryValidationException("availableStock cannot be negative for item: " + itemId);
        }
        if (soldOut < 0) {
            throw new InventoryValidationException("soldOut cannot be negative for item: " + itemId);
        }
        if (damaged < 0) {
            throw new InventoryValidationException("damaged cannot be negative for item: " + itemId);
        }

        // Validate specialProduct
        Object specialProductObj = item.get("specialProduct");
        if (specialProductObj != null && !(specialProductObj instanceof Boolean)) {
            throw new InventoryValidationException("specialProduct must be a boolean for item: " + itemId + ", found: " + specialProductObj.getClass().getSimpleName());
        }

        exchange.getIn().setBody(itemId);
        logger.debug("Validated item and set itemId for findById: {}", itemId);
    }

    public void handleExistingItem(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
        exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_ITEM_ALREADY_EXISTS));
        logger.warn("Item already exists: {}", exchange.getProperty("newItem", Map.class).get("_id"));
    }

    public void setCategoryId(Exchange exchange) {
        Map<String, Object> item = exchange.getProperty("newItem", Map.class);
        exchange.setProperty("validatedItem", item);
        String categoryId = (String) item.get("categoryId");
        exchange.getIn().setBody(categoryId);
        logger.debug("Set categoryId for findById: {}", categoryId);
    }

    public void handleInvalidCategory(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
        exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_CATEGORY_NOT_FOUND));
        logger.warn("Invalid category for item: {}", exchange.getProperty("validatedItem", Map.class).get("_id"));
    }

    public void prepareItemForInsert(Exchange exchange) {
        Map<String, Object> item = exchange.getProperty("validatedItem", Map.class);
        exchange.getIn().setBody(item);
        logger.debug("Prepared item for insert: {}", item.get("_id"));
    }

    public void handleInsertSuccess(Exchange exchange) {
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
        exchange.getIn().setBody(Map.of("message", "Item inserted successfully"));
        logger.info("Successfully inserted item: {}", exchange.getProperty("validatedItem", Map.class).get("_id"));
    }

    @Override
    public void process(Exchange exchange) throws Exception {
    }
}