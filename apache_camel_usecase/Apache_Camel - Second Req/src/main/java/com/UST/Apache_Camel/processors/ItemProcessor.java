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

    // Placeholder method required by the Processor interface, not used in this implementation
    @Override
    public void process(Exchange exchange) throws Exception {
    }

    /* Validates the incoming item payload, ensuring it has '_id' and 'stockDetails' with 'soldOut' and 'damaged' fields
       Sets itemId, soldOut, and damaged as exchange properties for later use
       Throws InventoryValidationException if validation fails */
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

        int soldOut = Integer.parseInt(stock.get("soldOut").toString());
        int damaged = Integer.parseInt(stock.get("damaged").toString());

        exchange.setProperty("itemId", id);
        exchange.setProperty("soldOut", soldOut);
        exchange.setProperty("damaged", damaged);
        logger.debug("Processing item: {}", id);
    }

    /* Prepares the item ID for MongoDB findById operation by setting it as the message body
       Retrieves itemId from exchange properties and logs the operation */
    public void setItemId(Exchange exchange) {
        String itemId = exchange.getProperty("itemId", String.class);
        exchange.getIn().setBody(itemId);
        logger.debug("Set itemId for findById: {}", itemId);
    }

    /* Validates the retrieved item from MongoDB and updates its stock details
       Checks if the item exists and has valid stockDetails, then updates availableStock, soldOut, and damaged
       Sets the updated item in the exchange body and as a property for saving to MongoDB
       Throws InventoryValidationException if the item is not found or stock validation fails */
    public void validateAndUpdateItem(Exchange exchange) throws Exception {
        Map<String, Object> item = exchange.getIn().getBody(Map.class);
        if (item == null) {
            throw new InventoryValidationException("Item not found for ID: " + exchange.getProperty("itemId"));
        }

        Map<String, Object> stockDetails = (Map<String, Object>) item.get("stockDetails");
        if (stockDetails == null) {
            throw new InventoryValidationException("Missing stockDetails in DB for item: " + exchange.getProperty("itemId"));
        }

        int availableStock = Integer.parseInt(stockDetails.get("availableStock").toString());
        int existingSoldOut = Integer.parseInt(stockDetails.get("soldOut").toString());
        int existingDamaged = Integer.parseInt(stockDetails.get("damaged").toString());

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

    /* Marks the inventory update as successful and stores the result in exchange properties
       Creates a result map with itemId, status, and success message, then logs the success */
    public void markSuccess(Exchange exchange) {
        String itemId = exchange.getProperty("itemId", String.class);
        Map<String, Object> itemResult = new HashMap<>();
        itemResult.put("itemId", itemId);
        itemResult.put("status", "success");
        itemResult.put("message", "Inventory updated successfully for item " + itemId);
        exchange.setProperty("itemResult", itemResult);
        logger.info("Inventory updated for {}", itemId);
    }

    /* Marks the inventory update as failed and stores the error result in exchange properties
       Retrieves the caught exception, creates a result map with itemId, status, and error message, then logs the failure */
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