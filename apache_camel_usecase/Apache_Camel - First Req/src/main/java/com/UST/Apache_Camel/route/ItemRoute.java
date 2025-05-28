package com.UST.Apache_Camel.route;

import com.UST.Apache_Camel.config.ApplicationConstants;
import com.UST.Apache_Camel.exception.InventoryValidationException;
import com.UST.Apache_Camel.model.Category;
import com.UST.Apache_Camel.model.InventoryUpdateRequest;
import com.UST.Apache_Camel.model.Item;
import com.UST.Apache_Camel.processors.*;
import com.mongodb.MongoException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.jms.JMSException;

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


        // Exception handling for JMS errors
        onException(UncategorizedJmsException.class, JMSException.class)
                .handled(true)
                .bean(AsyncInventoryUpdateProcessor.class, "handleException")
                .stop()
                .end();

        // Global exception handling for all routes
        onException(InventoryValidationException.class)
                .handled(true)
                .bean(ErrorResponseProcessor.class, "processValidationError")
                .log("Validation error: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400));


        // Exception Handling for Mongo Exception
        onException(MongoException.class)
                .handled(true)
                .bean(ErrorResponseProcessor.class, "processMongoError")
                .log("MongoDB error: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500));


        // Exception Handler for Generic Errors
        onException(Throwable.class)
                .handled(true)
                .bean(ErrorResponseProcessor.class, "processGenericError")
                .log("Unexpected error: ${exception.message} at ${exception.stacktrace}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500));

        restConfiguration()
                .component(ApplicationConstants.REST_COMPONENT)
                .contextPath("/camel")
                .host(ApplicationConstants.REST_HOST)
                .port(ApplicationConstants.REST_PORT)
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("json.in.disableFeatures", "FAIL_ON_UNKNOWN_PROPERTIES");

        rest("/mycart/item/{itemId}")
                .get()
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEM_BY_ID);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_GET_ITEM_BY_ID)
                .routeId(ApplicationConstants.ROUTE_GET_ITEM_BY_ID)
                .onException(Exception.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(simple("Error fetching item: ${exception.message}"))
                .log("Error fetching item: ${exception.message}")
                .end()
                .log("Fetching item with ID: ${header.itemId}")
                .bean(GetItemProcessor.class, "setItemId")
                .to(String.format(ApplicationConstants.MONGO_ITEM_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .bean(GetItemProcessor.class, "processResult")
                .choice()
                .when(exchangeProperty("itemNotFound").isNull())
                .bean(GetItemProcessor.class, "setCategoryId")
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_READ_COLLECTION))
                .bean(GetItemProcessor.class, "processCategoryResult")
                .endChoice();

        //get items by categoryid

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
                .onException(Exception.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(simple("Error fetching items: ${exception.message}"))
                .log("Error fetching items: ${exception.message}")
                .end()
                .bean(GetItemsByCategoryProcessor.class, "buildAggregationPipeline")
                .to(String.format(ApplicationConstants.MONGO_ITEM_AGGREGATE,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .bean(GetItemsByCategoryProcessor.class, "processResult")
                .split(body())
                .aggregationStrategy(new ItemResponseCatAggregationStrategy())
                .bean(GetItemsByCategoryProcessor.class, "transformItem")
                .end()
                .bean(GetItemsByCategoryProcessor.class, "buildFinalResponse");

        //post new item
        rest("/mycart")
                .post()
                .consumes("application/json")
                .type(Item.class) // Ensure JSON is deserialized to Item
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_ITEM);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_ITEM)
                .routeId(ApplicationConstants.ROUTE_POST_NEW_ITEM)
                .onException(InventoryValidationException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setBody(simple("${exception.message}"))
                .log("Validation error: ${exception.message}")
                .end()
                .log("Received new item: ${body}")
                .bean(PostNewItemProcessor.class, "validateItem")
                .to(String.format(ApplicationConstants.MONGO_ITEM_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .choice()
                .when(body().isNotNull())
                .bean(PostNewItemProcessor.class, "handleExistingItem")
                .otherwise()
                .bean(PostNewItemProcessor.class, "setCategoryId")
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_READ_COLLECTION))
                .choice()
                .when(body().isNull())
                .bean(PostNewItemProcessor.class, "handleInvalidCategory")
                .otherwise()
                .bean(PostNewItemProcessor.class, "prepareItemForInsert")
                .to(String.format(ApplicationConstants.MONGO_ITEM_INSERT,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_WRITE_COLLECTION))
                .bean(PostNewItemProcessor.class, "handleInsertSuccess")
                .endChoice()
                .endChoice();


        // Route for sync update
        rest("/inventory/update")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_PROCESS_INVENTORY_UPDATE);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_PROCESS_INVENTORY_UPDATE)
                .routeId(ApplicationConstants.ROUTE_PROCESS_INVENTORY_UPDATE)
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_UPDATE_INVENTORY)
                .process(new FinalResponseProcessor());

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_UPDATE_INVENTORY)
                .routeId(ApplicationConstants.ROUTE_UPDATE_INVENTORY)
                .process(new PayloadValidationProcessor())
                .split(simple("${exchangeProperty.inventoryList}"))
                .aggregationStrategy(new ItemAggregationStrategy())
                .streaming()
                .doTry()
                .bean(ItemProcessor.class, "processItem")
                .bean(ItemProcessor.class, "setItemId")
                .to(String.format(ApplicationConstants.MONGO_ITEM_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_READ_COLLECTION))
                .bean(ItemProcessor.class, "validateAndUpdateItem")
                .to(String.format(ApplicationConstants.MONGO_ITEM_SAVE,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_ITEM_WRITE_COLLECTION))
                .bean(ItemProcessor.class, "markSuccess")
                .doCatch(InventoryValidationException.class)
                .bean(ItemProcessor.class, "markFailure")
                .end()
                .log("Completed processing item ${exchangeProperty.itemId}, itemResult: ${exchangeProperty.itemResult}")
                .end()
                .log("Split completed, itemResults: ${exchangeProperty.itemResults}");

        // Route for async update
        rest("/inventory/async-update")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_ASYNC_INVENTORY_UPDATE);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_ASYNC_INVENTORY_UPDATE)
                .routeId(ApplicationConstants.ROUTE_ASYNC_INVENTORY_UPDATE)
                .doTry()
                .bean(PayloadValidationProcessor.class)
                .bean(AsyncInventoryUpdateProcessor.class, "initializeCorrelationId")
                .bean(AsyncInventoryUpdateProcessor.class, "prepareQueueMessage")
                .log("Sending item list to ActiveMQ queue: ${header.JMSCorrelationID}")
                .to(String.format(ApplicationConstants.AMQ_INVENTORY_UPDATE_WRITE,
                        ApplicationConstants.AMQ_INVENTORY_UPDATE_WRITE_QUEUE))
                .bean(AsyncInventoryUpdateProcessor.class, "buildEnqueueResponse")
                .doCatch(InventoryValidationException.class, JMSException.class)
                .bean(AsyncInventoryUpdateProcessor.class, "handleException")
                .doCatch(Exception.class)
                .bean(AsyncInventoryUpdateProcessor.class, "handleException")
                .end();

        //----------------------------------------------------------------------------------------------------


        //post new Category
        rest("/mycart/category")
                .post()
                .consumes("application/json")
                .type(Category.class)
                .to(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_CATEGORY);

        from(ApplicationConstants.DIRECT_PREFIX + ApplicationConstants.ENDPOINT_POST_NEW_CATEGORY)
                .routeId(ApplicationConstants.ROUTE_POST_NEW_CATEGORY)
                .log("Received new category: ${body}")
                .bean(PostNewCategoryProcessor.class, "validateCategory")
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_FIND_BY_ID,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_READ_COLLECTION))
                .choice()
                .when(body().isNull())
                .bean(PostNewCategoryProcessor.class, "prepareCategoryForInsert")
                .to(String.format(ApplicationConstants.MONGO_CATEGORY_INSERT,
                        ApplicationConstants.MONGO_DATABASE, ApplicationConstants.MONGO_CATEGORY_WRITE_COLLECTION))
                .bean(PostNewCategoryProcessor.class, "handleInsertSuccess")
                .otherwise()
                .bean(PostNewCategoryProcessor.class, "handleExistingCategory");
    }



}