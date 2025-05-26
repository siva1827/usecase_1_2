package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.exception.InventoryValidationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PayloadValidationProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(PayloadValidationProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        Map<String, Object> bodyMap;

        try {
            if (body instanceof Map) {
                bodyMap = (Map<String, Object>) body;
            } else {
                exchange.getIn().setBody(body, String.class);
                bodyMap = exchange.getIn().getBody(Map.class);
            }
        } catch (Exception e) {
            throw new InventoryValidationException("Invalid JSON payload: " + e.getMessage());
        }

        if (bodyMap == null || !bodyMap.containsKey("items")) {
            throw new InventoryValidationException("Missing 'items' in inventory payload.");
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) bodyMap.get("items");
        if (items.isEmpty()) {
            throw new InventoryValidationException("Inventory items list is empty.");
        }

        List<Map<String, Object>> itemResults = new ArrayList<>();
        exchange.setProperty("itemResults", itemResults);
        exchange.setProperty("inventoryList", items);
        logger.info("Initialized itemResults, processing {} items", items.size());
    }
}