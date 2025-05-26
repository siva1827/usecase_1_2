package com.UST.fileExport.model;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "TrendXml")
public class TrendXml {
    private String itemId;
    private String categoryId;
    private String categoryName;
    private int availableStock;
    private int sellingPrice;

    @XmlElement
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    @XmlElement
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    @XmlElement
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    @XmlElement
    public int getAvailableStock() { return availableStock; }
    public void setAvailableStock(int availableStock) { this.availableStock = availableStock; }

    @XmlElement
    public int getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(int sellingPrice) { this.sellingPrice = sellingPrice; }
}