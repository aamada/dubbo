/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.config.spring.context.annotation;

import com.alibaba.dubbo.config.AbstractConfig;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.MonitorConfig;
import com.alibaba.dubbo.config.spring.beans.factory.annotation.DubboConfigBindingBeanPostProcessor;
import com.alibaba.dubbo.config.spring.context.config.NamePropertyDefaultValueDubboConfigBeanCustomizer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.alibaba.dubbo.config.spring.util.BeanRegistrar.registerInfrastructureBean;
import static com.alibaba.dubbo.config.spring.util.PropertySourcesUtils.getSubProperties;
import static com.alibaba.dubbo.config.spring.util.PropertySourcesUtils.normalizePrefix;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;
import static org.springframework.beans.factory.support.BeanDefinitionReaderUtils.registerWithGeneratedName;

/**
 * {@link AbstractConfig Dubbo Config} binding Bean registrar
 *
 * @see EnableDubboConfigBinding
 * @see DubboConfigBindingBeanPostProcessor
 * @since 2.5.8
 */
public class DubboConfigBindingRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private final Log log = LogFactory.getLog(getClass());

    private ConfigurableEnvironment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        /**
         * 从EnableDubboConfigBindings注解中去获取到一些EnableDubboConfigBinding的信息
         *
         * 这里我们只是拿到一个Single的类, 那么这个multiple=false
         */
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableDubboConfigBinding.class.getName()));

        // 注册每一个binding的注解信息
        // @EnableDubboConfigBinding(prefix = "dubbo.monitors", type = MonitorConfig.class, multiple = true),
        registerBeanDefinitions(attributes, registry);

    }

    protected void registerBeanDefinitions(AnnotationAttributes attributes, BeanDefinitionRegistry registry) {

        // @EnableDubboConfigBinding(prefix = "dubbo.applications", type = ApplicationConfig.class, multiple = true),
        // prefix
        String prefix = environment.resolvePlaceholders(attributes.getString("prefix"));

        // type
        Class<? extends AbstractConfig> configClass = attributes.getClass("type");

        // multiple
        boolean multiple = attributes.getBoolean("multiple");

        // prefix
        // type=ApplicationConfig.class
        // true or false
        // 注册器
        registerDubboConfigBeans(prefix, configClass, multiple, registry);

    }

    // 注册配置的类, 例如ApplicationConfig
    private void registerDubboConfigBeans(String prefix,
                                          Class<? extends AbstractConfig> configClass,
                                          boolean multiple,
                                          BeanDefinitionRegistry registry) {

        // 获得dubbo.application.的前缀的配置
        Map<String, Object> properties = getSubProperties(environment.getPropertySources(), prefix);

        if (CollectionUtils.isEmpty(properties)) {
            // 如果配置的属性, 那个没有, 那么直接返回
            if (log.isDebugEnabled()) {
                log.debug("There is no property for binding to dubbo config class [" + configClass.getName()
                        + "] within prefix [" + prefix + "]");
            }
            return;
        }

        // 获得配置属性对应的bean名字的集合
        // multiple=true, 就是多个配置
        // multiple=false, 那么
        Set<String> beanNames = multiple ? resolveMultipleBeanNames(properties) :
                Collections.singleton(resolveSingleBeanName(properties, configClass, registry));
        // 这里是真的不懂, 为什么?
        for (String beanName : beanNames) {
            // 逐个去注册这些bean
            // 然后去注册这个配置类
            registerDubboConfigBean(beanName, configClass, registry);
            // 这里也是挺有意思的哈, 居然搞一个后处理器, 每一个配置类, 都会配置一个后处理器吗?
            registerDubboConfigBindingBeanPostProcessor(prefix, beanName, multiple, registry);

        }
        // 注册dubbo客户配置的配置类
        registerDubboConfigBeanCustomizers(registry);
    }

    // 注册一个dubbo的配置bean
    private void registerDubboConfigBean(String beanName, Class<? extends AbstractConfig> configClass,
                                         BeanDefinitionRegistry registry) {
        // 生成一个BeanDefinitionBuilder
        BeanDefinitionBuilder builder = rootBeanDefinition(configClass);
        // 从里面获取一个BeanDefinition
        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        // 注册进去
        registry.registerBeanDefinition(beanName, beanDefinition);
        // 打印日志
        if (log.isInfoEnabled()) {
            log.info("The dubbo config bean definition [name : " + beanName + ", class : " + configClass.getName() +
                    "] has been registered.");
        }
    }

    // 搞不懂的是, 有必要, 为每一个bean来设置他的后处理器
    // 注册一个后处理器
    private void registerDubboConfigBindingBeanPostProcessor(String prefix, String beanName, boolean multiple,
                                                             BeanDefinitionRegistry registry) {
        // 创建BeanDefinitionBuilder对象
        Class<?> processorClass = DubboConfigBindingBeanPostProcessor.class;
        BeanDefinitionBuilder builder = rootBeanDefinition(processorClass);
        // 添加构造方法的参数为actualPrefix和beanName.即, 创建DubboConfigBindingBeanPostProcessor对象, 需要这两个构造参数
        String actualPrefix = multiple ? normalizePrefix(prefix) + beanName : prefix;
        builder.addConstructorArgValue(actualPrefix).addConstructorArgValue(beanName);

        // 获得AbstractBeanDefinition对象
        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        // 设置role属性
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        // 注册到registry中
        registerWithGeneratedName(beanDefinition, registry);
        if (log.isInfoEnabled()) {
            log.info("The BeanPostProcessor bean definition [" + processorClass.getName()
                    + "] for dubbo config bean [name : " + beanName + "] has been registered.");
        }
    }

    private void registerDubboConfigBeanCustomizers(BeanDefinitionRegistry registry) {
        registerInfrastructureBean(registry, "namePropertyDefaultValueDubboConfigBeanCustomizer",
                NamePropertyDefaultValueDubboConfigBeanCustomizer.class);
    }

    @Override
    public void setEnvironment(Environment environment) {

        Assert.isInstanceOf(ConfigurableEnvironment.class, environment);

        this.environment = (ConfigurableEnvironment) environment;

    }

    // -----------------------------对下面这两个方法来解释一下------------------------------------
    // dubbo.application.x.name=biu_x_name
    // dubbo.application.y.name=biu_y_name
    // 此时指定@Service Bean使用哪个应用

    // 当multiple=true时, 才会走到这里这个方法
    // 例如: dubbo.application.${beanName}.name=dubbo-demo-annotation-provider
    private Set<String> resolveMultipleBeanNames(Map<String, Object> properties) {
        Set<String> beanNames = new LinkedHashSet<String>();
        for (String propertyName : properties.keySet()) {
            // 获取上述示例的${beanName}字符串
            int index = propertyName.indexOf(".");
            if (index > 0) {
                String beanName = propertyName.substring(0, index);
                beanNames.add(beanName);
            }
        }
        return beanNames;
    }

    // 解析单例的名称
    // 这个方法有些看不懂
    private String resolveSingleBeanName(Map<String, Object> properties, Class<? extends AbstractConfig> configClass,
                                         BeanDefinitionRegistry registry) {
        // 从配置属性中获取属性id
        String beanName = (String) properties.get("id");
        // 如果定义, 基于spring提供的机制, 生成对应的bean的名字, 例如说:org.apache.dubbo.config.ApplicationConfig#0
        if (!StringUtils.hasText(beanName)) {
            // 没有bean的id属性
            BeanDefinitionBuilder builder = rootBeanDefinition(configClass);
            // 生成一个beanName
            beanName = BeanDefinitionReaderUtils.generateBeanName(builder.getRawBeanDefinition(), registry);
        }
        return beanName;

    }


    // ------------------------------对上面这两个方法来解释一下-----------------------------------

}
