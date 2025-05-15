package com.UST.Apache_Camel.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.UncategorizedJmsException;

import javax.jms.JMSException;
import java.util.Map;
import java.util.UUID;

public class AsyncInventoryUpdateProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(AsyncInventoryUpdateProcessor.class);

    public void handleException(Exchange exchange) {
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        String correlationId = exchange.getProperty("correlationId", String.class);
        String errorMessage;
        int httpStatus;

        if (exception instanceof UncategorizedJmsException || exception instanceof JMSException) {
            errorMessage = "Something went wrong with ActiveMQ";
            httpStatus = 503;
            logger.warn("JMS error for correlationId {}: {}", correlationId, errorMessage);
        } else {
            errorMessage = exception != null ? exception.getMessage() : "Unknown error";
            httpStatus = 500;
            logger.error("Error in async update for correlationId {}: {}", correlationId, errorMessage, exception);
        }

        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, httpStatus);
        exchange.getIn().setBody(Map.of(
                "status", "error",
                "message", errorMessage
        ));
    }

    public void initializeCorrelationId(Exchange exchange) {
        String correlationId = UUID.randomUUID().toString();
        exchange.setProperty("correlationId", correlationId);
        exchange.getIn().setHeader("JMSCorrelationID", correlationId);
        logger.info("Generated correlationId: {}", correlationId);
    }

    public void prepareQueueMessage(Exchange exchange) {
        Map<String, Object> item = exchange.getIn().getBody(Map.class);
        exchange.getIn().setBody(item);
        exchange.getIn().setHeader("JMSCorrelationID", exchange.getProperty("correlationId"));
        logger.debug("Prepared queue message for itemId: {}", item.get("_id"));
    }

    public void buildEnqueueResponse(Exchange exchange) {
        String correlationId = exchange.getProperty("correlationId", String.class);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
        exchange.getIn().setBody(Map.of(
                "status", "enqueued",
                "correlationId", correlationId
        ));
        logger.info("Enqueued items with correlationId: {}", correlationId);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
    }
}