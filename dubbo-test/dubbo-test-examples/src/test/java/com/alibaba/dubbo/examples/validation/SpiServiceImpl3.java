package com.alibaba.dubbo.examples.validation;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Adaptive;

//@Adaptive
public class SpiServiceImpl3 implements SpiService{
    @Override
    public String m1(URL url) {
        return SpiServiceImpl3.class.getCanonicalName() + "_m1";
    }

    @Override
    public String m2(URL url) {
        return SpiServiceImpl3.class.getCanonicalName() + "_m2";
    }
}
