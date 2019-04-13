# codegen-demo

Annotation Processor 练手项目
模拟lombok经典注解
- @Data
- @AllArgsConstructor
- @NoArgsConstructor


`anno`子项目是注解处理器相关代码

`mirror`子项目是测试相关代码

已知问题：

@Data/@AllArgsConstructor注解 无法和手写构造器同时使用，编译器会抛出断言异常。

reproducer example:
``` java
@Data
public class User {
    private String username;
    private Integer age;
    
    public User(){} //手写构造器,去掉这一行就可以编译通过了
}
```
