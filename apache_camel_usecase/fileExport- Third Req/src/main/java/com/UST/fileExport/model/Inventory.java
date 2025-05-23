package com.UST.fileExport.model;

import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "inventory")
@XmlAccessorType(XmlAccessType.FIELD)
public class Inventory {
    @XmlElement(name = "category")
    private List<Category> categories;

    public List<Category> getCategories() { return categories; }
    public void setCategories(List<Category> categories) { this.categories = categories; }
}

