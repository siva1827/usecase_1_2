package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.exception.InventoryValidationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ItemProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(ItemProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
    }

    public void processItem(Exchange exchange) throws Exception {
        Map<String, Object> item = exchange.getIn().getBody(Map.class);
        if (item == null || item.get("_id") == null || item.get("stockDetails") == null) {
            throw new InventoryValidationException("Each item must have '_id' and 'stockDetails'.");
        }

        String id = item.get("_id").toString();
        Map<String, Object> stock = (Map<String, Object>) item.get("stockDetails");

        if (!stock.containsKey("soldOut") || !stock.containsKey("damaged")) {
            throw new InventoryValidationException("Missing 'soldOut' or 'damaged' values in stock details for item: " + id);
        }

        Object soldOutObj = stock.get("soldOut");
        Object damagedObj = stock.get("damaged");

        if (!(soldOutObj instanceof Integer)) {
            throw new InventoryValidationException("soldOut must be an integer for item: " + id + ", found: " + soldOutObj.getClass().getSimpleName());
        }
        if (!(damagedObj instanceof Integer)) {
            throw new InventoryValidationException("damaged must be an integer for item: " + id + ", found: " + damagedObj.getClass().getSimpleName());
        }

        int soldOut = (Integer) soldOutObj;
        int damaged = (Integer) damagedObj;

        if (soldOut < 0) {
            throw new InventoryValidationException("soldOut cannot be negative for item: " + id);
        }
        if (damaged < 0) {
            throw new InventoryValidationException("damaged cannot be negative for item: " + id);
        }

        exchange.setProperty("itemId", id);
        exchange.setProperty("soldOut", soldOut);
        exchange.setProperty("damaged", damaged);
        logger.debug("Processing item: {}", id);
    }

    public void setItemId(Exchange exchange) {
        String itemId = exchange.getProperty("itemId", String.class);
        exchange.getIn().setBody(itemId);
        logger.debug("Set itemId for findById: {}", itemId);
    }

    public void validateAndUpdateItem(Exchange exchange) throws Exception {
        Map<String, Object> item = exchange.getIn().getBody(Map.class);
        if (item == null) {
            throw new InventoryValidationException("Item not found for ID: " + exchange.getProperty("itemId"));
        }

        Map<String, Object> stockDetails = (Map<String, Object>) item.get("stockDetails");
        if (stockDetails == null) {
            throw new InventoryValidationException("Missing stockDetails in DB for item: " + exchange.getProperty("itemId"));
        }

        Object availableStockObj = stockDetails.get("availableStock");
        Object existingSoldOutObj = stockDetails.get("soldOut");
        Object existingDamagedObj = stockDetails.get("damaged");

        if (!(availableStockObj instanceof Integer)) {
            throw new InventoryValidationException("availableStock must be an integer for item: " + exchange.getProperty("itemId") + ", found: " + availableStockObj.getClass().getSimpleName());
        }
        if (!(existingSoldOutObj instanceof Integer)) {
            throw new InventoryValidationException("soldOut must be an integer for item: " + exchange.getProperty("itemId") + ", found: " + existingSoldOutObj.getClass().getSimpleName());
        }
        if (!(existingDamagedObj instanceof Integer)) {
            throw new InventoryValidationException("damaged must be an integer for item: " + exchange.getProperty("itemId") + ", found: " + existingDamagedObj.getClass().getSimpleName());
        }

        int availableStock = (Integer) availableStockObj;
        int existingSoldOut = (Integer) existingSoldOutObj;
        int existingDamaged = (Integer) existingDamagedObj;

        if (availableStock < 0) {
            throw new InventoryValidationException("availableStock cannot be negative for item: " + exchange.getProperty("itemId"));
        }

        int soldOut = exchange.getProperty("soldOut", Integer.class);
        int damaged = exchange.getProperty("damaged", Integer.class);

        if (soldOut + damaged > availableStock) {
            throw new InventoryValidationException("Requested quantity exceeds available stock for item ID: " + exchange.getProperty("itemId"));
        }

        stockDetails.put("availableStock", availableStock - soldOut - damaged);
        stockDetails.put("soldOut", existingSoldOut + soldOut);
        stockDetails.put("damaged", existingDamaged + damaged);

        item.put("stockDetails", stockDetails);
        item.put("lastUpdateDate", LocalDate.now().toString());

        exchange.setProperty("updatedItem", item);
        exchange.getIn().setBody(item);
    }

    public void markSuccess(Exchange exchange) {
        String itemId = exchange.getProperty("itemId", String.class);
        Map<String, Object> itemResult = new HashMap<>();
        itemResult.put("itemId", itemId);
        itemResult.put("status", "success");
        itemResult.put("message", "Inventory updated successfully for item " + itemId);
        exchange.setProperty("itemResult", itemResult);
        logger.info("✅ Inventory updated for {}", itemId);
    }

    public void markFailure(Exchange exchange) {
        String itemId = exchange.getProperty("itemId", String.class);
        InventoryValidationException e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, InventoryValidationException.class);
        String errorMessage = e.getMessage();
        Map<String, Object> itemResult = new HashMap<>();
        itemResult.put("itemId", itemId);
        itemResult.put("status", "error");
        itemResult.put("message", errorMessage);
        exchange.setProperty("itemResult", itemResult);
        logger.warn("Inventory update failed for item {}: {}", itemId, errorMessage);
    }
}