package com.example.lightscript.server.context;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring应用上下文提供者
 * 用于在非Spring管理的类中获取Spring Bean
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {
    
    private static ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }
    
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }
    
    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }
}