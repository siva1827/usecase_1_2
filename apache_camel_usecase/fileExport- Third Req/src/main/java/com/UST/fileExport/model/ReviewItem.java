package com.UST.fileExport.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class ReviewItem {
    @XmlAttribute(name = "id")
    private String id;

    @XmlElement(name = "Review")
    private List<Review> reviews;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }
}
