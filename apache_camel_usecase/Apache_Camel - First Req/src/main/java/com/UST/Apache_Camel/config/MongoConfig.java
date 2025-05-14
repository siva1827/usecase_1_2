//package com.UST.Apache_Camel.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.mongodb.client.MongoClient;
//import com.mongodb.client.MongoClients;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class MongoConfig {
//
//    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);
//
////    @Bean(name = "mongoClient")
////    public MongoClient mongoClient() {
////        logger.info("Creating MongoClient bean");
////        return MongoClients.create("mongodb://localhost:27017");
////    }
//
//    @Bean
//    public ObjectMapper objectMapper() {
//        logger.info("Creating ObjectMapper bean");
//        return new ObjectMapper();
//    }
//}