package com.cgb.decp.dcepagentserver.conf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @Classname RestTemplateProxy
 * @Description TODO
 * @Date 2021/4/16 23:38
 * @Created by hexm
 */
@Configuration
public class RestTemplateProxy {

    @Bean
    public RestOperations restTemplate() {
        RestTemplate target = new RestTemplate();
        System.out.println("target:" + target.toString());
        RestOperations newProxyInstance = (RestOperations) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        //反射知识点
                        System.out.println("target run before:" + target.toString());
                        Object invoke = method.invoke(target, args);
                        System.out.println("target run after:" + target.toString());
                        return invoke;
                    }
                });
        return newProxyInstance;
    }
}
