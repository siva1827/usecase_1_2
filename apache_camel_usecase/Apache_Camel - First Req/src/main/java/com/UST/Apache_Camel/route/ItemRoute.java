package com.UST.Apache_Camel.route;

import com.UST.Apache_Camel.config.ApplicationConstants;
import com.UST.Apache_Camel.config.InventoryUpdateComponents;
import com.UST.Apache_Camel.exception.InventoryValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ItemRoute extends RouteBuilder {

    @Autowired
    private ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(ItemRoute.class);

    @Value("${app.error.itemNotFound:Item not found}")
    private String itemNotFoundMessage;

    @Value("${app.error.categoryNotFound:Category not found}")
    private String categoryNotFoundMessage;

    // Configures Camel routes for the Item Service, handling REST endpoints for item and category management,
    // synchronous inventory updates, and asynchronous inventory updates via ActiveMQ
    // Sets up REST configuration with JSON binding and defines routes for:
    // - Retrieving, deleting, and listing items by ID or category
    // - Creating new items and categories
    // - Synchronous and asynchronous inventory updates
    // Integrates with MongoDB for data storage and ActiveMQ for async processing, with logging for monitoring
    @Override
    public void configure() {
        logger.info("Configuring Camel routes for Item Service");

        JsonDataFormat jsonDataFormat = new JsonDataFormat();
        jsonDataFormat.setObjectMapper(objectMapper.toString());

        restConfiguration()
                .component(ApplicationConstants.REST_COMPONENT)
                .contextPath("/camel")
                .host(ApplicationConstants.REST_HOST)
                .port(ApplicationConstants.REST_PORT)
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("json.in.disableFeatures", "FAIL_ON_UNKNOWN_PROPERTIES");

        // Item management routes
        // GET /camel/mycart/item/{itemId}: Retrieves an item by ID from MongoDB
        // DELETE /camel/mycart/item/{itemId}: Deletes an item by ID after verifying its existence
        rest("/mycart/item/{itemId}")
                .get().to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEM_BY_ID)
                .delete().to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_DELETE_ITEM);

        // Route for retrieving an item by ID
        // Uses GetItemProcessor to set the item ID and process the MongoDB result
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEM_BY_ID)
                .routeId(ApplicationConstants.ROUTE_GET_ITEM_BY_ID)
                .log("Fetching item with ID: ${header.itemId}")
                .bean(InventoryUpdateComponents.GetItemProcessor.class, "setItemId")
                .to(String.format(ApplicationConstants.MONGO_ITEM_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .bean(InventoryUpdateComponents.GetItemProcessor.class, "processResult");

        // Route for deleting an item by ID
        // Checks if the item exists, deletes it from MongoDB, and handles success or not-found cases
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_DELETE_ITEM)
                .routeId(ApplicationConstants.ROUTE_DELETE_ITEM)
                .log("Deleting item with ID: ${header.itemId}")
                .bean(InventoryUpdateComponents.DeleteItemProcessor.class, "setItemId")
                .to(String.format(ApplicationConstants.MONGO_ITEM_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .choice()
                .when(body().isNull())
                .bean(InventoryUpdateComponents.DeleteItemProcessor.class, "handleItemNotFound")
                .otherwise()
                .to(String.format(ApplicationConstants.MONGO_ITEM_DELETE,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_WRITE_COLLECTION))
                .bean(InventoryUpdateComponents.DeleteItemProcessor.class, "handleDeleteSuccess")
                .end();

        // Route for listing items by category
        // GET /camel/mycart/items/{categoryId}?includeSpecial={boolean}: Retrieves items for a category,
        // optionally including special products, using MongoDB aggregation
        rest("/mycart/items/{categoryId}")
                .get()
                .param()
                .name("includeSpecial")
                .type(RestParamType.query)
                .description("Include special items")
                .dataType("boolean")
                .defaultValue("false")
                .endParam()
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEMS_BY_CATEGORY);

        // Processes the category items request
        // Builds an aggregation pipeline and processes the MongoDB results using GetItemsByCategoryProcessor
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEMS_BY_CATEGORY)
                .routeId(ApplicationConstants.ROUTE_GET_ITEMS_BY_CATEGORY)
                .bean(InventoryUpdateComponents.GetItemsByCategoryProcessor.class, "buildAggregationPipeline")
                .to(String.format(ApplicationConstants.MONGO_ITEM_AGGREGATE,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .bean(InventoryUpdateComponents.GetItemsByCategoryProcessor.class, "processResult");

        // Route for creating a new item
        // POST /camel/mycart: Creates a new item after validating it and checking category existence
        rest("/mycart")
                .post()
                .consumes("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_ITEM);

        // Validates and inserts a new item
        // Checks for existing items, validates the category, and inserts into MongoDB
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_ITEM)
                .routeId(ApplicationConstants.ROUTE_POST_NEW_ITEM)
                .log("Received new item: ${body}")
                .bean(InventoryUpdateComponents.PostNewItemProcessor.class, "validateItem")
                .to(String.format(ApplicationConstants.MONGO_ITEM_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .choice()
                .when(body().isNotNull())
                .bean(InventoryUpdateComponents.PostNewItemProcessor.class, "handleExistingItem")
                .otherwise()
                .bean(InventoryUpdateComponents.PostNewItemProcessor.class, "setCategoryId")
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_READ_COLLECTION))
                .choice()
                .when(body().isNull())
                .bean(InventoryUpdateComponents.PostNewItemProcessor.class, "handleInvalidCategory")
                .otherwise()
                .bean(InventoryUpdateComponents.PostNewItemProcessor.class, "prepareItemForInsert")
                .to(String.format(ApplicationConstants.MONGO_ITEM_INSERT,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_WRITE_COLLECTION))
                .bean(InventoryUpdateComponents.PostNewItemProcessor.class, "handleInsertSuccess")
                .endChoice()
                .endChoice();

        // Category management routes
        // POST /camel/mycart/category: Creates a new category
        // DELETE /camel/mycart/category/{categoryId}: Deletes a category by ID
        rest("/mycart/category")
                .post()
                .consumes("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_CATEGORY)
                .delete("/{categoryId}")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_DELETE_CATEGORY);

        // Route for creating a new category
        // Validates and inserts a new category, checking for duplicates
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_CATEGORY)
                .routeId(ApplicationConstants.ROUTE_POST_NEW_CATEGORY)
                .errorHandler(noErrorHandler())
                .log("Received new category: ${body}")
                .bean(InventoryUpdateComponents.PostNewCategoryProcessor.class, "validateCategory")
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_READ_COLLECTION))
                .choice()
                .when(body().isNull())
                .bean(InventoryUpdateComponents.PostNewCategoryProcessor.class, "prepareCategoryForInsert")
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_INSERT,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_WRITE_COLLECTION))
                .bean(InventoryUpdateComponents.PostNewCategoryProcessor.class, "handleInsertSuccess")
                .otherwise()
                .bean(InventoryUpdateComponents.PostNewCategoryProcessor.class, "handleExistingCategory");

        // Route for deleting a category by ID
        // Verifies the category exists before deleting it from MongoDB
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_DELETE_CATEGORY)
                .routeId(ApplicationConstants.ROUTE_DELETE_CATEGORY)
                .log("Deleting category with ID: ${header.categoryId}")
                .bean(InventoryUpdateComponents.DeleteCategoryProcessor.class, "setCategoryId")
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_READ_COLLECTION))
                .choice()
                .when(body().isNull())
                .bean(InventoryUpdateComponents.DeleteCategoryProcessor.class, "handleCategoryNotFound")
                .otherwise()
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_DELETE,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_WRITE_COLLECTION))
                .bean(InventoryUpdateComponents.DeleteCategoryProcessor.class, "handleDeleteSuccess")
                .end();

        // Synchronous inventory update route
        // POST /camel/inventory/update: Updates item stock details synchronously and returns results
        rest("/inventory/update")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_PROCESS_INVENTORY_UPDATE);

        // Orchestrates synchronous inventory update
        // Handles exceptions and finalizes the response using ErrorResponseProcessor and FinalResponseProcessor
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_PROCESS_INVENTORY_UPDATE)
                .routeId(ApplicationConstants.ROUTE_PROCESS_INVENTORY_UPDATE)
                .errorHandler(noErrorHandler())
                .doTry()
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_UPDATE_INVENTORY)
                .doCatch(Exception.class)
                .bean(InventoryUpdateComponents.ErrorResponseProcessor.class)
                .doFinally()
                .bean(InventoryUpdateComponents.FinalResponseProcessor.class)
                .end();

        // Processes synchronous inventory updates
        // Splits the item list, validates and updates each item in MongoDB, and aggregates results
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_UPDATE_INVENTORY)
                .routeId(ApplicationConstants.ROUTE_UPDATE_INVENTORY)
                .bean(InventoryUpdateComponents.PayloadValidationProcessor.class)
                .split(simple("${exchangeProperty.inventoryList}"))
                .aggregationStrategy(new InventoryUpdateComponents.ItemAggregationStrategy())
                .streaming()
                .doTry()
                .bean(InventoryUpdateComponents.ItemProcessor.class, "processItem")
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
                .end()
                .log("Split completed, itemResults: ${exchangeProperty.itemResults}");

        // Asynchronous inventory update route
        // POST /camel/inventory/async-update: Enqueues item updates to ActiveMQ and returns a correlation ID
        rest("/inventory/async-update")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_ASYNC_INVENTORY_UPDATE);

        // Processes asynchronous inventory updates
        // Validates the payload, generates a correlation ID, splits items, and sends them to ActiveMQ in parallel
        // Uses parallelProcessing to handle each item concurrently, improving performance for large item lists
        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_ASYNC_INVENTORY_UPDATE)
                .routeId(ApplicationConstants.ROUTE_ASYNC_INVENTORY_UPDATE)
                .onException(Exception.class)
                .handled(true)
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "handleException")
                .end()
                .bean(InventoryUpdateComponents.PayloadValidationProcessor.class)
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "initializeCorrelationId")
                .split(simple("${exchangeProperty.inventoryList}"))
                .parallelProcessing() // Enables parallel processing for split items
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "prepareQueueMessage")
                .log("Sending item to ActiveMQ queue: ${header.JMSCorrelationID}")
                .to(String.format(ApplicationConstants.AMQ_INVENTORY_UPDATE_WRITE ,
                        ApplicationConstants.AMQ_INVENTORY_UPDATE_WRITE_QUEUE))
                .end()
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "buildEnqueueResponse");
    }
}