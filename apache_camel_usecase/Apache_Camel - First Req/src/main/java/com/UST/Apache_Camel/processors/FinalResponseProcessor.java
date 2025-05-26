package com.UST.Apache_Camel.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FinalResponseProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(FinalResponseProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        List<Map<String, Object>> itemResults = exchange.getProperty("itemResults", List.class);
        if (itemResults == null) {
            itemResults = new ArrayList<>();
            logger.warn("itemResults is null in final response, initializing as empty list");
        }
        String status = itemResults.stream().allMatch(r -> "success".equals(r.get("status"))) ? "completed" : "partial";
        logger.info("Final response itemResults: {}, status: {}", itemResults, status);
        exchange.getMessage().setBody(Map.of(
                "status", status,
                "results", itemResults
        ));
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
    }
}