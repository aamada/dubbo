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
package com.alibaba.dubbo.config.spring.beans.factory.annotation;

import com.alibaba.dubbo.common.utils.Assert;
import com.alibaba.dubbo.config.AbstractConfig;
import com.alibaba.dubbo.config.spring.context.annotation.DubboConfigBindingRegistrar;
import com.alibaba.dubbo.config.spring.context.annotation.EnableDubboConfigBinding;
import com.alibaba.dubbo.config.spring.context.config.DubboConfigBeanCustomizer;
import com.alibaba.dubbo.config.spring.context.properties.DefaultDubboConfigBinder;
import com.alibaba.dubbo.config.spring.context.properties.DubboConfigBinder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * Dubbo Config Binding {@link BeanPostProcessor}
 *
 * @see EnableDubboConfigBinding
 * @see DubboConfigBindingRegistrar
 * @since 2.5.8
 */

public class DubboConfigBindingBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, InitializingBean {

    private final Log log = LogFactory.getLog(getClass());

    /**
     * The prefix of Configuration Properties
     * 配置属性的前缀
     */
    private final String prefix;

    /**
     * Binding Bean Name
     * bean的名称
     */
    private final String beanName;

    private DubboConfigBinder dubboConfigBinder;

    private ApplicationContext applicationContext;

    private List<DubboConfigBeanCustomizer> configBeanCustomizers = Collections.emptyList();

    /**
     * @param prefix   the prefix of Configuration Properties
     * @param beanName the binding Bean Name
     */
    public DubboConfigBindingBeanPostProcessor(String prefix, String beanName) {
        Assert.notNull(prefix, "The prefix of Configuration Properties must not be null");
        Assert.notNull(beanName, "The name of bean must not be null");
        this.prefix = prefix;
        this.beanName = beanName;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 判断必须是beanName, 并且是AbstractConfig类型
        if (beanName.equals(this.beanName) && bean instanceof AbstractConfig) {
            AbstractConfig dubboConfig = (AbstractConfig) bean;
            // 设置属性到dubboConfig中
            bind(prefix, dubboConfig);

            customize(beanName, dubboConfig);
        }
        return bean;
    }

    private void bind(String prefix, AbstractConfig dubboConfig) {

        dubboConfigBinder.bind(prefix, dubboConfig);

        if (log.isInfoEnabled()) {
            log.info("The properties of bean [name : " + beanName + "] have been binding by prefix of " +
                    "configuration properties : " + prefix);
        }
    }

    private void customize(String beanName, AbstractConfig dubboConfig) {

        for (DubboConfigBeanCustomizer customizer : configBeanCustomizers) {
            customizer.customize(beanName, dubboConfig);
        }

    }

    public DubboConfigBinder getDubboConfigBinder() {
        return dubboConfigBinder;
    }

    public void setDubboConfigBinder(DubboConfigBinder dubboConfigBinder) {
        this.dubboConfigBinder = dubboConfigBinder;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        initDubboConfigBinder();

        initConfigBeanCustomizers();

    }

    // 初始化dubbo配置绑定
    private void initDubboConfigBinder() {
        // 获得(创建)DubboCnofigBinder对象
        if (dubboConfigBinder == null) {
            try {
                dubboConfigBinder = applicationContext.getBean(DubboConfigBinder.class);
            } catch (BeansException ignored) {
                if (log.isDebugEnabled()) {
                    log.debug("DubboConfigBinder Bean can't be found in ApplicationContext.");
                }
                // Use Default implementation
                dubboConfigBinder = createDubboConfigBinder(applicationContext.getEnvironment());
            }
        }

    }

    // 初始化用户配置bean
    private void initConfigBeanCustomizers() {

        Collection<DubboConfigBeanCustomizer> configBeanCustomizers =
                beansOfTypeIncludingAncestors(applicationContext, DubboConfigBeanCustomizer.class).values();

        this.configBeanCustomizers = new ArrayList<DubboConfigBeanCustomizer>(configBeanCustomizers);

        AnnotationAwareOrderComparator.sort(this.configBeanCustomizers);
    }

    /**
     * Create {@link DubboConfigBinder} instance.
     *
     * @param environment 属性环境
     * @return {@link DefaultDubboConfigBinder}
     */
    protected DubboConfigBinder createDubboConfigBinder(Environment environment) {
        // 创建DefaultDubboConfigBinder'对象
        DefaultDubboConfigBinder defaultDubboConfigBinder = new DefaultDubboConfigBinder();
        defaultDubboConfigBinder.setEnvironment(environment);

        // 忽略属性
        defaultDubboConfigBinder.setIgnoreUnknownFields(true);
        defaultDubboConfigBinder.setIgnoreInvalidFields(true);

        return defaultDubboConfigBinder;
    }

}
