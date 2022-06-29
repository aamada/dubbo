package com.alibaba.dubbo.rpc.cluster;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class Cluster$Adaptive implements Cluster {
    public com.alibaba.dubbo.rpc.Invoker join(Directory arg0) throws com.alibaba.dubbo.rpc.RpcException {
        if (arg0 == null)
            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.cluster.Directory argument == null");
        if (arg0.getUrl() == null)
            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.cluster.Directory argument getUrl() == null");
        com.alibaba.dubbo.common.URL url = arg0.getUrl();
        String extName = url.getParameter("cluster", "failover");
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.cluster.Cluster) name from url(" + url.toString() + ") use keys([cluster])");
        Cluster extension = (Cluster) ExtensionLoader.getExtensionLoader(Cluster.class).getExtension(extName);
        return extension.join(arg0);
    }
}