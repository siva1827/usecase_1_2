package com.UST.fileExport.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ControlRefProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(ControlRefProcessor.class);
    private static final String OPERATION_HEADER = "ControlRefOperation";
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = exchange.getIn().getHeader(OPERATION_HEADER, "fetch", String.class);
        String itemId = exchange.getIn().getHeader("ControlRefId", String.class);

        if ("update".equalsIgnoreCase(operation)) {
            updateControlRef(exchange, itemId);
        } else {
            fetchControlRefs(exchange);
        }
    }

    public void fetchControlRefs(Exchange exchange) {
        Map<String, Object> query = new HashMap<>();
        exchange.getIn().setBody(query);
        exchange.setProperty("controlRefQuery", query);
        logger.debug("Prepared query to fetch all ControlRefs: {}", query);
    }

    public void processControlRefs(Exchange exchange) {
        List<Document> controlRefs = exchange.getIn().getBody(List.class);
        Map<String, Date> controlRefMap = new HashMap<>();

        if (controlRefs != null) {
            for (Document doc : controlRefs) {
                String itemId = doc.getString("_id");
                String lastProcessTs = doc.getString("lastProcessTs");
                if (itemId != null && lastProcessTs != null) {
                    try {
                        synchronized (FORMATTER) {
                            Date lastProcessDate = FORMATTER.parse(lastProcessTs);
                            controlRefMap.put(itemId, lastProcessDate);
                            logger.debug("Mapped ControlRef: itemId={}, lastProcessTs={}", itemId, lastProcessTs);
                        }
                    } catch (ParseException e) {
                        logger.error("Invalid lastProcessTs format for itemId {}: {}", itemId, lastProcessTs, e);
                    }
                }
            }
            logger.info("Fetched {} ControlRef documents", controlRefMap.size());
        } else {
            logger.warn("No ControlRef documents found, proceeding with empty map");
        }

        exchange.setProperty("controlRefMap", controlRefMap);
        logger.debug("Set controlRefMap with {} entries", controlRefMap.size());
    }

    public void updateControlRef(Exchange exchange, String itemId) {
        if (itemId == null) {
            logger.warn("No itemId provided for ControlRef update, skipping");
            exchange.getIn().setBody(null);
            return;
        }

        String currentTs = exchange.getProperty("currentTs", String.class);
        if (currentTs == null) {
            logger.warn("currentTs is null, cannot update ControlRef for itemId: {}", itemId);
            exchange.getIn().setBody(null);
            return;
        }

        Document doc = new Document("_id", itemId)
                .append("lastProcessTs", currentTs);

        exchange.getIn().setBody(doc);
        logger.info("Prepared ControlRef update for itemId: {}, lastProcessTs: {}", itemId, currentTs);
    }
}