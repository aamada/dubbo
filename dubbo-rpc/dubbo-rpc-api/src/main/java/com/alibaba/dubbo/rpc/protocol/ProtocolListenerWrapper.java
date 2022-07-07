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
package com.alibaba.dubbo.rpc.protocol;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.ExporterListener;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.InvokerListener;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.listener.ListenerExporterWrapper;
import com.alibaba.dubbo.rpc.listener.ListenerInvokerWrapper;

import java.util.Collections;
import java.util.List;

/**
 * ListenerProtocol
 */
public class ProtocolListenerWrapper implements Protocol {

    private final Protocol protocol;

    public ProtocolListenerWrapper(Protocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol == null");
        }
        this.protocol = protocol;
    }

    @Override
    public int getDefaultPort() {
        return protocol.getDefaultPort();
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        // url
        // injvm://127.0.0.1/com.alibaba.dubbo.examples.annotation.api.AnnotationService?anyhost=true&application=annotationprovider&bean.name=ServiceBean:com.alibaba.dubbo.examples.annotation.api.AnnotationService&bind.ip=192.168.204.1&bind.port=20883&default.timeout=5000&dubbo=2.0.2&generic=false&interface=com.alibaba.dubbo.examples.annotation.api.AnnotationService&methods=sayHello&pid=18036&side=provider&timestamp=1656511416557
        // 注册中心
        if (Constants.REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
            return protocol.export(invoker);
        }
        // 暴露服务, 创建Exporter对象
        Exporter<T> export = protocol.export(invoker);
        // spi获得ExporterListener数组
        List<ExporterListener> listeners = ExtensionLoader.getExtensionLoader(ExporterListener.class)
                .getActivateExtension(invoker.getUrl(), Constants.EXPORTER_LISTENER_KEY);
        // 创建ExporterListener的Exporter对象
        return new ListenerExporterWrapper<T>(export, Collections.unmodifiableList(listeners));
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        // 注册中心协议
        // InjvmProtocol
        Invoker<T> refer = protocol.refer(type, url);
        if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
            // 引用服务
            return refer;
        }
        // 获得InvokerListeners数组
        List<InvokerListener> listeners = ExtensionLoader
                .getExtensionLoader(InvokerListener.class)
                .getActivateExtension(url, Constants.INVOKER_LISTENER_KEY);
        // 包装成不可变
        List<InvokerListener> listeners2 = Collections.unmodifiableList(listeners);
        // 创建ListenerInvokerWrapper
        return new ListenerInvokerWrapper<T>(refer, listeners2);
    }

    @Override
    public void destroy() {
        protocol.destroy();
    }

}