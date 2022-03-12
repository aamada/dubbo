**服务消费者配置**
![](../3_config.png)

![](../4_config.png)

1. 抽象引用配置类AbstractReferenceConfig
2. 服务消费者默认配置ConsumerConfig
3. 服务消费者引用配置类ReferenceConfig(这里有一个点, 看到没有这个类, 它与spring是无关的, 也就是没有spring,也是可以使用的)
   1. 进一步初始化 ReferenceConfig 对象
   2. 校验 ReferenceConfig 对象的配置项
   3. 使用 ReferenceConfig 对象，生成 Dubbo URL 对象数组
   4. 使用 Dubbo URL 对象，应用服务
   5. 主要是ReferenceConfig.get()方法

**属性配置**

![](../5_config.png)

1. 将自动加载 classpath 根目录下的 dubbo.properties ，可以通过JVM启动参数 -Ddubbo.properties.file=xxx.properties 改变缺省配置位置
2. AbstractConfig读取启动参数变量和 properties 配置到配置对象

**XML配置**

![](../6_config.png)

1. 定义:
   1. dubbo.xsd
      1. <xsd:element name="" />, 定义了元素的名称, 例如, <xsd:element name="application" />对应<dubbo:application />
      2. <xsd:element type="" />, 定义了内建数据类型的名称, 例如, <xsd:element type="applicationType" />对应<xsd:complexType name="applicationType" />
      3. <xsd:complexType name="" />, 定义了复杂类型, 例如<xsd:complexType name="applicationType" />
   2. spring.handlers -> DubboNamespaceHandler, 定义了Dubbo的XML Namespace的处理器DubboNamespaceHandler

**注解配置**
![](../7_config.png)

1. EnableDubbo

   1. @EnableDubboConfig

      > 1. DubboConfigConfigurationRegistrar, 实现ImportBeanDefinitionRegistrar接口， 处理@EnableDubboConfig注解，注册相应的DubboConfigConfiguration到Spring容器中。
      > 2. DubboConfigConfiguration
      >    `就是一个Single和Multiple内部类， 其上都有@EnableDubboConfigBindings和@EnableDubboConfig注解`
      >    `前者Single， 其上的注解， prefix都是单数`
      >    `后者Multiple, 其上的注解, prefix都是复数, 且有multiple=true`
      > 3. @EnableDubboConfigBindings
      >    `DubboConfigBindingsRegistrar:表明使用DubboConfigBindingsRegistrar类进行导入`
      >    1. `DubboConfigBindingsRegistrar实现ImportBeanDefinitionRegistrar, EnvironmentAware接口, 处理@EnableDuboConfigBindings注解, 注册相应的Dubbo AbstractConfig到Spring容器中`
      >       ![解析EnableDubboConfigBindings注解](../8_config.png)
      > 4. @EnableDubboConfigBinding
      >    1. DubboConfigBindingRegistrar注册相应的AbstractConfig到容器中
      >       1. registerBeanDefinitions
      >          1. resolveMultipleBeanNames
      >          2. resolveSingleBeanName
      >          3. registerDubboConfigBean
      >          4. registerDubboConfigBindingBeanPostProcessor
      > 5. DubboConfigBindingBeanPostProcessor
      >    1. DubboConfigBinder数据绑定
      >    2. DefaultDubboConfigBinder将配置属性设置到Dubbo config对象中
      >
   2. @DubboComponentScan`配置要扫描@Service和@Reference注解的包或者类们, 从而创建对应的bean对象`

      > 1. DubboComponentScanRegistrar
      >    1. getPackagesToScan(importingClassMetadata);
      >    2. registerServiceAnnotationBeanPostProcessor(packagesToScan, registry);
      >    3. registerReferenceAnnotationBeanPostProcessor(registry);
      > 2. ServiceAnnotationBeanPostProcessor
      >    1. postProcessBeanDefinitionRegistry
      >    2. resolvePackagesToScan
      >    3. findServiceBeanDefinitionHolders
      >    4. registerServiceBean
      > 3. ReferenceAnnotationBeanPostProcessor
      >    1. ReferenceAnnotationBeanPostProcessor
      >    2. doGetInjectedBean
      >    3. onApplicationEvent
      >    4. ReferenceBeanBuilder
      > 4. a
      > 5. a
      > 6. a
      > 7. a
      > 8. a
      > 9. a
      > 10. a
      > 11. a
      > 12. a
      > 13. a
      > 14. a
      > 15. a
      > 16. a
      > 17. a
      >
