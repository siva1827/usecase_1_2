//package com.UST.Apache_Camel.config;
//
//import org.apache.activemq.ActiveMQConnectionFactory;
//import org.apache.camel.component.jms.JmsComponent;
//import org.apache.camel.component.jms.JmsConfiguration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import javax.jms.ConnectionFactory;
//
//@Configuration
//public class ActiveMqConfig {
//
////    @Bean
////    public ConnectionFactory connectionFactory() {
////        ActiveMQConnectionFactory factory =
////                new ActiveMQConnectionFactory("tcp://localhost:61616");
////        factory.setUserName("admin");
////        factory.setPassword("admin");
////        return factory;
////    }
//
////    @Bean
////    public JmsComponent activemq(ConnectionFactory connectionFactory) {
////        JmsConfiguration config = new JmsConfiguration();
////        config.setConnectionFactory(connectionFactory);
////        config.setConcurrentConsumers(5); // Optional concurrency
////        return new JmsComponent(config);
////    }
//}
//
