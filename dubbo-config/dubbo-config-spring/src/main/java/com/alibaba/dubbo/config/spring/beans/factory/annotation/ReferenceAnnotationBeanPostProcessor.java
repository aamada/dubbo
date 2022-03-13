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

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.spring.ReferenceBean;
import com.alibaba.dubbo.config.spring.ServiceBean;
import com.alibaba.dubbo.config.spring.context.event.ServiceBeanExportedEvent;
import com.alibaba.dubbo.config.spring.util.AnnotationUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that Consumer service {@link Reference} annotated fields
 *
 * @since 2.5.7
 */
public class ReferenceAnnotationBeanPostProcessor extends AnnotationInjectedBeanPostProcessor<Reference>
        implements ApplicationContextAware, ApplicationListener {

    /**
     * The bean name of {@link ReferenceAnnotationBeanPostProcessor}
     */
    public static final String BEAN_NAME = "referenceAnnotationBeanPostProcessor";

    /**
     * Cache size
     */
    private static final int CACHE_SIZE = Integer.getInteger(BEAN_NAME + ".cache.size", 32);

    private final ConcurrentMap<String, ReferenceBean<?>> referenceBeanCache =
            new ConcurrentHashMap<String, ReferenceBean<?>>(CACHE_SIZE);

    private final ConcurrentHashMap<String, ReferenceBeanInvocationHandler> localReferenceBeanInvocationHandlerCache =
            new ConcurrentHashMap<String, ReferenceBeanInvocationHandler>(CACHE_SIZE);

    private final ConcurrentMap<InjectionMetadata.InjectedElement, ReferenceBean<?>> injectedFieldReferenceBeanCache =
            new ConcurrentHashMap<InjectionMetadata.InjectedElement, ReferenceBean<?>>(CACHE_SIZE);

    private final ConcurrentMap<InjectionMetadata.InjectedElement, ReferenceBean<?>> injectedMethodReferenceBeanCache =
            new ConcurrentHashMap<InjectionMetadata.InjectedElement, ReferenceBean<?>>(CACHE_SIZE);

    private ApplicationContext applicationContext;

    /**
     * Gets all beans of {@link ReferenceBean}
     *
     * @return non-null read-only {@link Collection}
     * @since 2.5.9
     */
    public Collection<ReferenceBean<?>> getReferenceBeans() {
        return referenceBeanCache.values();
    }

    /**
     * Get {@link ReferenceBean} {@link Map} in injected field.
     *
     * @return non-null {@link Map}
     * @since 2.5.11
     */
    public Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> getInjectedFieldReferenceBeanMap() {
        return Collections.unmodifiableMap(injectedFieldReferenceBeanCache);
    }

    /**
     * Get {@link ReferenceBean} {@link Map} in injected method.
     *
     * @return non-null {@link Map}
     * @since 2.5.11
     */
    public Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> getInjectedMethodReferenceBeanMap() {
        return Collections.unmodifiableMap(injectedMethodReferenceBeanCache);
    }

    // 注入bean
    // 它是被AnnotationInjectedBeanPostProcessor来调用的
    @Override
    protected Object doGetInjectedBean(Reference reference, Object bean, String beanName, Class<?> injectedType,
                                       InjectionMetadata.InjectedElement injectedElement) throws Exception {

        // 创建引用的bean
        String referencedBeanName = buildReferencedBeanName(reference, injectedType);

        // 创建引用的bean
        ReferenceBean referenceBean = buildReferenceBeanIfAbsent(referencedBeanName, reference, injectedType, getClassLoader());

        // 缓存起来
        cacheInjectedReferenceBean(referenceBean, injectedElement);

        // 创建代理
        Object proxy = buildProxy(referencedBeanName, referenceBean, injectedType);

        // 返回代理
        return proxy;
    }

    // 创建代理
    private Object buildProxy(String referencedBeanName, ReferenceBean referenceBean, Class<?> injectedType) {
        // 建构创建代理所需要的InvocationHandler
        InvocationHandler handler = buildInvocationHandler(referencedBeanName, referenceBean);
        // 创建代理
        Object proxy = Proxy.newProxyInstance(getClassLoader(), new Class[]{injectedType}, handler);
        // 返回代理
        return proxy;
    }

    // 构建这个invocationHandler放入至缓存中去
    // 要在下一步, 再去将它移除
    private InvocationHandler buildInvocationHandler(String referencedBeanName, ReferenceBean referenceBean) {
        // 本地引用的bean的invocationHandler
        // 首先, 从localReferenceBeanInvocationHandlerCache缓存中, 获得ReferenceBeanInvocationHandler对象
        ReferenceBeanInvocationHandler handler = localReferenceBeanInvocationHandlerCache.get(referencedBeanName);
        // 然后, 如果不存在, 则创建ReferenceBeanInvocationhandler对象
        if (handler == null) {
            // 当它为null时, 这个时候新建一个
            handler = new ReferenceBeanInvocationHandler(referenceBean);
        }
        // 如果上下文中包含这个引用的bean
        // 之后, 根据引用的Dubbo服务是远程的还是本地的, 做不同的处理
        // [本地]判断如果applicationContext中已经初始化, 说明是本地的@Service Bean, 则添加到localReferenceBeanInvocationHandlerCache
        // 等到本地的@Service Bean暴露后, 再里德初始化
        if (applicationContext.containsBean(referencedBeanName)) { // Is local @Service Bean or not ?
            // ReferenceBeanInvocationHandler's initialization has to wait for current local @Service Bean has been exported.
            // 那么把这个bean与这个handler给对应起来
            localReferenceBeanInvocationHandlerCache.put(referencedBeanName, handler);
            // [远程]判断若果applicationContext中未初始化, 说明是远程的@Service Bean对象, 则立即进行初始化
        } else {
            // 如果不包含的话, 就初始化这个handler了
            // Remote Reference Bean should initialize immediately
            handler.init();
        }
        return handler;
    }

    // 哦豁, 这里是一个代码的InvocationHandler
    private static class ReferenceBeanInvocationHandler implements InvocationHandler {

        // 引用的bean
        private final ReferenceBean referenceBean;

        // 真的bean
        private Object bean;

        private ReferenceBeanInvocationHandler(ReferenceBean referenceBean) {
            this.referenceBean = referenceBean;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = null;
            try {
                if (bean == null) { // If the bean is not initialized, invoke init()
                    // issue: https://github.com/apache/incubator-dubbo/issues/3429
                    init();
                }
                result = method.invoke(bean, args);
            } catch (InvocationTargetException e) {
                // re-throws the actual Exception.
                throw e.getTargetException();
            }
            return result;
        }

        private void init() {
            this.bean = referenceBean.get();
        }
    }

    /**
     * 缓存key
     *
     * @param reference 引用糊糊
     * @param bean            Current bean that will be injected
     * @param beanName        Current bean name that will be injected
     * @param injectedType    the type of injected-object
     * @param injectedElement {@link InjectionMetadata.InjectedElement}
     * @return cacheKey
     */
    @Override
    protected String buildInjectedObjectCacheKey(Reference reference, Object bean, String beanName,
                                                 Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) {

        String key = buildReferencedBeanName(reference, injectedType) +
                "#source=" + (injectedElement.getMember()) +
                "#attributes=" + AnnotationUtils.getAttributes(reference, getEnvironment(), true);

        return key;
    }

    private String buildReferencedBeanName(Reference reference, Class<?> injectedType) {
        // 创建Service Bean的名字
        ServiceBeanNameBuilder builder = ServiceBeanNameBuilder.create(reference, injectedType, getEnvironment());
        // 这里, 貌似重复解析占位符, 不过没啥影响~
        return getEnvironment().resolvePlaceholders(builder.build());
    }

    // 创建ReferenceBean, 创建这个引用的bean, 你看哈, 这里创建一个bean
    private ReferenceBean buildReferenceBeanIfAbsent(String referencedBeanName, Reference reference,
                                                     Class<?> referencedType, ClassLoader classLoader)
            throws Exception {
        //  首先, 从referenceBeanCache缓存中, 获得referenceBeanName对应的ReferenceBean对象
        ReferenceBean<?> referenceBean = referenceBeanCache.get(referencedBeanName);
        // 然后, 如果不存在, 则进行创建, 然后, 添加到referenceBeanCahce.get(referenceBeanName);
        if (referenceBean == null) {
            ReferenceBeanBuilder beanBuilder = ReferenceBeanBuilder
                    .create(reference, classLoader, applicationContext)
                    .interfaceClass(referencedType);
            // 创建
            referenceBean = beanBuilder.build();
            // 引用对象缓存
            referenceBeanCache.put(referencedBeanName, referenceBean);
        }
        return referenceBean;
    }

    private void cacheInjectedReferenceBean(ReferenceBean referenceBean,
                                            InjectionMetadata.InjectedElement injectedElement) {
        if (injectedElement.getMember() instanceof Field) {
            // 字段引用对象缓存
            injectedFieldReferenceBeanCache.put(injectedElement, referenceBean);
        } else if (injectedElement.getMember() instanceof Method) {
            // 方法引用对象缓存
            injectedMethodReferenceBeanCache.put(injectedElement, referenceBean);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ServiceBeanExportedEvent) {
            // 暴露服务事件
            onServiceBeanExportEvent((ServiceBeanExportedEvent) event);
        } else if (event instanceof ContextRefreshedEvent) {
            // 上下文刷新事件
            onContextRefreshedEvent((ContextRefreshedEvent) event);
        }
    }

    private void onServiceBeanExportEvent(ServiceBeanExportedEvent event) {
        // 从事件中拿到这个服务bean
        ServiceBean serviceBean = event.getServiceBean();
        // 初始化引用bean的invocationHandler
        initReferenceBeanInvocationHandler(serviceBean);
    }

    private void initReferenceBeanInvocationHandler(ServiceBean serviceBean) {
        // 得到这个bean的名称
        String serviceBeanName = serviceBean.getBeanName();
        // Remove ServiceBean when it's exported
        // 当这个bean暴露的时候, 移除这个bean
        ReferenceBeanInvocationHandler handler = localReferenceBeanInvocationHandlerCache.remove(serviceBeanName);
        // Initialize
        if (handler != null) {
            handler.init();
        }
    }

    private void onContextRefreshedEvent(ContextRefreshedEvent event) {

    }


    @Override
    public void destroy() throws Exception {
        super.destroy();
        this.referenceBeanCache.clear();
        this.localReferenceBeanInvocationHandlerCache.clear();
        this.injectedFieldReferenceBeanCache.clear();
        this.injectedMethodReferenceBeanCache.clear();
    }
}
