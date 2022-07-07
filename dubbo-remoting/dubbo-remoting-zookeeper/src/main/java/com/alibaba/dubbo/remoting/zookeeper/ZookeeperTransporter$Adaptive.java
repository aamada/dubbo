package com.alibaba.dubbo.remoting.zookeeper;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class ZookeeperTransporter$Adaptive implements ZookeeperTransporter {
    public ZookeeperClient connect(com.alibaba.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        // 从url中获取这些个参数
        String extName = url.getParameter("client", url.getParameter("transporter", "curator"));
        // 如果没有这个名称, 那么直接抛出异常
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter) name from url(" + url.toString() + ") use keys([client, transporter])");
        // 扩展类
        ZookeeperTransporter extension = (ZookeeperTransporter) ExtensionLoader.getExtensionLoader(ZookeeperTransporter.class).getExtension(extName);
        return extension.connect(arg0);
    }
}