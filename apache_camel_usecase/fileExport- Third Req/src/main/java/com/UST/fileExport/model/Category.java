package com.UST.fileExport.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class Category {
    @XmlAttribute(name = "id")
    private String _id;

    @XmlElement(name = "categoryName")
    private CategoryName categoryName;

    @XmlElement(name = "item")
    private List<ItemXml> items;

    public String getId() { return _id; }
    public void setId(String id) { this._id = _id; }
    public CategoryName getCategoryName() { return categoryName; }
    public void setCategoryName(CategoryName categoryName) { this.categoryName = categoryName; }
    public List<ItemXml> getItems() { return items; }
    public void setItems(List<ItemXml> items) { this.items = items; }
}
