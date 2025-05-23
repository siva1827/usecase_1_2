package com.UST.fileExport.processor;

import com.UST.fileExport.model.*;
import org.apache.camel.Exchange;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
public class ItemProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ItemProcessor.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime DEFAULT_LAST_PROCESS_TS = LocalDateTime.of(2025, 5, 22, 0, 0, 0);

    private LocalDateTime parseDate(String dateStr) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateStr, DATE_TIME_FORMATTER);
            logger.debug("Parsed date: {} to {}", dateStr, dateTime);
            return dateTime;
        } catch (DateTimeParseException e) {
            throw new DateTimeParseException("Invalid date format: " + dateStr + ", expected yyyy-MM-dd HH:mm:ss", dateStr, 0, e);
        }
    }

    public void prepareItemQuery(Exchange exchange) {
        String lastProcessTsString = exchange.getProperty("lastProcessTsString", String.class);
        Map<String, Object> query = new HashMap<>();

        if (lastProcessTsString != null) {
            try {
                LocalDateTime parsedTs = parseDate(lastProcessTsString);
                query.put("lastUpdateDate", Map.of("$gt", lastProcessTsString)); // Use the string directly
                logger.info("Prepared item query with lastProcessTsString: {}, parsed: {}, query: {}", lastProcessTsString, parsedTs, query);
            } catch (DateTimeParseException e) {
                logger.warn("Invalid lastProcessTsString format: {}, using fallback to {}", lastProcessTsString, DEFAULT_LAST_PROCESS_TS.format(DATE_TIME_FORMATTER));
                query.put("lastUpdateDate", Map.of("$gt", DEFAULT_LAST_PROCESS_TS.format(DATE_TIME_FORMATTER)));
            }
        } else {
            logger.warn("lastProcessTsString is null, using fallback to {}", DEFAULT_LAST_PROCESS_TS.format(DATE_TIME_FORMATTER));
            query.put("lastUpdateDate", Map.of("$gt", DEFAULT_LAST_PROCESS_TS.format(DATE_TIME_FORMATTER)));
        }

        exchange.getIn().setBody(query);
    }

    public void filterValidItems(Exchange exchange) {
        List<Document> items = exchange.getIn().getBody(List.class);
        String lastProcessTsString = exchange.getProperty("lastProcessTsString", String.class);
        List<Document> validItems = new ArrayList<>();

        if (items == null || items.isEmpty()) {
            logger.info("No items fetched from MongoDB");
            exchange.getIn().setBody(validItems);
            return;
        }

        if (lastProcessTsString == null) {
            logger.warn("lastProcessTsString is null, cannot filter items, returning empty list");
            exchange.getIn().setBody(validItems);
            return;
        }

        LocalDateTime lastProcessTs;
        try {
            lastProcessTs = parseDate(lastProcessTsString);
        } catch (DateTimeParseException e) {
            logger.warn("Invalid lastProcessTsString format: {}, using fallback {}", lastProcessTsString, DEFAULT_LAST_PROCESS_TS);
            lastProcessTs = DEFAULT_LAST_PROCESS_TS;
        }

        for (Document item : items) {
            String id = item.getString("_id");
            String lastUpdateDate = item.getString("lastUpdateDate");

            if (lastUpdateDate == null) {
                logger.warn("Skipping item {}: lastUpdateDate is null", id);
                continue;
            }

            try {
                LocalDateTime itemUpdateDate = parseDate(lastUpdateDate);
                if (itemUpdateDate.isAfter(lastProcessTs)) {
                    validItems.add(item);
                    logger.info("Valid item: {} with lastUpdateDate: {} (parsed: {})", id, lastUpdateDate, itemUpdateDate);
                } else {
                    logger.debug("Skipping item {}: lastUpdateDate {} (parsed: {}) not after lastProcessTs {} (parsed: {})",
                            id, lastUpdateDate, itemUpdateDate, lastProcessTsString, lastProcessTs);
                }
            } catch (DateTimeParseException e) {
                logger.warn("Skipping item {}: invalid lastUpdateDate format: {}", id, lastUpdateDate);
            }
        }

        logger.info("Fetched {} items, filtered to {} valid items: {}",
                items.size(), validItems.size(), validItems.stream().map(doc -> doc.getString("_id")).toList());
        exchange.getIn().setBody(validItems);
    }

    public void logFetchedItems(Exchange exchange) {
        List<Document> items = exchange.getIn().getBody(List.class);
        if (items != null && !items.isEmpty()) {
            List<String> itemSummaries = items.stream()
                    .map(doc -> doc.getString("_id") + "@" + doc.getString("lastUpdateDate"))
                    .toList();
            logger.info("Processing {} items: {}", items.size(), itemSummaries);
        } else {
            logger.info("No items to process after filtering");
        }
    }

    public void enrichWithCategory(Exchange exchange) {
        Document item = exchange.getIn().getBody(Document.class);
        if (item != null) {
            String categoryId = item.getString("categoryId");
            if (categoryId != null) {
                exchange.setProperty("item", item);
                exchange.getIn().setBody(Map.of("_id", categoryId));
                logger.debug("Enriching item {} with categoryId {}", item.getString("_id"), categoryId);
            } else {
                logger.warn("Item {} has no categoryId", item.getString("_id"));
                exchange.setProperty("item", item);
                exchange.setProperty("category", new Document("name", "Unknown"));
            }
        } else {
            logger.warn("Item is null in enrichWithCategory");
            exchange.setProperty("category", new Document("name", "Unknown"));
        }
    }

    public void prepareTrendAnalyzerXml(Exchange exchange) {
        Document item = exchange.getProperty("item", Document.class);
        Document category = exchange.getProperty("category", Document.class);
        logger.debug("prepareTrendAnalyzerXml: item={}, category={}", item, category);

        if (item == null) {
            logger.warn("Item is null in prepareTrendAnalyzerXml, skipping");
            exchange.getIn().setBody(null);
            return;
        }

        String lastUpdateDate = item.getString("lastUpdateDate");
        try {
            parseDate(lastUpdateDate);
        } catch (DateTimeParseException e) {
            logger.warn("Skipping item {} due to invalid lastUpdateDate: {}", item.getString("_id"), lastUpdateDate);
            exchange.getIn().setBody(null);
            return;
        }

        Inventory inventory = new Inventory();
        Category cat = new Category();
        String categoryId = item.getString("categoryId") != null ? item.getString("categoryId") : "Unknown";
        cat.setId(categoryId);
        CategoryName catName = new CategoryName();
        catName.setName(category != null && category.getString("name") != null ? category.getString("name") : "Unknown");
        cat.setCategoryName(catName);

        ItemXml itemXml = new ItemXml();
        itemXml.setItemId(item.getString("_id") != null ? item.getString("_id") : "Unknown");
        itemXml.setCategoryId(categoryId);

        Integer availableStock = 0;
        Object stockDetails = item.get("stockDetails");
        if (stockDetails instanceof Document) {
            Object availableStockObj = ((Document) stockDetails).get("availableStock");
            if (availableStockObj instanceof Integer) {
                availableStock = (Integer) availableStockObj;
            } else if (availableStockObj instanceof String) {
                try {
                    availableStock = Integer.parseInt((String) availableStockObj);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid availableStock format for item {}: {}", item.getString("_id"), availableStockObj);
                }
            }
        }
        itemXml.setAvailableStock(availableStock);

        int sellingPrice = 0;
        Object itemPrice = item.get("itemPrice");
        if (itemPrice instanceof Document) {
            Object sellingPriceObj = ((Document) itemPrice).get("sellingPrice");
            if (sellingPriceObj instanceof Number) {
                sellingPrice = ((Number) sellingPriceObj).intValue();
            } else if (sellingPriceObj instanceof String) {
                try {
                    sellingPrice = Integer.parseInt((String) sellingPriceObj);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid sellingPrice format for item {}: {}", item.getString("_id"), sellingPriceObj);
                }
            }
        }
        itemXml.setSellingPrice(sellingPrice);

        cat.setItems(List.of(itemXml));
        inventory.setCategories(List.of(cat));

        exchange.getIn().setHeader("CamelFileName", String.format("trend_%s.xml", item.getString("_id")));
        exchange.getIn().setBody(inventory);
        logger.debug("Prepared Trend Analyzer XML for item: {}", item.getString("_id"));
    }

    public void prepareStoreFrontJson(Exchange exchange) {
        Document item = exchange.getProperty("item", Document.class);
        Document category = exchange.getProperty("category", Document.class);
        logger.debug("prepareStoreFrontJson: item={}, category={}", item, category);

        if (item == null) {
            logger.warn("Item is null in prepareStoreFrontJson, skipping");
            exchange.getIn().setBody(null);
            return;
        }

        String lastUpdateDate = item.getString("lastUpdateDate");
        try {
            parseDate(lastUpdateDate);
        } catch (DateTimeParseException e) {
            logger.warn("Skipping item {} due to invalid lastUpdateDate: {}", item.getString("_id"), lastUpdateDate);
            exchange.getIn().setBody(null);
            return;
        }

        Map<String, Object> storeFront = new LinkedHashMap<>();
        storeFront.put("_id", item.getString("_id") != null ? item.getString("_id") : "Unknown");
        storeFront.put("itemName", item.getString("itemName") != null ? item.getString("itemName") : "Unknown");
        storeFront.put("categoryName", category != null && category.getString("name") != null ? category.getString("name") : "Unknown");

        Map<String, Object> itemPrice = new LinkedHashMap<>();
        Object itemPriceObj = item.get("itemPrice");
        if (itemPriceObj instanceof Document) {
            Document priceDoc = (Document) itemPriceObj;
            itemPrice.put("basePrice", priceDoc.get("basePrice", 0));
            itemPrice.put("sellingPrice", priceDoc.get("sellingPrice", 0));
        } else {
            itemPrice.put("basePrice", 0);
            itemPrice.put("sellingPrice", 0);
        }
        storeFront.put("itemPrice", itemPrice);

        Map<String, Object> stockDetails = new LinkedHashMap<>();
        Object stockDetailsObj = item.get("stockDetails");
        if (stockDetailsObj instanceof Document) {
            Document stockDoc = (Document) stockDetailsObj;
            Object availableStock = stockDoc.get("availableStock");
            if (availableStock instanceof String) {
                try {
                    stockDetails.put("availableStock", Integer.parseInt((String) availableStock));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid availableStock format for item {}: {}", item.getString("_id"), availableStock);
                    stockDetails.put("availableStock", 0);
                }
            } else {
                stockDetails.put("availableStock", stockDoc.getInteger("availableStock", 0));
            }
            stockDetails.put("unitOfMeasure", stockDoc.getString("unitOfMeasure") != null ? stockDoc.getString("unitOfMeasure") : "Unknown");
        } else {
            stockDetails.put("availableStock", 0);
            stockDetails.put("unitOfMeasure", "Unknown");
        }
        storeFront.put("stockDetails", stockDetails);

        storeFront.put("specialProduct", item.get("specialProduct") != null ? item.get("specialProduct") : false);

        exchange.getIn().setHeader("CamelFileName", String.format("storefront_%s.json", item.getString("_id")));
        exchange.getIn().setBody(storeFront);
        logger.debug("Prepared StoreFront JSON for item: {}", item.getString("_id"));
    }

    public void prepareReviewAggregatorXml(Exchange exchange) {
        Document item = exchange.getProperty("item", Document.class);
        if (item == null) {
            logger.warn("Item is null in prepareReviewAggregatorXml, skipping");
            exchange.getIn().setBody(null);
            return;
        }

        String lastUpdateDate = item.getString("lastUpdateDate");
        try {
            parseDate(lastUpdateDate);
        } catch (DateTimeParseException e) {
            logger.warn("Skipping item {} due to invalid lastUpdateDate: {}", item.getString("_id"), lastUpdateDate);
            exchange.getIn().setBody(null);
            return;
        }

        List<Document> reviewDocs = item.getList("review", Document.class, Collections.emptyList());
        Reviews reviews = new Reviews();
        ReviewItem reviewItem = new ReviewItem();
        reviewItem.setId(item.getString("_id"));

        List<Review> reviewList = new ArrayList<>();
        for (Document rev : reviewDocs) {
            Review review = new Review();
            Object ratingObj = rev.get("rating");
            if (ratingObj instanceof String) {
                try {
                    review.setReviewRating(Integer.parseInt((String) ratingObj));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid rating format for item {}: {}", item.getString("_id"), ratingObj);
                    review.setReviewRating(0);
                }
            } else {
                review.setReviewRating(rev.getInteger("rating", 0));
            }
            review.setReviewComment(rev.getString("comment"));
            reviewList.add(review);
        }
        reviewItem.setReviews(reviewList);
        reviews.setItems(List.of(reviewItem));

        exchange.getIn().setHeader("CamelFileName", String.format("review_%s.xml", item.getString("_id")));
        exchange.getIn().setBody(reviews);
        logger.debug("Prepared Review Aggregator XML for item: {}", item.getString("_id"));
    }
}
