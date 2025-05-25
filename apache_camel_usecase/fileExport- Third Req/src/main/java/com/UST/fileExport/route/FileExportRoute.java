package com.UST.fileExport.route;

import com.UST.fileExport.config.ApplicationConstants;
import com.UST.fileExport.processor.ControlRefProcessor;
import com.UST.fileExport.processor.ItemProcessor;
import com.UST.fileExport.model.ReviewXml;
import com.UST.fileExport.model.StoreJson;
import com.UST.fileExport.model.TrendXml;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class FileExportRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("timer:fileExport?period={{scheduler.interval}}")
                .routeId("fileExport")
                .bean(ItemProcessor.class, "setCurrentTimestamp")
                .to(ApplicationConstants.DIRECT_FETCH_CONTROL_REF)
                .to(ApplicationConstants.DIRECT_PROCESS_ITEMS)
                .log("File export completed");

        from(ApplicationConstants.DIRECT_FETCH_CONTROL_REF)
                .routeId("fetchControlRef")
                .bean(ControlRefProcessor.class, "fetchControlRefs")
                .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_CONTROL_REF_COLLECTION + "&operation=findAll")
                .bean(ControlRefProcessor.class, "processControlRefs")
                .log("Fetched controlRefMap with ${exchangeProperty.controlRefMap.size()} entries");

        from(ApplicationConstants.DIRECT_PROCESS_ITEMS)
                .routeId("processItems")
                .bean(ItemProcessor.class, "prepareItemQuery")
                .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_ITEM_COLLECTION + "&operation=findAll")
                .bean(ItemProcessor.class, "filterValidItems")
                .bean(ItemProcessor.class, "logFetchedItems")
                .split(body())
                .bean(ItemProcessor.class, "enrichWithCategory")
                .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_CATEGORY_COLLECTION + "&operation=findById")
                .setProperty("category", simple("${body}"))
                .bean(ItemProcessor.class, "mapItemData")
                .multicast().parallelProcessing()
                    .to("direct:writeTrendXml", "direct:writeReviewXml", "direct:writeStoreJson")
                .end()
                .to("direct:updateControlRef");

        from("direct:writeTrendXml")
                .routeId("writeTrendXml")
                .throttle(1).timePeriodMillis(60000).asyncDelayed()
                .bean(ItemProcessor.class, "prepareTrendXml")
                .choice()
                .when(body().isNotNull())
                .marshal(new org.apache.camel.converter.jaxb.JaxbDataFormat(TrendXml.class.getPackage().getName()))
                .to("file:C:/export/trend?fileName=${header.CamelFileName}")
                .log("Saved trend XML: ${header.CamelFileName}")
                .otherwise()
                .log("Skipping null trend XML")
                .end();

        from("direct:writeReviewXml")
                .routeId("writeReviewXml")
                .throttle(1).timePeriodMillis(60000).asyncDelayed()
                .bean(ItemProcessor.class, "prepareReviewXml")
                .choice()
                .when(body().isNotNull())
                .marshal(new org.apache.camel.converter.jaxb.JaxbDataFormat(ReviewXml.class.getPackage().getName()))
                .to("file:C:/export/reviews?fileName=${header.CamelFileName}")
                .log("Saved review XML: ${header.CamelFileName}")
                .otherwise()
                .log("Skipping null review XML")
                .end();

        from("direct:writeStoreJson")
                .routeId("writeStoreJson")
                .throttle(1).timePeriodMillis(60000).asyncDelayed()
                .bean(ItemProcessor.class, "prepareStoreJson")
                .choice()
                    .when(body().isNotNull())
                        .marshal().json(JsonLibrary.Jackson)
                        .to("file:C:/export/storefront?fileName=${header.CamelFileName}")
                        .log("Saved store JSON: ${header.CamelFileName}")
                    .otherwise()
                        .log("Skipping null store JSON")
                .end();

        from("direct:updateControlRef")
                .routeId("updateControlRef")
                .setHeader("ControlRefOperation", constant("update"))
                .setHeader("ControlRefId", simple("${exchangeProperty.itemId}"))
                .bean(ControlRefProcessor.class, "updateControlRef")
                .choice()
                    .when(body().isNotNull())
                        .to("mongodb:mongoBean?database={{camel.component.mongodb.database}}&collection=" + ApplicationConstants.MONGO_CONTROL_REF_COLLECTION + "&operation=save")
                        .log("ControlRef updated for item: ${exchangeProperty.itemId}")
                    .otherwise()
                        .log("Skipped ControlRef update for item: ${exchangeProperty.itemId} (null body)")
                .end();
    }
}