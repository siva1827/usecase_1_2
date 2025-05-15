//package com.UST.Apache_Camel.config;
//
//import com.UST.Apache_Camel.exception.InventoryValidationException;
//import org.apache.camel.Exchange;
//import org.apache.camel.Processor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.*;
//
//public class InventoryUpdateComponents {
//    private static final Logger logger = LoggerFactory.getLogger(InventoryUpdateComponents.class);
//
//    public static class ItemProcessor implements Processor {
//        // Placeholder method required by the Processor interface, not used in this implementation
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//
//        /* Validates the incoming item payload, ensuring it has '_id' and 'stockDetails' with 'soldOut' and 'damaged' fields
//           Sets itemId, soldOut, and damaged as exchange properties for later use
//           Throws InventoryValidationException if validation fails */
//        public void processItem(Exchange exchange) throws Exception {
//            Map<String, Object> item = exchange.getIn().getBody(Map.class);
//            if (item == null || item.get("_id") == null || item.get("stockDetails") == null) {
//                throw new InventoryValidationException("Each item must have '_id' and 'stockDetails'.");
//            }
//
//            String id = item.get("_id").toString();
//            Map<String, Object> stock = (Map<String, Object>) item.get("stockDetails");
//
//            if (!stock.containsKey("soldOut") || !stock.containsKey("damaged")) {
//                throw new InventoryValidationException("Missing 'soldOut' or 'damaged' values in stock details for item: " + id);
//            }
//
//            int soldOut = Integer.parseInt(stock.get("soldOut").toString());
//            int damaged = Integer.parseInt(stock.get("damaged").toString());
//
//            exchange.setProperty("itemId", id);
//            exchange.setProperty("soldOut", soldOut);
//            exchange.setProperty("damaged", damaged);
//            logger.debug("Processing item: {}", id);
//        }
//
//         /* Prepares the item ID for MongoDB findById operation by setting it as the message body
//            Retrieves itemId from exchange properties and logs the operation */
//        public void setItemId(Exchange exchange) {
//            String itemId = exchange.getProperty("itemId", String.class);
//            exchange.getIn().setBody(itemId);
//            logger.debug("Set itemId for findById: {}", itemId);
//        }
//
//        /* Validates the retrieved item from MongoDB and updates its stock details
//           Checks if the item exists and has valid stockDetails, then updates availableStock, soldOut, and damaged
//           Sets the updated item in the exchange body and as a property for saving to MongoDB
//           Throws InventoryValidationException if the item is not found or stock validation fails */
//        public void validateAndUpdateItem(Exchange exchange) throws Exception {
//            Map<String, Object> item = exchange.getIn().getBody(Map.class);
//            if (item == null) {
//                throw new InventoryValidationException("Item not found for ID: " + exchange.getProperty("itemId"));
//            }
//
//            Map<String, Object> stockDetails = (Map<String, Object>) item.get("stockDetails");
//            if (stockDetails == null) {
//                throw new InventoryValidationException("Missing stockDetails in DB for item: " + exchange.getProperty("itemId"));
//            }
//
//            int availableStock = Integer.parseInt(stockDetails.get("availableStock").toString());
//            int existingSoldOut = Integer.parseInt(stockDetails.get("soldOut").toString());
//            int existingDamaged = Integer.parseInt(stockDetails.get("damaged").toString());
//
//            int soldOut = exchange.getProperty("soldOut", Integer.class);
//            int damaged = exchange.getProperty("damaged", Integer.class);
//
//            if (soldOut + damaged > availableStock) {
//                throw new InventoryValidationException("Requested quantity exceeds available stock for item ID: " + exchange.getProperty("itemId"));
//            }
//
//            stockDetails.put("availableStock", availableStock - soldOut - damaged);
//            stockDetails.put("soldOut", existingSoldOut + soldOut);
//            stockDetails.put("damaged", existingDamaged + damaged);
//
//            item.put("stockDetails", stockDetails);
//            item.put("lastUpdateDate", LocalDate.now().toString());
//
//            exchange.setProperty("updatedItem", item);
//            exchange.getIn().setBody(item);
//        }
//
//        /* Marks the inventory update as successful and stores the result in exchange properties
//           Creates a result map with itemId, status, and success message, then logs the success */
//        public void markSuccess(Exchange exchange) {
//            String itemId = exchange.getProperty("itemId", String.class);
//            Map<String, Object> itemResult = new HashMap<>();
//            itemResult.put("itemId", itemId);
//            itemResult.put("status", "success");
//            itemResult.put("message", "Inventory updated successfully for item " + itemId);
//            exchange.setProperty("itemResult", itemResult);
//            logger.info("Inventory updated for {}", itemId);
//        }
//
//        /* Marks the inventory update as failed and stores the error result in exchange properties
//           Retrieves the caught exception, creates a result map with itemId, status, and error message, then logs the failure */
//        public void markFailure(Exchange exchange) {
//            String itemId = exchange.getProperty("itemId", String.class);
//            InventoryValidationException e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, InventoryValidationException.class);
//            String errorMessage = e.getMessage();
//            Map<String, Object> itemResult = new HashMap<>();
//            itemResult.put("itemId", itemId);
//            itemResult.put("status", "error");
//            itemResult.put("message", errorMessage);
//            exchange.setProperty("itemResult", itemResult);
//            logger.warn("Inventory update failed for item {}: {}", itemId, errorMessage);
//        }
//    }
//
//    public static class AsyncInventoryUpdateProcessor implements Processor {
//        private static final Logger logger = LoggerFactory.getLogger(AsyncInventoryUpdateProcessor.class);
//
//        /* Handles exceptions that occur during queue processing in the Inventory Queue Processor Service
//           Logs the error and sets an error response in the exchange body with status and message */
//        public void handleQueueException(Exchange exchange) {
//            Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
//            String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
//            logger.error("Error processing inventory update queue: {}", errorMessage, exception);
//            exchange.getIn().setBody(Map.of(
//                    "status", "error",
//                    "message", "Failed to process inventory update: " + errorMessage
//            ));
//        }
//
//        /* Creates an audit record for the inventory update and sets it as the exchange body for MongoDB insertion
//           Includes correlationId, itemId, status, message, and timestamp from the itemResult and JMS headers */
//        public void storeAuditRecord(Exchange exchange) {
//            Map<String, Object> itemResult = exchange.getProperty("itemResult", Map.class);
//            String correlationId = exchange.getIn().getHeader("JMSCorrelationID", String.class);
//            String itemId = exchange.getProperty("itemId", String.class);
//            Map<String, Object> auditRecord = new HashMap<>();
//            auditRecord.put("_id", UUID.randomUUID().toString());
//            auditRecord.put("correlationId", correlationId);
//            auditRecord.put("itemId", itemId);
//            auditRecord.put("status", itemResult.get("status"));
//            auditRecord.put("message", itemResult.get("message"));
//            auditRecord.put("timestamp", LocalDateTime.now().toString());
//            exchange.getIn().setBody(auditRecord);
//            logger.info("Prepared audit record for itemId: {}, correlationId: {}", itemId, correlationId);
//        }
//
//        // Placeholder method required by the Processor interface, not used in this implementation
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//    }
//}