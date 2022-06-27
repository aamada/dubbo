package com.alibaba.dubbo.examples.validation;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class SpiService$Adaptive implements com.alibaba.dubbo.examples.validation.SpiService {
    public java.lang.String m2(com.alibaba.dubbo.common.URL arg0) {
        throw new UnsupportedOperationException("method public abstract java.lang.String com.alibaba.dubbo.examples.validation.SpiService.m2(com.alibaba.dubbo.common.URL) of interface com.alibaba.dubbo.examples.validation.SpiService is not adaptive method!");
    }

    public java.lang.String m1(com.alibaba.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        String extName = url.getParameter("spi.service", "impl1");
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.examples.validation.SpiService) name from url(" + url.toString() + ") use keys([spi.service])");
        com.alibaba.dubbo.examples.validation.SpiService extension = (com.alibaba.dubbo.examples.validation.SpiService) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.examples.validation.SpiService.class).getExtension(extName);
        return extension.m1(arg0);
    }
}