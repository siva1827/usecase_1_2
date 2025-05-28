package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import com.UST.Apache_Camel.model.*;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GetItemsByCategoryProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(GetItemsByCategoryProcessor.class);

    @Override
    public void process(Exchange exchange) {
        // Default process method
    }

    public void buildAggregationPipeline(Exchange exchange) {
        String categoryId = exchange.getIn().getHeader("categoryId", String.class);
        boolean includeSpecial = Boolean.parseBoolean(exchange.getIn().getHeader("includeSpecial", "false", String.class));

        List<Document> pipeline = new ArrayList<>();
        Document matchStage = new Document("$match", new Document("categoryId", categoryId));
        if (!includeSpecial) {
            matchStage.get("$match", Document.class).append("specialProduct", false);
        }
        pipeline.add(matchStage);

        pipeline.add(new Document("$lookup", new Document()
                .append("from", ApplicationConstants.MONGO_CATEGORY_READ_COLLECTION)
                .append("localField", "categoryId")
                .append("foreignField", "_id")
                .append("as", "categoryDetails")
        ));

        pipeline.add(new Document("$unwind", new Document()
                .append("path", "$categoryDetails")
                .append("preserveNullAndEmptyArrays", true)
        ));

        pipeline.add(new Document("$group", new Document()
                .append("_id", "$categoryId")
                .append("categoryName", new Document("$first", "$categoryDetails.categoryName"))
                .append("categoryDepartment", new Document("$first", "$categoryDetails.categoryDep"))
                .append("items", new Document("$push", new Document()
                        .append("id", "$_id")
                        .append("itemName", "$itemName")
                        .append("categoryId", "$categoryId")
                        .append("lastUpdateDate", "$lastUpdateDate")
                        .append("itemPrice", "$itemPrice")
                        .append("stockDetails", new Document()
                                .append("availableStock", "$stockDetails.availableStock")
                                .append("unitOfMeasure", "$stockDetails.unitOfMeasure"))
                        .append("specialProduct", "$specialProduct")
                ))
        ));

        exchange.getIn().setBody(pipeline);
        logger.debug("Built aggregation pipeline for categoryId: {}, includeSpecial: {}, matchStage: {}", 
                categoryId, includeSpecial, matchStage);
    }

    public void processResult(Exchange exchange) {
        List<?> result = exchange.getIn().getBody(List.class);
        CategoryItemsResponse response = new CategoryItemsResponse();

        if (result == null || result.isEmpty()) {
            exchange.getIn().setBody(response);
            logger.info("No items or category found for categoryId: {}", exchange.getIn().getHeader("categoryId"));
            return;
        }

        logger.debug("Raw pipeline result for categoryId {}: {}", 
                exchange.getIn().getHeader("categoryId"), result);

        Document resultDoc = (Document) result.get(0);
        CategoryItemsResponse resultResponse = new CategoryItemsResponse();
        resultResponse.setCategoryName(resultDoc.getString("categoryName"));
        resultResponse.setCategoryDepartment(resultDoc.getString("categoryDepartment"));

        List<Document> itemDocs = resultDoc.getList("items", Document.class);
        logger.info("Retrieved {} item documents for categoryId: {}, itemIds: {}", 
                itemDocs.size(), 
                exchange.getIn().getHeader("categoryId"), 
                itemDocs.stream().map(doc -> doc.getString("id")).collect(Collectors.toList()));
        exchange.setProperty("resultResponse", resultResponse);
        exchange.getIn().setBody(itemDocs);
    }

    public void transformItem(Exchange exchange) {
        Document itemDoc = exchange.getIn().getBody(Document.class);
        if (itemDoc == null) {
            logger.error("Item document is null for categoryId: {}", exchange.getIn().getHeader("categoryId"));
            exchange.setProperty("itemResult", null);
            exchange.getIn().setBody(null);
            return;
        }
        logger.debug("Processing item document: {}", itemDoc);

        ItemResponseCat item = new ItemResponseCat();
        item.setId(itemDoc.getString("id"));
        item.setItemName(itemDoc.getString("itemName"));
        item.setCategoryId(itemDoc.getString("categoryId"));
//        item.setLastUpdateDate(itemDoc.getString("lastUpdateDate"));

        // Handle specialProduct with type checking
        Object specialProduct = itemDoc.get("specialProduct");
        item.setSpecialProduct(specialProduct instanceof Boolean ? (Boolean) specialProduct : 
                "true".equalsIgnoreCase(String.valueOf(specialProduct)));
        logger.debug("Processed specialProduct for item {}: {}", item.getId(), item.isSpecialProduct());

        // Handle nested itemPrice
        Document priceDoc = itemDoc.get("itemPrice", Document.class);
        if (priceDoc != null) {
            ItemPrice itemPrice = new ItemPrice();
            Number basePrice = priceDoc.get("basePrice", Number.class);
            Number sellingPrice = priceDoc.get("sellingPrice", Number.class);
            itemPrice.setBasePrice(basePrice != null ? BigDecimal.valueOf(basePrice.doubleValue()) : null);
            itemPrice.setSellingPrice(sellingPrice != null ? BigDecimal.valueOf(sellingPrice.doubleValue()) : null);
            item.setItemPrice(itemPrice);
        }

        // Handle nested stockDetails
        Document stockDoc = itemDoc.get("stockDetails", Document.class);
        if (stockDoc != null) {
            StockDetails stockDetails = new StockDetails();
            stockDetails.setAvailableStock(stockDoc.getInteger("availableStock", 0));
            stockDetails.setUnitOfMeasure(stockDoc.getString("unitOfMeasure"));
            item.setStockDetails(stockDetails);
        }

        exchange.setProperty("itemResult", item);
        exchange.getIn().setBody(item);
        logger.debug("Transformed item: {}, stockDetails: {}, specialProduct: {}", 
                item.getId(), item.getStockDetails(), item.isSpecialProduct());
    }

    public void buildFinalResponse(Exchange exchange) {
        List<ItemResponseCat> items = exchange.getProperty("itemResults", List.class);
        CategoryItemsResponse resultResponse = exchange.getProperty("resultResponse", CategoryItemsResponse.class);

        if (items == null) {
            items = new ArrayList<>();
            logger.warn("itemResults was null in buildFinalResponse for categoryId: {}, using empty list", 
                    exchange.getIn().getHeader("categoryId"));
        }

        CategoryItemsResponse response = new CategoryItemsResponse(
                resultResponse != null ? resultResponse.getCategoryName() : null,
                resultResponse != null ? resultResponse.getCategoryDepartment() : null,
                items
        );

        exchange.getIn().setBody(response);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        logger.info("Final response with {} items for categoryId: {}, specialItemsIncluded: {}, itemIds: {}", 
                items.size(), 
                exchange.getIn().getHeader("categoryId"),
                items.stream().anyMatch(ItemResponseCat::isSpecialProduct),
                items.stream().map(ItemResponseCat::getId).collect(Collectors.toList()));
    }
}