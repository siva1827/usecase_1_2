package com.UST.fileExport.model;

import jakarta.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory {

    public ObjectFactory() {
    }

    public Inventory createInventory() {
        return new Inventory();
    }

    public Category createCategory() {
        return new Category();
    }

    public CategoryName createCategoryName() {
        return new CategoryName();
    }

    public ItemXml createItemXml() {
        return new ItemXml();
    }

    public Reviews createReviews() {
        return new Reviews();
    }

    public ReviewItem createReviewItem() {
        return new ReviewItem();
    }

    public Review createReview() {
        return new Review();
    }
}