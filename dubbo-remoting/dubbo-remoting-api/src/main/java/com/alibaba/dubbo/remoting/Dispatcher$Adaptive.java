package com.alibaba.dubbo.remoting;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class Dispatcher$Adaptive implements Dispatcher {
    public ChannelHandler dispatch(ChannelHandler arg0, com.alibaba.dubbo.common.URL arg1) {
        if (arg1 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg1;
        String extName = url.getParameter("dispatcher", url.getParameter("dispather", url.getParameter("channel.handler", "all")));
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.remoting.Dispatcher) name from url(" + url.toString() + ") use keys([dispatcher, dispather, channel.handler])");
        Dispatcher extension = (Dispatcher) ExtensionLoader.getExtensionLoader(Dispatcher.class).getExtension(extName);
        return extension.dispatch(arg0, arg1);
    }
}