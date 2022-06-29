package com.alibaba.dubbo.monitor;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class MonitorFactory$Adaptive implements MonitorFactory {
    public Monitor getMonitor(com.alibaba.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.monitor.MonitorFactory) name from url(" + url.toString() + ") use keys([protocol])");
        MonitorFactory extension = (MonitorFactory) ExtensionLoader.getExtensionLoader(MonitorFactory.class).getExtension(extName);
        return extension.getMonitor(arg0);
    }
}