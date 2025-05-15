package com.UST.Apache_Camel.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AsyncInventoryUpdateProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(AsyncInventoryUpdateProcessor.class);

    // Placeholder method required by the Processor interface, not used in this implementation
    @Override
    public void process(Exchange exchange) throws Exception {
    }

    /* Handles exceptions that occur during queue processing in the Inventory Queue Processor Service
       Logs the error and sets an error response in the exchange body with status and message */
    public void handleQueueException(Exchange exchange) {
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
        logger.error("Error processing inventory update queue: {}", errorMessage, exception);
        exchange.getIn().setBody(Map.of(
                "status", "error",
                "message", "Failed to process inventory update: " + errorMessage
        ));
    }

    /* Creates an audit record for the inventory update and sets it as the exchange body for MongoDB insertion
       Includes correlationId, itemId, status, message, and timestamp from the itemResult and JMS headers */
    public void storeAuditRecord(Exchange exchange) {
        Map<String, Object> itemResult = exchange.getProperty("itemResult", Map.class);
        String correlationId = exchange.getIn().getHeader("JMSCorrelationID", String.class);
        String itemId = exchange.getProperty("itemId", String.class);
        Map<String, Object> auditRecord = new HashMap<>();
        auditRecord.put("_id", UUID.randomUUID().toString());
        auditRecord.put("correlationId", correlationId);
        auditRecord.put("itemId", itemId);
        auditRecord.put("status", itemResult.get("status"));
        auditRecord.put("message", itemResult.get("message"));
        auditRecord.put("timestamp", LocalDateTime.now().toString());
        exchange.getIn().setBody(auditRecord);
        logger.info("Prepared audit record for itemId: {}, correlationId: {}", itemId, correlationId);
    }
}