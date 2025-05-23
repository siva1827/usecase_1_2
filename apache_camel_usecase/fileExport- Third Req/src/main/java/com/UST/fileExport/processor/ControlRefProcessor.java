package com.UST.fileExport.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

public class ControlRefProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(ControlRefProcessor.class);
    private static final String DEFAULT_ID = "fileExport";
    private static final String OPERATION_HEADER = "ControlRefOperation";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime DEFAULT_LAST_PROCESS_TS = LocalDateTime.of(2025, 5, 22, 0, 0, 0);

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = exchange.getIn().getHeader(OPERATION_HEADER, "fetch", String.class);
        String id = exchange.getIn().getHeader("ControlRefId", DEFAULT_ID, String.class); // Changed to ControlRefId

        if ("update".equalsIgnoreCase(operation)) {
            updateLastProcessTs(exchange, id);
        } else {
            fetchLastProcessTs(exchange, id);
        }
    }

    public void fetchLastProcessTs(Exchange exchange, String id) {
        Map<String, Object> query = new HashMap<>();
        String controlRefId = id != null ? id : DEFAULT_ID;
        query.put("_id", controlRefId);
        exchange.getIn().setHeader("ControlRefId", controlRefId); // Ensure header persists
        exchange.getIn().setBody(query);
        exchange.setProperty("controlRefQuery", query);
        logger.debug("Prepared query to fetch lastProcessTs: {}", query);
    }

    public void updateLastProcessTs(Exchange exchange, String id) {
        LocalDateTime now = LocalDateTime.now();
        String formattedTs = now.format(FORMATTER);

        Document doc = new Document("_id", id != null ? id : DEFAULT_ID)
                .append("lastProcessTs", formattedTs);

        exchange.getIn().setHeader("ControlRefId", id != null ? id : DEFAULT_ID); // Ensure header for save
        exchange.getIn().setBody(doc);
        logger.info("Prepared update for _id: {} with lastProcessTs: {}", id != null ? id : DEFAULT_ID, formattedTs);
    }

    public void processControlRef(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        Map<String, Object> query = exchange.getProperty("controlRefQuery", Map.class);
        LocalDateTime lastProcessTs = DEFAULT_LAST_PROCESS_TS;
        String lastProcessTsString = DEFAULT_LAST_PROCESS_TS.format(FORMATTER);

        logger.debug("MongoDB response for ControlRef: {}", body);

        if (body instanceof Document doc) {
            logger.debug("Received ControlRef document: {}", doc.toJson());
            String ts = doc.getString("lastProcessTs");

            if (ts != null && !ts.trim().isEmpty()) {
                try {
                    lastProcessTs = LocalDateTime.parse(ts);
                    lastProcessTsString = ts;
                    logger.info("Fetched ControlRef: _id={}, lastProcessTs={}", doc.getString("_id"), ts);
                } catch (DateTimeParseException e) {
                    logger.warn("Failed to parse lastProcessTs: {}, using default: {}", ts, DEFAULT_LAST_PROCESS_TS);
                }
            } else {
                logger.warn("lastProcessTs is null or empty in ControlRef document: {}, using default: {}", doc, DEFAULT_LAST_PROCESS_TS);
            }
        } else {
            logger.error("Expected Document for ControlRef but got: {}, query was: {}, using default: {}", 
                        body != null ? body.getClass().getSimpleName() : "null", query, DEFAULT_LAST_PROCESS_TS);
            Document initDoc = new Document("_id", DEFAULT_ID)
                    .append("lastProcessTs", lastProcessTs);
            exchange.setProperty("initControlRef", initDoc);
        }

        exchange.setProperty("lastProcessTs", lastProcessTs);
        exchange.setProperty("lastProcessTsString", lastProcessTs);
        logger.debug("Set exchange properties: lastProcessTs={}, lastProcessTsString={}", lastProcessTs, lastProcessTsString);
    }
}