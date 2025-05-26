package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.exception.InventoryValidationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.UncategorizedJmsException;

import javax.jms.JMSException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AsyncInventoryUpdateProcessor  {
    private static final Logger logger = LoggerFactory.getLogger(AsyncInventoryUpdateProcessor.class);


    public void handleException(Exchange exchange) {
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        String correlationId = exchange.getProperty("correlationId", String.class);
        String errorMessage;
        int httpStatus;

        if (exception instanceof UncategorizedJmsException || exception instanceof JMSException) {
            errorMessage = "Something went wrong with ActiveMQ";
            httpStatus = 503;
            logger.warn("JMS error for correlationId {}: {}", correlationId, errorMessage);
        } else if (exception instanceof InventoryValidationException) {
            errorMessage = exception.getMessage();
            httpStatus = 400;
            logger.warn("Validation error for correlationId {}: {}", correlationId, errorMessage);
        } else {
            errorMessage = exception != null ? exception.getMessage() : "Unknown error";
            httpStatus = 500;
            logger.error("Error in async update for correlationId {}: {}", correlationId, errorMessage, exception);
        }
        // Clear exchange properties
        exchange.removeProperty("correlationId");
        exchange.removeProperty("inventoryList");
        exchange.removeProperty(Exchange.EXCEPTION_CAUGHT);

        // Set response
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, httpStatus);
        exchange.getIn().setBody(Map.of(
                "status", "error",
                "message", errorMessage
        ));
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