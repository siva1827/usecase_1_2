package com.UST.Apache_Camel.route;

import com.UST.Apache_Camel.config.ApplicationConstants;
import com.UST.Apache_Camel.config.InventoryUpdateComponents;
import com.UST.Apache_Camel.exception.InventoryValidationException;
import com.mongodb.MongoException;
import org.apache.camel.Exchange;
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

    @Override
    public void configure() {
        logger.info("Configuring Camel routes for Item Service");

        // Global exception handling for all routes
        onException(InventoryValidationException.class)
                .handled(true)
                .bean(InventoryUpdateComponents.ErrorResponseProcessor.class, "processValidationError")
                .log("Validation error: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400));

        onException(MongoException.class)
                .handled(true)
                .bean(InventoryUpdateComponents.ErrorResponseProcessor.class, "processMongoError")
                .log("MongoDB error: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500));

        onException(Throwable.class)
                .handled(true)
                .bean(InventoryUpdateComponents.ErrorResponseProcessor.class, "processGenericError")
                .log("Unexpected error: ${exception.message} at ${exception.stacktrace}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500));

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
        rest("/mycart/item/{itemId}")
                .get().to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEM_BY_ID)
                .delete().to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_DELETE_ITEM);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEM_BY_ID)
                .routeId(ApplicationConstants.ROUTE_GET_ITEM_BY_ID)
                .log("Fetching item with ID: ${header.itemId}")
                .bean(InventoryUpdateComponents.GetItemProcessor.class, "setItemId")
                .to(String.format(ApplicationConstants.MONGO_ITEM_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .bean(InventoryUpdateComponents.GetItemProcessor.class, "processResult");

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

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEMS_BY_CATEGORY)
                .routeId(ApplicationConstants.ROUTE_GET_ITEMS_BY_CATEGORY)
                .bean(InventoryUpdateComponents.GetItemsByCategoryProcessor.class, "buildAggregationPipeline")
                .to(String.format(ApplicationConstants.MONGO_ITEM_AGGREGATE,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .bean(InventoryUpdateComponents.GetItemsByCategoryProcessor.class, "processResult");

        rest("/mycart")
                .post()
                .consumes("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_ITEM);

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

        rest("/mycart/category")
                .post()
                .consumes("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_CATEGORY)
                .delete("/{categoryId}")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_DELETE_CATEGORY);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_CATEGORY)
                .routeId(ApplicationConstants.ROUTE_POST_NEW_CATEGORY)
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

        rest("/inventory/update")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_PROCESS_INVENTORY_UPDATE);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_PROCESS_INVENTORY_UPDATE)
                .routeId(ApplicationConstants.ROUTE_PROCESS_INVENTORY_UPDATE)
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_UPDATE_INVENTORY)
                .bean(InventoryUpdateComponents.FinalResponseProcessor.class);

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

        rest("/inventory/async-update")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_ASYNC_INVENTORY_UPDATE);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_ASYNC_INVENTORY_UPDATE)
                .routeId(ApplicationConstants.ROUTE_ASYNC_INVENTORY_UPDATE)
                .bean(InventoryUpdateComponents.PayloadValidationProcessor.class)
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "initializeCorrelationId")
                .split(simple("${exchangeProperty.inventoryList}"))
                .parallelProcessing()
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "prepareQueueMessage")
                .log("Sending item to ActiveMQ queue: ${header.JMSCorrelationID}")
                .to(String.format(ApplicationConstants.AMQ_INVENTORY_UPDATE_WRITE,
                        ApplicationConstants.AMQ_INVENTORY_UPDATE_WRITE_QUEUE))
                .end()
                .bean(InventoryUpdateComponents.AsyncInventoryUpdateProcessor.class, "buildEnqueueResponse");
    }
}