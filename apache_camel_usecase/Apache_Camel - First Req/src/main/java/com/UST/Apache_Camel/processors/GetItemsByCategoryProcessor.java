package com.UST.Apache_Camel.processors;

import com.UST.Apache_Camel.config.ApplicationConstants;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GetItemsByCategoryProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(GetItemsByCategoryProcessor.class);

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
                .append("categoryTax", new Document("$first", "$categoryDetails.categoryTax"))
                .append("items", new Document("$push", new Document()
                        .append("id", "$_id")
                        .append("itemName", "$itemName")
                        .append("categoryId", "$categoryId")
                        .append("lastUpdateDate", "$lastUpdateDate")
                        .append("itemPrice", "$itemPrice")
                        .append("stockDetails", "$stockDetails")
                        .append("specialProduct", "$specialProduct")
                        .append("review", "$review")
                ))
        ));

        exchange.getIn().setBody(pipeline);
        logger.debug("Built aggregation pipeline for categoryId: {}, includeSpecial: {}", categoryId, includeSpecial);
    }

    public void processResult(Exchange exchange) {
        List<?> result = exchange.getIn().getBody(List.class);
        if (result == null || result.isEmpty()) {
            exchange.getIn().setBody(new HashMap<String, Object>() {{
                put("message", "No items found for the given category.");
                put("items", new ArrayList<>());
            }});
            logger.info("No items found for categoryId: {}", exchange.getIn().getHeader("categoryId"));
        } else {
            exchange.getIn().setBody(result);
            logger.info("Found {} items for categoryId: {}", result.size(), exchange.getIn().getHeader("categoryId"));
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
    }
}