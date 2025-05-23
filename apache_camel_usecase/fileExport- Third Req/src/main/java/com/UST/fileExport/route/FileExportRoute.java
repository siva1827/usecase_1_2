package com.UST.fileExport.route;

import com.UST.fileExport.config.ApplicationConstants;
import com.UST.fileExport.processor.ControlRefProcessor;
import com.UST.fileExport.processor.ItemProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.springframework.stereotype.Component;

@Component
public class FileExportRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
        jaxbDataFormat.setContextPath("com.UST.fileExport.model");
        JsonDataFormat jsonDataFormat = new JsonDataFormat();

        // Handle MongoDB exceptions
        onException(org.apache.camel.component.mongodb.CamelMongoDbException.class)
                .handled(true)
                .log("MongoDB error: ${exception.message}")
                .setBody(simple("{\"error\": \"MongoDB operation failed: ${exception.message}\"}"))
                .to("log:error?level=ERROR");

        // Handle generic exceptions
        onException(Exception.class)
                .handled(true)
                .log("File export failed: ${exception.message}")
                .to("log:error?level=ERROR");

        from("timer:fileExport?period={{scheduler.interval}}")
                .routeId("fileExportScheduler")
                .setHeader("ControlRefId", constant("fileExport"))
                .to(ApplicationConstants.DIRECT_FETCH_CONTROL_REF)
                // Initialize ControlRef if missing
                .choice()
                    .when(simple("${exchangeProperty.initControlRef} != null"))
                        .log("Initializing ControlRef document")
                        .setBody(simple("${exchangeProperty.initControlRef}"))
                        .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_CONTROL_REF_COLLECTION + "&operation=save")
                        .log("Initialized ControlRef with default timestamp: ${body}")
                        .setBody(constant(null)) // Clear save result
                        .setHeader("ControlRefId", constant("fileExport")) // Reset ControlRefId
                        .to(ApplicationConstants.DIRECT_FETCH_CONTROL_REF) // Re-fetch to get actual lastProcessTs
                    .otherwise()
                        .log("ControlRef exists, proceeding with lastProcessTs: ${exchangeProperty.lastProcessTsString}")
                .end()
                .choice()
                    .when(simple("${exchangeProperty.lastProcessTsString} != null"))
                        .to(ApplicationConstants.DIRECT_PROCESS_ITEMS)
                    .otherwise()
                        .log("WARNING: lastProcessTsString is null, skipping item processing")
                        .stop()
                .end()
                // Update ControlRef after processing
                .setHeader("ControlRefOperation", constant("update"))
                .setBody(constant(null)) // Clear body to avoid item data
                .bean(ControlRefProcessor.class, "updateLastProcessTs")
                .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_CONTROL_REF_COLLECTION + "&operation=save")
                .log("Saved ControlRef update: ${body}")
                .setBody(constant(null)) // Clear UpdateResult
                .log("File export completed");

        from(ApplicationConstants.DIRECT_FETCH_CONTROL_REF)
                .routeId("fetchControlRef")
                .bean(ControlRefProcessor.class, "fetchLastProcessTs")
                .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_CONTROL_REF_COLLECTION + "&operation=findById")
                .bean(ControlRefProcessor.class, "processControlRef")
                .log("Fetched lastProcessTs: ${exchangeProperty.lastProcessTsString}");

        from(ApplicationConstants.DIRECT_PROCESS_ITEMS)
                .routeId("processItems")
                .bean(ItemProcessor.class, "prepareItemQuery")
                .log("MongoDB query: ${body}")
                .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_ITEM_COLLECTION + "&operation=findAll")
                .bean(ItemProcessor.class, "filterValidItems")
                .bean(ItemProcessor.class, "logFetchedItems")
                .log("Retrieved ${body.size()} items from MongoDB")
                .choice()
                    .when(simple("${body} != null && ${body.size()} > 0"))
                        .split(body()).parallelProcessing()
                            .filter(simple("${body} != null"))
                            .setProperty("item", simple("${body}"))
                            .bean(ItemProcessor.class, "enrichWithCategory")
                            .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_CATEGORY_COLLECTION + "&operation=findById")
                            .log("Category query result: ${body}")
                            .setProperty("category", simple("${body}"))
                            .log("Processing item: ${exchangeProperty.item[_id]}, category: ${exchangeProperty.category[name]}")
                            .multicast().parallelProcessing()
                                .to("direct:trendAnalyzer", "direct:reviewAggregator", "direct:storeFront")
                            .end()
                        .end()
                .end()
                .log("No valid items to process");

        from("direct:trendAnalyzer")
                .routeId("trendAnalyzer")
                .bean(ItemProcessor.class, "prepareTrendAnalyzerXml")
                .choice()
                    .when(body().isNotNull())
                        .marshal(jaxbDataFormat)
                        .to("file:C:/export/trend?fileName=${header.CamelFileName}")
                        .log("Saved Trend Analyzer file: ${header.CamelFileName}")
                .end()
                .log("Skipping null Trend Analyzer body");

        from("direct:reviewAggregator")
                .routeId("reviewAggregator")
                .bean(ItemProcessor.class, "prepareReviewAggregatorXml")
                .choice()
                    .when(body().isNotNull())
                        .marshal(jaxbDataFormat)
                        .to("file:C:/export/reviews?fileName=${header.CamelFileName}")
                        .log("Saved Review Aggregator file: ${header.CamelFileName}")
                .end()
                .log("Skipping null Review Aggregator body");

        from("direct:storeFront")
                .routeId("storeFront")
                .bean(ItemProcessor.class, "prepareStoreFrontJson")
                .choice()
                    .when(body().isNotNull())
                        .marshal(jsonDataFormat)
                        .to("file:C:/export/storefront?fileName=${header.CamelFileName}")
                        .log("Saved StoreFront file: ${header.CamelFileName}")
                .end()
                .log("Skipping null StoreFront body");
    }
}