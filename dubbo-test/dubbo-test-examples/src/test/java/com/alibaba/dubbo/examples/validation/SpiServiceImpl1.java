package com.alibaba.dubbo.examples.validation;

import com.alibaba.dubbo.common.URL;

public class SpiServiceImpl1 implements SpiService{
    @Override
    public String m1(URL url) {
        return SpiServiceImpl1.class.getCanonicalName() + "_m1";
    }

    @Override
    public String m2(URL url) {
        return SpiServiceImpl1.class.getCanonicalName() + "_m2";
    }
}
