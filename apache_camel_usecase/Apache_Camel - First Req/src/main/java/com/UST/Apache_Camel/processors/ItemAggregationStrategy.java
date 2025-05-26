package com.UST.Apache_Camel.processors;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemAggregationStrategy implements AggregationStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ItemAggregationStrategy.class);

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        List itemResults = oldExchange != null
                ? oldExchange.getProperty("itemResults", List.class)
                : newExchange.getProperty("itemResults", List.class);

        if (itemResults == null) {
            itemResults = new ArrayList<>();
            logger.warn("itemResults was null in aggregation, initialized new list");
        }

        Map<String, Object> itemResult = newExchange.getProperty("itemResult", Map.class);
        if (itemResult != null) {
            itemResults.add(itemResult);
            logger.debug("Added itemResult for item {}: {}", itemResult.get("itemId"), itemResult);
        } else {
            logger.warn("itemResult is null for item, exchange: {}", newExchange);
        }

        Exchange resultExchange = oldExchange != null ? oldExchange : newExchange;
        resultExchange.setProperty("itemResults", itemResults);
        return resultExchange;
    }
}