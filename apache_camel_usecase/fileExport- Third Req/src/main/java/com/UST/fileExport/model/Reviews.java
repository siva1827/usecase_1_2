package com.UST.fileExport.model;

import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "Reviews")
@XmlAccessorType(XmlAccessType.FIELD)
public class Reviews {
    @XmlElement(name = "item")
    private List<ReviewItem> items;

    public List<ReviewItem> getItems() { return items; }
    public void setItems(List<ReviewItem> items) { this.items = items; }
}

