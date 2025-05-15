//package com.UST.Apache_Camel.config;
//
//import com.UST.Apache_Camel.exception.InventoryValidationException;
//import com.mongodb.MongoException;
//import org.apache.camel.AggregationStrategy;
//import org.apache.camel.Exchange;
//import org.apache.camel.Processor;
//import org.bson.Document;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.*;
//
//public class InventoryUpdateComponents {
//
//    private static final Logger logger = LoggerFactory.getLogger(InventoryUpdateComponents.class);
//
//    public static class ItemAggregationStrategy implements AggregationStrategy {
//        private static final Logger logger = LoggerFactory.getLogger(ItemAggregationStrategy.class);
//
//        @Override
//        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
//            List itemResults = oldExchange != null
//                    ? oldExchange.getProperty("itemResults", List.class)
//                    : newExchange.getProperty("itemResults", List.class);
//
//            if (itemResults == null) {
//                itemResults = new ArrayList<>();
//                logger.warn("itemResults was null in aggregation, initialized new list");
//            }
//
//            Map<String, Object> itemResult = newExchange.getProperty("itemResult", Map.class);
//            if (itemResult != null) {
//                itemResults.add(itemResult);
//                logger.debug("Added itemResult for item {}: {}", itemResult.get("itemId"), itemResult);
//            } else {
//                logger.warn("itemResult is null for item, exchange: {}", newExchange);
//            }
//
//            Exchange resultExchange = oldExchange != null ? oldExchange : newExchange;
//            resultExchange.setProperty("itemResults", itemResults);
//            return resultExchange;
//        }
//    }
//
//    public static class PayloadValidationProcessor implements Processor {
//        @Override
//        public void process(Exchange exchange) throws Exception {
//            Object body = exchange.getIn().getBody();
//            Map<String, Object> bodyMap;
//
//            try {
//                if (body instanceof Map) {
//                    bodyMap = (Map<String, Object>) body;
//                } else {
//                    exchange.getIn().setBody(body, String.class);
//                    bodyMap = exchange.getIn().getBody(Map.class);
//                }
//            } catch (Exception e) {
//                throw new InventoryValidationException("Invalid JSON payload: " + e.getMessage());
//            }
//
//            if (bodyMap == null || !bodyMap.containsKey("items")) {
//                throw new InventoryValidationException("Missing 'items' in inventory payload.");
//            }
//
//            List<Map<String, Object>> items = (List<Map<String, Object>>) bodyMap.get("items");
//            if (items.isEmpty()) {
//                throw new InventoryValidationException("Inventory items list is empty.");
//            }
//
//            List<Map<String, Object>> itemResults = new ArrayList<>();
//            exchange.setProperty("itemResults", itemResults);
//            exchange.setProperty("inventoryList", items);
//            logger.info("Initialized itemResults, processing {} items", items.size());
//        }
//    }
//
//    public static class ItemProcessor implements Processor {
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//
//        public void processItem(Exchange exchange) throws Exception {
//            Map<String, Object> item = exchange.getIn().getBody(Map.class);
//            if (item == null || item.get("_id") == null || item.get("stockDetails") == null) {
//                throw new InventoryValidationException("Each item must have '_id' and 'stockDetails'.");
//            }
//
//            String id = item.get("_id").toString();
//            Map<String, Object> stock = (Map<String, Object>) item.get("stockDetails");
//
//            if (!stock.containsKey("soldOut") || !stock.containsKey("damaged")) {
//                throw new InventoryValidationException("Missing 'soldOut' or 'damaged' values in stock details for item: " + id);
//            }
//
//            Object soldOutObj = stock.get("soldOut");
//            Object damagedObj = stock.get("damaged");
//
//            if (!(soldOutObj instanceof Integer)) {
//                throw new InventoryValidationException("soldOut must be an integer for item: " + id + ", found: " + soldOutObj.getClass().getSimpleName());
//            }
//            if (!(damagedObj instanceof Integer)) {
//                throw new InventoryValidationException("damaged must be an integer for item: " + id + ", found: " + damagedObj.getClass().getSimpleName());
//            }
//
//            int soldOut = (Integer) soldOutObj;
//            int damaged = (Integer) damagedObj;
//
//            if (soldOut < 0) {
//                throw new InventoryValidationException("soldOut cannot be negative for item: " + id);
//            }
//            if (damaged < 0) {
//                throw new InventoryValidationException("damaged cannot be negative for item: " + id);
//            }
//
//            exchange.setProperty("itemId", id);
//            exchange.setProperty("soldOut", soldOut);
//            exchange.setProperty("damaged", damaged);
//            logger.debug("Processing item: {}", id);
//        }
//
//        public void setItemId(Exchange exchange) {
//            String itemId = exchange.getProperty("itemId", String.class);
//            exchange.getIn().setBody(itemId);
//            logger.debug("Set itemId for findById: {}", itemId);
//        }
//
//        public void validateAndUpdateItem(Exchange exchange) throws Exception {
//            Map<String, Object> item = exchange.getIn().getBody(Map.class);
//            if (item == null) {
//                throw new InventoryValidationException("Item not found for ID: " + exchange.getProperty("itemId"));
//            }
//
//            Map<String, Object> stockDetails = (Map<String, Object>) item.get("stockDetails");
//            if (stockDetails == null) {
//                throw new InventoryValidationException("Missing stockDetails in DB for item: " + exchange.getProperty("itemId"));
//            }
//
//            Object availableStockObj = stockDetails.get("availableStock");
//            Object existingSoldOutObj = stockDetails.get("soldOut");
//            Object existingDamagedObj = stockDetails.get("damaged");
//
//            if (!(availableStockObj instanceof Integer)) {
//                throw new InventoryValidationException("availableStock must be an integer for item: " + exchange.getProperty("itemId") + ", found: " + availableStockObj.getClass().getSimpleName());
//            }
//            if (!(existingSoldOutObj instanceof Integer)) {
//                throw new InventoryValidationException("soldOut must be an integer for item: " + exchange.getProperty("itemId") + ", found: " + existingSoldOutObj.getClass().getSimpleName());
//            }
//            if (!(existingDamagedObj instanceof Integer)) {
//                throw new InventoryValidationException("damaged must be an integer for item: " + exchange.getProperty("itemId") + ", found: " + existingDamagedObj.getClass().getSimpleName());
//            }
//
//            int availableStock = (Integer) availableStockObj;
//            int existingSoldOut = (Integer) existingSoldOutObj;
//            int existingDamaged = (Integer) existingDamagedObj;
//
//            if (availableStock < 0) {
//                throw new InventoryValidationException("availableStock cannot be negative for item: " + exchange.getProperty("itemId"));
//            }
//
//            int soldOut = exchange.getProperty("soldOut", Integer.class);
//            int damaged = exchange.getProperty("damaged", Integer.class);
//
//            if (soldOut + damaged > availableStock) {
//                throw new InventoryValidationException("Requested quantity exceeds available stock for item ID: " + exchange.getProperty("itemId"));
//            }
//
//            stockDetails.put("availableStock", availableStock - soldOut - damaged);
//            stockDetails.put("soldOut", existingSoldOut + soldOut);
//            stockDetails.put("damaged", existingDamaged + damaged);
//
//            item.put("stockDetails", stockDetails);
//            item.put("lastUpdateDate", LocalDate.now().toString());
//
//            exchange.setProperty("updatedItem", item);
//            exchange.getIn().setBody(item);
//        }
//
//        public void markSuccess(Exchange exchange) {
//            String itemId = exchange.getProperty("itemId", String.class);
//            Map<String, Object> itemResult = new HashMap<>();
//            itemResult.put("itemId", itemId);
//            itemResult.put("status", "success");
//            itemResult.put("message", "Inventory updated successfully for item " + itemId);
//            exchange.setProperty("itemResult", itemResult);
//            logger.info("✅ Inventory updated for {}", itemId);
//        }
//
//        public void markFailure(Exchange exchange) {
//            String itemId = exchange.getProperty("itemId", String.class);
//            InventoryValidationException e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, InventoryValidationException.class);
//            String errorMessage = e.getMessage();
//            Map<String, Object> itemResult = new HashMap<>();
//            itemResult.put("itemId", itemId);
//            itemResult.put("status", "error");
//            itemResult.put("message", errorMessage);
//            exchange.setProperty("itemResult", itemResult);
//            logger.warn("Inventory update failed for item {}: {}", itemId, errorMessage);
//        }
//    }
//
//    public static class ErrorResponseProcessor implements Processor {
//        @Override
//        public void process(Exchange exchange) throws Exception {
//            Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
//            String errorMessage = e.getMessage();
//            logger.warn("Request failed: {}", errorMessage);
//            exchange.getMessage().setBody(Map.of(
//                    "status", "error",
//                    "message", errorMessage
//            ));
//            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
//            exchange.removeProperty(Exchange.EXCEPTION_CAUGHT);
//        }
//
//        public void processValidationError(Exchange exchange) {
//            InventoryValidationException e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, InventoryValidationException.class);
//            String errorMessage = e.getMessage();
//            logger.warn("Validation error: {}", errorMessage);
//            exchange.getMessage().setBody(Map.of(
//                    "status", "error",
//                    "message", errorMessage
//            ));
//            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
//            exchange.removeProperty(Exchange.EXCEPTION_CAUGHT);
//        }
//
//        public void processMongoError(Exchange exchange) {
//            MongoException e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, MongoException.class);
//            String errorMessage = "MongoDB error: " + e.getMessage();
//            logger.error("MongoDB error: {}", errorMessage, e);
//            exchange.getMessage().setBody(Map.of(
//                    "status", "error",
//                    "message", errorMessage
//            ));
//            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
//            exchange.removeProperty(Exchange.EXCEPTION_CAUGHT);
//        }
//
//        public void processGenericError(Exchange exchange) {
//            Throwable t = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
//            String errorMessage = "Unexpected error: " + t.getMessage();
//            logger.error("Unexpected error: {}", errorMessage, t);
//            exchange.getMessage().setBody(Map.of(
//                    "status", "error",
//                    "message", errorMessage
//            ));
//            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
//            exchange.removeProperty(Exchange.EXCEPTION_CAUGHT);
//        }
//    }
//
//    public static class FinalResponseProcessor implements Processor {
//        @Override
//        public void process(Exchange exchange) throws Exception {
//            List<Map<String, Object>> itemResults = exchange.getProperty("itemResults", List.class);
//            if (itemResults == null) {
//                itemResults = new ArrayList<>();
//                logger.warn("itemResults is null in final response, initializing as empty list");
//            }
//            String status = itemResults.stream().allMatch(r -> "success".equals(r.get("status"))) ? "completed" : "partial";
//            logger.info("Final response itemResults: {}, status: {}", itemResults, status);
//            exchange.getMessage().setBody(Map.of(
//                    "status", status,
//                    "results", itemResults
//            ));
//            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
//        }
//    }
//
//    public static class GetItemProcessor implements Processor {
//        private static final Logger logger = LoggerFactory.getLogger(GetItemProcessor.class);
//
//        public void setItemId(Exchange exchange) {
//            String itemId = exchange.getIn().getHeader("itemId", String.class);
//            exchange.getIn().setBody(itemId);
//            logger.debug("Set itemId for findById: {}", itemId);
//        }
//
//        public void processResult(Exchange exchange) {
//            if (exchange.getIn().getBody() == null) {
//                logger.info("Item not found for ID: {}", exchange.getIn().getHeader("itemId"));
//                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
//                exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_ITEM_NOT_FOUND));
//            } else {
//                logger.info("Item found: {}", exchange.getIn().getBody());
//                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
//            }
//        }
//
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//    }
//
//    public static class GetItemsByCategoryProcessor implements Processor {
//        private static final Logger logger = LoggerFactory.getLogger(GetItemsByCategoryProcessor.class);
//
//        public void buildAggregationPipeline(Exchange exchange) {
//            String categoryId = exchange.getIn().getHeader("categoryId", String.class);
//            boolean includeSpecial = Boolean.parseBoolean(exchange.getIn().getHeader("includeSpecial", "false", String.class));
//
//            List<Document> pipeline = new ArrayList<>();
//            Document matchStage = new Document("$match", new Document("categoryId", categoryId));
//            if (!includeSpecial) {
//                matchStage.get("$match", Document.class).append("specialProduct", false);
//            }
//            pipeline.add(matchStage);
//
//            pipeline.add(new Document("$lookup", new Document()
//                    .append("from", ApplicationConstants.MONGO_CATEGORY_READ_COLLECTION)
//                    .append("localField", "categoryId")
//                    .append("foreignField", "_id")
//                    .append("as", "categoryDetails")
//            ));
//
//            pipeline.add(new Document("$unwind", new Document()
//                    .append("path", "$categoryDetails")
//                    .append("preserveNullAndEmptyArrays", true)
//            ));
//
//            pipeline.add(new Document("$group", new Document()
//                    .append("_id", "$categoryId")
//                    .append("categoryName", new Document("$first", "$categoryDetails.categoryName"))
//                    .append("categoryDepartment", new Document("$first", "$categoryDetails.categoryDep"))
//                    .append("categoryTax", new Document("$first", "$categoryDetails.categoryTax"))
//                    .append("items", new Document("$push", new Document()
//                            .append("id", "$_id")
//                            .append("itemName", "$itemName")
//                            .append("categoryId", "$categoryId")
//                            .append("lastUpdateDate", "$lastUpdateDate")
//                            .append("itemPrice", "$itemPrice")
//                            .append("stockDetails", "$stockDetails")
//                            .append("specialProduct", "$specialProduct")
//                            .append("review", "$review")
//                    ))
//            ));
//
//            exchange.getIn().setBody(pipeline);
//            logger.debug("Built aggregation pipeline for categoryId: {}, includeSpecial: {}", categoryId, includeSpecial);
//        }
//
//        public void processResult(Exchange exchange) {
//            List<?> result = exchange.getIn().getBody(List.class);
//            if (result == null || result.isEmpty()) {
//                exchange.getIn().setBody(new HashMap<String, Object>() {{
//                    put("message", "No items found for the given category.");
//                    put("items", new ArrayList<>());
//                }});
//                logger.info("No items found for categoryId: {}", exchange.getIn().getHeader("categoryId"));
//            } else {
//                exchange.getIn().setBody(result);
//                logger.info("Found {} items for categoryId: {}", result.size(), exchange.getIn().getHeader("categoryId"));
//            }
//        }
//
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//    }
//
//    public static class PostNewItemProcessor implements Processor {
//        private static final Logger logger = LoggerFactory.getLogger(PostNewItemProcessor.class);
//
//        public void validateItem(Exchange exchange) throws Exception {
//            Map<String, Object> item = exchange.getIn().getBody(Map.class);
//            exchange.setProperty("newItem", item);
//            if (item == null || item.get("_id") == null || item.get("itemPrice") == null || item.get("stockDetails") == null) {
//                throw new InventoryValidationException("Item must have '_id', 'itemPrice', and 'stockDetails'");
//            }
//
//            String itemId = item.get("_id").toString();
//            Map<String, Object> price = (Map<String, Object>) item.get("itemPrice");
//            Map<String, Object> stock = (Map<String, Object>) item.get("stockDetails");
//
//            // Validate itemPrice
//            if (!price.containsKey("basePrice") || !price.containsKey("sellingPrice")) {
//                throw new InventoryValidationException("itemPrice must contain 'basePrice' and 'sellingPrice' for item: " + itemId);
//            }
//
//            Object basePriceObj = price.get("basePrice");
//            Object sellingPriceObj = price.get("sellingPrice");
//
//            if (!(basePriceObj instanceof Number)) {
//                throw new InventoryValidationException("basePrice must be a number for item: " + itemId + ", found: " + basePriceObj.getClass().getSimpleName());
//            }
//            if (!(sellingPriceObj instanceof Number)) {
//                throw new InventoryValidationException("sellingPrice must be a number for item: " + itemId + ", found: " + sellingPriceObj.getClass().getSimpleName());
//            }
//
//            double basePrice = ((Number) basePriceObj).doubleValue();
//            double sellingPrice = ((Number) sellingPriceObj).doubleValue();
//
//            if (basePrice <= 0) {
//                throw new InventoryValidationException("basePrice must be greater than zero for item: " + itemId);
//            }
//            if (sellingPrice <= 0) {
//                throw new InventoryValidationException("sellingPrice must be greater than zero for item: " + itemId);
//            }
//
//            // Validate stockDetails
//            if (!stock.containsKey("availableStock") || !stock.containsKey("soldOut") || !stock.containsKey("damaged")) {
//                throw new InventoryValidationException("stockDetails must contain 'availableStock', 'soldOut', and 'damaged' for item: " + itemId);
//            }
//
//            Object availableStockObj = stock.get("availableStock");
//            Object soldOutObj = stock.get("soldOut");
//            Object damagedObj = stock.get("damaged");
//
//            if (!(availableStockObj instanceof Integer)) {
//                throw new InventoryValidationException("availableStock must be an integer for item: " + itemId + ", found: " + availableStockObj.getClass().getSimpleName());
//            }
//            if (!(soldOutObj instanceof Integer)) {
//                throw new InventoryValidationException("soldOut must be an integer for item: " + itemId + ", found: " + soldOutObj.getClass().getSimpleName());
//            }
//            if (!(damagedObj instanceof Integer)) {
//                throw new InventoryValidationException("damaged must be an integer for item: " + itemId + ", found: " + damagedObj.getClass().getSimpleName());
//            }
//
//            int availableStock = (Integer) availableStockObj;
//            int soldOut = (Integer) soldOutObj;
//            int damaged = (Integer) damagedObj;
//
//            if (availableStock < 0) {
//                throw new InventoryValidationException("availableStock cannot be negative for item: " + itemId);
//            }
//            if (soldOut < 0) {
//                throw new InventoryValidationException("soldOut cannot be negative for item: " + itemId);
//            }
//            if (damaged < 0) {
//                throw new InventoryValidationException("damaged cannot be negative for item: " + itemId);
//            }
//
//            // Validate specialProduct
//            Object specialProductObj = item.get("specialProduct");
//            if (specialProductObj != null && !(specialProductObj instanceof Boolean)) {
//                throw new InventoryValidationException("specialProduct must be a boolean for item: " + itemId + ", found: " + specialProductObj.getClass().getSimpleName());
//            }
//
//            exchange.getIn().setBody(itemId);
//            logger.debug("Validated item and set itemId for findById: {}", itemId);
//        }
//
//        public void handleExistingItem(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
//            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_ITEM_ALREADY_EXISTS));
//            logger.warn("Item already exists: {}", exchange.getProperty("newItem", Map.class).get("_id"));
//        }
//
//        public void setCategoryId(Exchange exchange) {
//            Map<String, Object> item = exchange.getProperty("newItem", Map.class);
//            exchange.setProperty("validatedItem", item);
//            String categoryId = (String) item.get("categoryId");
//            exchange.getIn().setBody(categoryId);
//            logger.debug("Set categoryId for findById: {}", categoryId);
//        }
//
//        public void handleInvalidCategory(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
//            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_CATEGORY_NOT_FOUND));
//            logger.warn("Invalid category for item: {}", exchange.getProperty("validatedItem", Map.class).get("_id"));
//        }
//
//        public void prepareItemForInsert(Exchange exchange) {
//            Map<String, Object> item = exchange.getProperty("validatedItem", Map.class);
//            exchange.getIn().setBody(item);
//            logger.debug("Prepared item for insert: {}", item.get("_id"));
//        }
//
//        public void handleInsertSuccess(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
//            exchange.getIn().setBody(Map.of("message", "Item inserted successfully"));
//            logger.info("Successfully inserted item: {}", exchange.getProperty("validatedItem", Map.class).get("_id"));
//        }
//
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//    }
//
//    public static class PostNewCategoryProcessor implements Processor {
//        private static final Logger logger = LoggerFactory.getLogger(PostNewCategoryProcessor.class);
//
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//
//        public void validateCategory(Exchange exchange) throws Exception {
//            Map<String, Object> category = exchange.getIn().getBody(Map.class);
//            exchange.setProperty("newCategory", category);
//
//            String categoryId = (String) category.get("_id");
//            String categoryName = (String) category.get("categoryName");
//
//            if (categoryId == null || categoryId.isBlank() || categoryName == null || categoryName.isBlank()) {
//                throw new IllegalArgumentException("Category ID and Category Name must not be empty");
//            }
//
//            exchange.getIn().setBody(categoryId);
//            logger.debug("Validated category and set categoryId for findById: {}", categoryId);
//        }
//
//        public void prepareCategoryForInsert(Exchange exchange) {
//            Map<String, Object> category = exchange.getProperty("newCategory", Map.class);
//            exchange.getIn().setBody(category);
//            logger.debug("Prepared category for insert: {}", category.get("_id"));
//        }
//
//        public void handleInsertSuccess(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
//            exchange.getIn().setBody(Map.of("message", "Category inserted successfully"));
//            logger.info("Successfully inserted category: {}", exchange.getProperty("newCategory", Map.class).get("_id"));
//        }
//
//        public void handleExistingCategory(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
//            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_CATEGORY_ALREADY_EXISTS));
//            logger.warn("Category already exists: {}", exchange.getProperty("newCategory", Map.class).get("_id"));
//        }
//    }
//
//    public static class DeleteItemProcessor implements Processor {
//        private static final Logger logger = LoggerFactory.getLogger(DeleteItemProcessor.class);
//
//        public void setItemId(Exchange exchange) {
//            String itemId = exchange.getIn().getHeader("itemId", String.class);
//            exchange.getIn().setBody(itemId);
//            logger.debug("Set itemId for deletion: {}", itemId);
//        }
//
//        public void handleItemNotFound(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
//            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_ITEM_NOT_FOUND));
//            logger.warn("Item not found for deletion: {}", exchange.getIn().getHeader("itemId"));
//        }
//
//        public void handleDeleteSuccess(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
//            exchange.getIn().setBody(Map.of("message", "Item deleted successfully"));
//            logger.info("Successfully deleted item: {}", exchange.getIn().getHeader("itemId"));
//        }
//
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//    }
//
//    public static class DeleteCategoryProcessor implements Processor {
//        private static final Logger logger = LoggerFactory.getLogger(DeleteCategoryProcessor.class);
//
//        public void setCategoryId(Exchange exchange) {
//            String categoryId = exchange.getIn().getHeader("categoryId", String.class);
//            exchange.getIn().setBody(categoryId);
//            logger.debug("Set categoryId for deletion: {}", categoryId);
//        }
//
//        public void handleCategoryNotFound(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
//            exchange.getIn().setBody(Map.of("message", ApplicationConstants.ERROR_CATEGORY_NOT_FOUND));
//            logger.warn("Category not found for deletion: {}", exchange.getIn().getHeader("categoryId"));
//        }
//
//        public void handleDeleteSuccess(Exchange exchange) {
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
//            exchange.getIn().setBody(Map.of("message", "Category deleted successfully"));
//            logger.info("Successfully deleted category: {}", exchange.getIn().getHeader("categoryId"));
//        }
//
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//    }
//
//    public static class AsyncInventoryUpdateProcessor implements Processor {
//        private static final Logger logger = LoggerFactory.getLogger(AsyncInventoryUpdateProcessor.class);
//
//        public void handleException(Exchange exchange) {
//            Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
//            String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
//            logger.error("Error in async update: {}", errorMessage, exception);
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
//            exchange.getIn().setBody(Map.of(
//                    "status", "error",
//                    "message", "Failed to process async update: " + errorMessage
//            ));
//        }
//
//        public void initializeCorrelationId(Exchange exchange) {
//            String correlationId = UUID.randomUUID().toString();
//            exchange.setProperty("correlationId", correlationId);
//            exchange.getIn().setHeader("JMSCorrelationID", correlationId);
//            logger.info("Generated correlationId: {}", correlationId);
//        }
//
//        public void prepareQueueMessage(Exchange exchange) {
//            Map<String, Object> item = exchange.getIn().getBody(Map.class);
//            exchange.getIn().setBody(item);
//            exchange.getIn().setHeader("JMSCorrelationID", exchange.getProperty("correlationId"));
//            logger.debug("Prepared queue message for itemId: {}", item.get("_id"));
//        }
//
//        public void buildEnqueueResponse(Exchange exchange) {
//            String correlationId = exchange.getProperty("correlationId", String.class);
//            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
//            exchange.getIn().setBody(Map.of(
//                    "status", "enqueued",
//                    "correlationId", correlationId
//            ));
//            logger.info("Enqueued items with correlationId: {}", correlationId);
//        }
//
//        @Override
//        public void process(Exchange exchange) throws Exception {
//        }
//    }
//}