package com.alibaba.dubbo.examples.validation;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.SPI;

@SPI
public interface SpiService {
    @Adaptive("impl2")
    String m1(URL url);

    String m2(URL url);
}
