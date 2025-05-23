package com.UST.fileExport.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class Item {
    private String id;
    private String itemName;
    private String categoryId;
    private Map<String, Object> itemPrice;
    private Map<String, Object> stockDetails;
    private Boolean specialProduct;
    private List<Map<String, Object>> review;
    private LocalDateTime lastUpdateTs;

    // Getters and setters
    public String get_id() { return id; }
    public void set_id(String id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public Map<String, Object> getItemPrice() { return itemPrice; }
    public void setItemPrice(Map<String, Object> itemPrice) { this.itemPrice = itemPrice; }
    public Map<String, Object> getStockDetails() { return stockDetails; }
    public void setStockDetails(Map<String, Object> stockDetails) { this.stockDetails = stockDetails; }
    public Boolean getSpecialProduct() { return specialProduct; }
    public void setSpecialProduct(Boolean specialProduct) { this.specialProduct = specialProduct; }
    public List<Map<String, Object>> getReview() { return review; }
    public void setReview(List<Map<String, Object>> review) { this.review = review; }
    public LocalDateTime getLastUpdateTs() { return lastUpdateTs; }
    public void setLastUpdateTs(LocalDateTime lastUpdateTs) { this.lastUpdateTs = lastUpdateTs; }
}