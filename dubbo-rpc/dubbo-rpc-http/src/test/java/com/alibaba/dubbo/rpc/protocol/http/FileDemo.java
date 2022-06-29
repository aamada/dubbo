package com.alibaba.dubbo.rpc.protocol.http;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class FileDemo {
    public static void main(String[] args) throws Exception {
        String local = "D:\\a.txt";
        File file = new File(local);
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        fileOutputStream.write("abc".getBytes("utf-8"));
        fileOutputStream.close();
    }
}
