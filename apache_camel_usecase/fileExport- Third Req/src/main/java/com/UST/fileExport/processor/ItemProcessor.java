package com.UST.fileExport.processor;

import com.UST.fileExport.model.ReviewXml;
import com.UST.fileExport.model.StoreJson;
import com.UST.fileExport.model.TrendXml;
import org.apache.camel.Exchange;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ItemProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ItemProcessor.class);
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public void setCurrentTimestamp(Exchange exchange) {
        String currentTs;
        synchronized (FORMATTER) {
            currentTs = FORMATTER.format(new Date());
        }
        exchange.setProperty("currentTs", currentTs);
        logger.debug("Set currentTs: {}", currentTs);
    }

    public void prepareItemQuery(Exchange exchange) {
        Map<String, Date> controlRefMap = exchange.getProperty("controlRefMap", Map.class);
        Document query = new Document();

        if (controlRefMap != null && !controlRefMap.isEmpty()) {
            // Find the most recent lastProcessTs to filter items
            Date latestProcessTs = Collections.max(controlRefMap.values());
            String latestProcessTsStr;
            synchronized (FORMATTER) {
                latestProcessTsStr = FORMATTER.format(latestProcessTs);
            }
            query.append("lastUpdateDate", new Document("$gt", latestProcessTsStr));
            logger.debug("Prepared item query with lastUpdateDate > {}", latestProcessTsStr);
        } else {
            logger.warn("controlRefMap is null or empty, fetching all items");
        }

        exchange.getIn().setBody(query);
        logger.debug("Prepared item query: {}", query);
    }

    public void filterValidItems(Exchange exchange) {
        List<Document> items = exchange.getIn().getBody(List.class);
        Map<String, Date> controlRefMap = exchange.getProperty("controlRefMap", Map.class);
        List<Document> validItems = new ArrayList<>();

        if (items == null || items.isEmpty()) {
            logger.info("No items fetched from MongoDB");
            exchange.getIn().setBody(validItems);
            return;
        }

        if (controlRefMap == null || controlRefMap.isEmpty()) {
            logger.warn("controlRefMap is null or empty, processing all items as new");
            validItems.addAll(items);
            exchange.getIn().setBody(validItems);
            logger.info("Fetched {} items, all considered valid (no ControlRef map)", items.size());
            return;
        }

        for (Document item : items) {
            String id = item.getString("_id");
            String lastUpdateDateStr = item.getString("lastUpdateDate");

            if (lastUpdateDateStr == null) {
                logger.warn("Skipping item {}: lastUpdateDate is null", id);
                continue;
            }

            Date lastUpdateDate;
            try {
                synchronized (FORMATTER) {
                    lastUpdateDate = FORMATTER.parse(lastUpdateDateStr);
                }
            } catch (ParseException e) {
                logger.error("Invalid lastUpdateDate format for item {}: {}", id, lastUpdateDateStr, e);
                continue;
            }

            Date lastProcessTs = controlRefMap.get(id);
            if (lastProcessTs == null || lastUpdateDate.after(lastProcessTs)) {
                validItems.add(item);
                logger.info("Valid item: {} with lastUpdateDate: {} (lastProcessTs: {})",
                        id, lastUpdateDateStr, lastProcessTs != null ? FORMATTER.format(lastProcessTs) : "none");
            } else {
                logger.debug("Skipping item {}: lastUpdateDate {} not after lastProcessTs {}",
                        id, lastUpdateDateStr, FORMATTER.format(lastProcessTs));
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
            exchange.setProperty("itemId", item.getString("_id"));
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

    public void mapItemData(Exchange exchange) {
        Document itemDoc = exchange.getProperty("item", Document.class);
        Document categoryDoc = exchange.getProperty("category", Document.class);
        String categoryName = categoryDoc != null ? categoryDoc.getString("name") : "Unknown";

        // TrendXml
        TrendXml trendXml = new TrendXml();
        trendXml.setItemId(itemDoc.getString("_id"));
        trendXml.setCategoryId(itemDoc.getString("categoryId"));
        trendXml.setCategoryName(categoryName);
        Document stock = itemDoc.get("stockDetails", Document.class);
        trendXml.setAvailableStock(stock != null ? stock.getInteger("availableStock", 0) : 0);
        Document price = itemDoc.get("itemPrice", Document.class);
        trendXml.setSellingPrice((price != null && price.get("sellingPrice") != null)
                ? price.getInteger("sellingPrice", 0) : 0);

        // ReviewXml
        ReviewXml reviewXml = new ReviewXml();
        reviewXml.setItemId(itemDoc.getString("_id"));
        List<ReviewXml.Review> reviews = new ArrayList<>();
        List<Document> reviewDocs = itemDoc.getList("review", Document.class, Collections.emptyList());
        for (Document r : reviewDocs) {
            ReviewXml.Review review = new ReviewXml.Review();
            review.setReviewrating(r.getInteger("rating"));
            review.setReviewcomment(r.getString("comment"));
            reviews.add(review);
        }
        reviewXml.setReviews(reviews);

        // StoreJson
        StoreJson storeJson = new StoreJson();
        storeJson.set_id(itemDoc.getString("_id"));
        storeJson.setItemName(itemDoc.getString("itemName"));
        storeJson.setCategoryName(categoryName);
        storeJson.setItemPrice(itemDoc.get("itemPrice"));
        storeJson.setStockDetails(itemDoc.get("stockDetails"));
        storeJson.setSpecialProduct(itemDoc.getBoolean("specialProduct", false));

        exchange.setProperty("trendXml", trendXml);
        exchange.setProperty("reviewXml", reviewXml);
        exchange.setProperty("storeJson", storeJson);
        logger.debug("Mapped item {} to TrendXml, ReviewXml, StoreJson", itemDoc.getString("_id"));
    }

    public void prepareTrendXml(Exchange exchange) {
        TrendXml trendXml = exchange.getProperty("trendXml", TrendXml.class);
        if (trendXml == null || trendXml.getItemId() == null) {
            logger.warn("trendXml is null or invalid, skipping");
            exchange.getIn().setBody(null);
            return;
        }
        exchange.getIn().setHeader("CamelFileName", String.format("trend_%s.xml", trendXml.getItemId()));
        exchange.getIn().setBody(trendXml);
        logger.debug("Prepared trend XML for item: {}", trendXml.getItemId());
    }

    public void prepareStoreJson(Exchange exchange) {
        StoreJson storeJson = exchange.getProperty("storeJson", StoreJson.class);
        if (storeJson == null || storeJson.get_id() == null) {
            logger.warn("storeJson is null or invalid, skipping");
            exchange.getIn().setBody(null);
            return;
        }
        exchange.getIn().setHeader("CamelFileName", String.format("storefront_%s.json", storeJson.get_id()));
        exchange.getIn().setBody(storeJson);
        logger.debug("Prepared store JSON for item: {}", storeJson.get_id());
    }

    public void prepareReviewXml(Exchange exchange) {
        ReviewXml reviewXml = exchange.getProperty("reviewXml", ReviewXml.class);
        if (reviewXml == null || reviewXml.getItemId() == null) {
            logger.warn("reviewXml is null or invalid, skipping");
            exchange.getIn().setBody(null);
            return;
        }
        exchange.getIn().setHeader("CamelFileName", String.format("review_%s.xml", reviewXml.getItemId()));
        exchange.getIn().setBody(reviewXml);
        logger.debug("Prepared review XML for item: {}", reviewXml.getItemId());
    }
}