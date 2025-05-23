package com.UST.fileExport.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class CategoryName {
    @XmlAttribute(name = "name")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
