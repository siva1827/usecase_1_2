package com.UST.Apache_Camel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class InventoryItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("_id")
    private String id;
    private StockUpdateDetails stockDetails;

    public InventoryItem(String id, StockUpdateDetails stockDetails) {
        this.id = id;
        this.stockDetails = stockDetails;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public StockUpdateDetails getStockDetails() {
        return stockDetails;
    }

    public void setStockDetails(StockUpdateDetails stockDetails) {
        this.stockDetails = stockDetails;
    }

    public InventoryItem() {
    }

}