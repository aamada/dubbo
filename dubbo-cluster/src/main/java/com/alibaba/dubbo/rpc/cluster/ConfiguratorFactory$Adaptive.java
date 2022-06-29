package com.alibaba.dubbo.rpc.cluster;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class ConfiguratorFactory$Adaptive implements ConfiguratorFactory {
    public Configurator getConfigurator(com.alibaba.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        String extName = url.getProtocol();
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.cluster.ConfiguratorFactory) name from url(" + url.toString() + ") use keys([protocol])");
        ConfiguratorFactory extension = (ConfiguratorFactory) ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class).getExtension(extName);
        return extension.getConfigurator(arg0);
    }
}