package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import com.UST.Apache_Camel.model.ItemResponseCat;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ItemResponseCatAggregationStrategy implements AggregationStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ItemResponseCatAggregationStrategy.class);

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Exchange resultExchange = oldExchange != null ? oldExchange : newExchange;
        String routeId = newExchange.getFromRouteId();
        logger.info("Aggregating for routeId: {}, expected ROUTE_GET_ITEMS_BY_CATEGORY: {}, oldExchange exists: {}", 
                routeId, ApplicationConstants.ROUTE_GET_ITEMS_BY_CATEGORY, oldExchange != null);

        List<ItemResponseCat> itemResults = oldExchange != null
                ? oldExchange.getProperty("itemResults", List.class)
                : new ArrayList<>();
        if (itemResults == null) {
            itemResults = new ArrayList<>();
            logger.warn("itemResults was null in aggregation for route {}, initialized new list", routeId);
        }

        ItemResponseCat itemResult = newExchange.getProperty("itemResult", ItemResponseCat.class);
        if (itemResult != null) {
            itemResults.add(itemResult);
            logger.debug("Added ItemResponseCat for item {} to aggregation for route {}", itemResult.getId(), routeId);
        } else {
            logger.warn("itemResult (ItemResponseCat) is null for aggregation, newExchange properties: {}, body: {}", 
                    newExchange.getProperties(), newExchange.getIn().getBody());
        }

        resultExchange.setProperty("itemResults", itemResults);
        resultExchange.getIn().setBody(itemResults); // Set body for buildFinalResponse
        logger.debug("ItemResponseCat aggregation: {} items aggregated for route {}", itemResults.size(), routeId);

        return resultExchange;
    }
}