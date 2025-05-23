package com.UST.fileExport.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Review {
    @XmlElement(name = "reviewrating")
    private int reviewRating;

    @XmlElement(name = "reviewcomment")
    private String reviewComment;

    public int getReviewRating() { return reviewRating; }
    public void setReviewRating(int reviewRating) { this.reviewRating = reviewRating; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
}
