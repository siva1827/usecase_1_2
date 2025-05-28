package com.UST.Apache_Camel.model;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class InventoryUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<InventoryItem> items;
}