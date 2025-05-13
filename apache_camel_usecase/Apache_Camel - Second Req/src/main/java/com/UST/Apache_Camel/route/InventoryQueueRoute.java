package com.UST.Apache_Camel.route;

import com.UST.Apache_Camel.config.ApplicationConstants;
import com.UST.Apache_Camel.config.InventoryUpdateComponents;
import com.UST.Apache_Camel.exception.InventoryValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class InventoryQueueRoute extends RouteBuilder {

    // Configures the Camel route for the Inventory Queue Processor Service to process inventory update messages
    // Consumes messages from the ActiveMQ queue (inventory.update.queue), validates and updates item stock in MongoDB,
    // and stores audit records. Handles exceptions and logs processing steps for debugging and monitoring.
    // The route performs the following steps:
    // 1. Reads messages from ActiveMQ with concurrent consumers
    // 2. Processes the item payload, validates it, and retrieves the item from MongoDB
    // 3. Updates stock details (availableStock, soldOut, damaged) and saves to MongoDB
    // 4. Marks success or failure, stores an audit record in MongoDB, and logs the outcome
    @Override
    public void configure() {
        from(String.format(ApplicationConstants.AMQ_INVENTORY_UPDATE_READ,
                ApplicationConstants.AMQ_INVENTORY_UPDATE_READ_QUEUE))
                .routeId(ApplicationConstants.ROUTE_PROCESS_INVENTORY_QUEUE)
                .onException(Exception.class)
                .handled(true)
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "handleQueueException")
                .end()
                .log("Processing inventory item from queue: ${body}, correlationId: ${header.JMSCorrelationID}")
                .doTry()
                .bean(InventoryUpdateComponents.ItemProcessor.class, "processItem")
                .log("Processing item: ${exchangeProperty.itemId}")
                .bean(InventoryUpdateComponents.ItemProcessor.class, "setItemId")
                .to(String.format(ApplicationConstants.MONGO_ITEM_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .bean(InventoryUpdateComponents.ItemProcessor.class, "validateAndUpdateItem")
                .to(String.format(ApplicationConstants.MONGO_ITEM_SAVE,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_WRITE_COLLECTION))
                .bean(InventoryUpdateComponents.ItemProcessor.class, "markSuccess")
                .doCatch(InventoryValidationException.class)
                .bean(InventoryUpdateComponents.ItemProcessor.class, "markFailure")
                .end()
                .log("Completed processing item ${exchangeProperty.itemId}, itemResult: ${exchangeProperty.itemResult}")
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "storeAuditRecord")
                .to(String.format(ApplicationConstants.MONGO_INVENTORY_AUDIT_INSERT,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_INVENTORY_AUDIT_WRITE_COLLECTION))
                .log("Stored audit result: ${body}");
    }
}