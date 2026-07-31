# Utils Support Example Starter - 功能示例模块

这是一个功能示例集合，展示了 utils-support-common-starter 模块的主要功能和使用方法。

## 功能特性

- 📍 **路径变量功能示例** - 演示路径模板匹配和参数提取
- 🏗️ **对象配置上下文示例** - 演示 Bean 管理和映射定义
- 🌐 **协议框架示例** - 演示多协议客户端/服务器使用
- � **Sync SPI 示例** - 演示通过 SPI 获取同步客户端/服务端
- �🛠️ **常用工具类示例** - 演示各种实用工具方法
- 🎯 **交互式运行器** - 提供友好的示例选择界面
- 🚀 **Spring Boot 集成示例** - 完整的 Web API 示例
- 📊 **POJO 实体类示例** - 用户、产品、订单等完整实体模型
- 🎮 **REST API 控制器** - 用户管理、产品管理、订单管理 API
- 🔧 **数据传输对象 (DTO)** - 请求参数验证和数据传输
- ⚠️ **全局异常处理** - 统一的异常处理和错误响应
- 🔄 **ModelView 内容协商** - 根据 Accept 头和 Content-Type 头进行格式转换
- ⚡ **AsyncFlow 异步流式执行器** - 基于 Java 21 虚拟线程的高性能异步执行框架
- 🤖 **LLM ChatClient 示例** - 展示对话、流式与工具调用的快速用法
- 🧪 **Translator 批量验证** - 检查图片输出/非图片输出是否正常
- 🏗️ **引导构建器框架示例** - 演示分步骤的对象构建和配置流程
- 🕷️ **Spider 示例** - 演示使用 `Site/Spider` 链式创建、可选大脑和低影响抓取

## 📋 示例目录结构

```
src/main/java/com/chua/example/support/
├── ExampleRunner.java                    # 主运行器
├── ExampleSpringBootApplication.java     # Spring Boot 启动类
├── controller/                           # REST API 控制器
│   ├── UserController.java              # 用户管理 API
│   ├── ProductController.java           # 产品管理 API
│   └── OrderController.java             # 订单管理 API
├── pojo/                                 # 实体类
│   ├── User.java                        # 用户实体
│   ├── UserRole.java                    # 用户角色实体
│   ├── UserAddress.java                 # 用户地址实体
│   ├── Product.java                     # 产品实体
│   ├── ProductImage.java                # 产品图片实体
│   ├── ProductAttribute.java            # 产品属性实体
│   ├── Order.java                       # 订单实体
│   ├── OrderItem.java                   # 订单项实体
│   └── ApiResponse.java                 # 统一响应结果
├── dto/                                  # 数据传输对象
│   ├── UserCreateDto.java               # 用户创建 DTO
│   ├── UserUpdateDto.java               # 用户更新 DTO
│   └── ProductCreateDto.java            # 产品创建 DTO
├── exception/                            # 异常处理
│   ├── BusinessException.java           # 业务异常类
│   └── GlobalExceptionHandler.java      # 全局异常处理器
├── mapping/
│   └── PathVariableExample.java         # 路径变量示例
├── objects/
│   └── ConfigureObjectContextExample.java # 对象配置上下文示例
├── async/
│   └── AsyncExample.java                # AsyncFlow 异步流式执行器示例
├── builder/                              # 引导构建器示例
│   ├── BasicGuideBuilderExample.java    # 基础构建器使用示例
│   ├── GuideBuilderListenerExample.java # 构建器监听器示例
│   ├── GuideBuilderErrorHandlingExample.java # 错误处理示例
│   ├── CustomGuideBuilderExample.java   # 自定义构建器示例
│   ├── ConditionalGuideBuilderExample.java # 条件构建示例
│   ├── AsyncGuideBuilderExample.java    # 异步构建示例
│   └── ValidationGuideBuilderExample.java # 验证构建示例
├── protocol/
│   ├── ProtocolExample.java             # 协议框架示例
│   └── ModelViewExample.java            # ModelView 内容协商示例
├── network/
│   └── sync/
│       └── SyncExample.java             # Sync SPI 示例
├── spider/
│   └── GiteeSpiderExample.java          # Gitee 核心爬虫示例（Site/Spider 链式创建，可选大脑）
└── utils/
    └── CommonUtilsExample.java          # 常用工具类示例
```

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>utils-support-example-starter</artifactId>
    <version>4.0.0.27</version>
</dependency>
```

### 2. 运行示例

#### 方式一：使用交互式运行器

```bash
# 使用脚本运行（Windows）
run-examples.bat

# 使用脚本运行（Linux/macOS）
./run-examples.sh

# 或直接运行 Java 类
java -cp target/classes com.chua.example.support.ExampleRunner
```

#### 方式二：直接运行特定示例

```java
// 运行路径变量示例
com.chua.example.support.mapping.PathVariableExample.main(args);

// 运行对象配置上下文示例
com.chua.example.support.objects.ConfigureObjectContextExample.main(args);

// 运行协议框架示例
com.chua.example.support.protocol.ProtocolExample.main(args);

// 运行 ModelView 内容协商示例
com.chua.example.support.protocol.ModelViewExample.main(args);

// 运行 Sync SPI 示例
com.chua.example.support.network.sync.SyncExample.main(args);

// 运行常用工具类示例
com.chua.example.support.utils.CommonUtilsExample.main(args);

// 运行 AsyncFlow 异步流式执行器示例
com.chua.example.support.async.AsyncExample.main(args);

// 运行 Task 断路器示例
com.chua.example.support.task.breaker.TaskBreakerExample.main(args);

// 运行引导构建器基础示例
com.chua.example.support.builder.BasicGuideBuilderExample.main(args);

// 运行引导构建器监听器示例
com.chua.example.support.builder.GuideBuilderListenerExample.main(args);

// 运行引导构建器错误处理示例
com.chua.example.support.builder.GuideBuilderErrorHandlingExample.main(args);

// 运行自定义引导构建器示例
com.chua.example.support.builder.CustomGuideBuilderExample.main(args);

// 运行条件构建示例
com.chua.example.support.builder.ConditionalGuideBuilderExample.main(args);

// 运行异步构建示例
com.chua.example.support.builder.AsyncGuideBuilderExample.main(args);

// 运行验证构建示例
com.chua.example.support.builder.ValidationGuideBuilderExample.main(args);

// 运行 Gitee 核心爬虫示例
com.chua.example.support.spider.GiteeSpiderExample.main(args);
```

Gitee 示例支持通过系统属性切换目标地址和大脑模式：

```bash
mvn -pl utils-support-core-parent/utils-support-example-starter exec:java ^
  -Dexec.mainClass=com.chua.example.support.spider.GiteeSpiderExample ^
  -Dgitee.project.url=https://gitee.com/dromara/Jpom
```

开启大脑时可直接复用示例模块里的 `apikeys.encrypted`，也可以手工传入厂商参数：

```bash
mvn -pl utils-support-core-parent/utils-support-example-starter exec:java ^
  -Dexec.mainClass=com.chua.example.support.spider.GiteeSpiderExample ^
  -Dgitee.project.brain.enabled=true ^
  -Dgitee.project.brain.provider=siliconflow ^
  -Dgitee.project.brain.model=Qwen/Qwen2.5-7B-Instruct
```

#### 方式三：运行 Spring Boot Web 应用

```bash
# 启动 Spring Boot 应用
java -cp target/classes com.chua.example.support.ExampleSpringBootApplication

# 或使用 Maven 启动
mvn spring-boot:run -Dspring-boot.run.main-class=com.chua.example.support.ExampleSpringBootApplication
```

启动后访问：

- 应用地址：http://localhost:8080
- 健康检查：http://localhost:8080/actuator/health
- 用户 API：http://localhost:8080/api/users
- 产品 API：http://localhost:8080/api/products
- 订单 API：http://localhost:8080/api/orders

## 🧪 Translator 批量验证（utils-example）
- 用例：`src/test/java/com/chua/example/support/translator/utils/TranslatorOutputVerificationTest.java`
- 输出目录：`target/translator-test-output-utils`
- 模型路径：
  - 系统属性：`-Donnx.model.path=...`
  - 环境变量：`ONNX_MODEL_PATH=...`
- MCP 开关：
  - 外部 MCP：`-Dmcp.external.enabled=true` 或 `MCP_EXTERNAL_ENABLED=true`
  - Memory MCP：`-Dmcp.memory.enabled=true` 或 `MCP_MEMORY_ENABLED=true`

## 示例说明

### 1. 路径变量功能示例 (PathVariableExample)

演示如何使用路径变量功能：

```java
// 路径模板匹配
String pathTemplate = "/{studentId}/save";
String actualPath = "/1/save";
boolean isMatch = PathVariableUtils.isPathMatch(pathTemplate, actualPath);

// 提取路径变量
Map<String, String> variables = PathVariableUtils.extractPathVariables(pathTemplate, actualPath);
// 结果: {studentId: "1"}

// 从请求中获取路径变量
String studentId = MappingUtils.getPathVariable("studentId", request);
Integer studentIdInt = MappingUtils.getPathVariableAsInt("studentId", request);
```

### 2. 对象配置上下文示例 (ConfigureObjectContextExample)

演示如何使用对象配置上下文：

```java
// 创建配置对象上下文
ConfigureObjectContext context = ConfigureObjectContext.newDefault();

// 注册 Bean
context.register("testService", new TestService());

// 获取 Bean
TestService service = context.getBean("testService", TestService.class);

// 检查 Bean 是否存在
boolean exists = context.containsBean("testService");
```

### 3. 协议框架示例 (ProtocolExample)

演示如何使用协议框架：

```java
// 创建 HTTP 协议客户端
Protocol httpProtocol = Protocol.create("http");
ClientSetting clientSetting = ClientSetting.builder()
    .host("httpbin.org")
    .port(80)
    .build();

ProtocolClient client = httpProtocol.createClient(protocolSetting);
client.connect();
```

### 4. 常用工具类示例 (CommonUtilsExample)

演示各种工具类的使用：

```java
// 字符串工具
boolean isEmpty = StringUtils.isEmpty("");
String trimmed = StringUtils.trim("  hello  ");

// 集合工具
boolean isNotEmpty = CollectionUtils.isNotEmpty(list);
String first = CollectionUtils.first(list);

// 类型转换
Integer number = ConvertUtils.convert("123", Integer.class);

// 反射工具
Object value = ReflectUtils.getFieldValue(obj, "fieldName");
```

### 5. AsyncFlow 异步流式执行器示例 (AsyncExample)

演示基于 Java 21 虚拟线程的异步流式执行器：

```java
// 基本使用 - 创建和执行异步任务
String result = AsyncFlow.of(() -> "Hello AsyncFlow")
    .execute();

// 链式操作 - 同步转换
String chainResult = AsyncFlow.of(() -> "Hello")
    .then(value -> value + " World")
    .then(value -> value + "!")
    .execute();

// 异步链式操作
String asyncResult = AsyncFlow.of(() -> "Async")
    .thenAsync(value -> AsyncFlow.of(() -> value + " Chain"))
    .execute();

// 回调处理
AsyncFlow.of(() -> "Success Task")
    .onSuccess(result -> System.out.println("成功: " + result))
    .onFailure(error -> System.out.println("失败: " + error.getMessage()))
    .onComplete(() -> System.out.println("任务完成"))
    .execute();

// 线程池选择
AsyncFlow.of(() -> "CPU密集型任务", ThreadPoolManager.PoolType.CPU_INTENSIVE)
    .execute();

// 超时控制
AsyncFlow.of(() -> { ThreadUtils.sleep(500); return "Task"; })
    .timeout(Duration.ofSeconds(1))
    .execute();

// 异常处理和恢复
String recovered = AsyncFlow.of(() -> { throw new RuntimeException("失败"); })
    .recover("默认值")
    .execute();

// 异常只传递给onFailure回调，不向外抛出
String safeResult = AsyncFlow.of(() -> { throw new RuntimeException("异常"); })
    .onFailure(error -> System.out.println("失败: " + error.getMessage()))
    .executeSafely(); // 返回null，不抛出异常

// 控制异常抛出行为
String controlledResult = AsyncFlow.of(() -> { throw new RuntimeException("异常"); })
    .onFailure(error -> System.out.println("失败: " + error.getMessage()))
    .execute(false); // 返回null，不抛出异常

// 并行执行
List<String> results = AsyncFlow.allOf(
    () -> "任务1",
    () -> "任务2",
    () -> "任务3"
).execute();

// 延迟执行
String delayed = AsyncFlow.delay(() -> "延迟任务", Duration.ofMillis(500))
    .execute();

// 组合操作
String combined = AsyncFlow.of(() -> "Hello")
    .combine(AsyncFlow.of(() -> "World"), (a, b) -> a + " " + b)
    .execute();

// AsyncFlowRule 高级用法
String advanced = AsyncFlow.<String>create()
    .next(AsyncFunction.of(input -> "步骤1"))
    .next(AsyncFunction.withRetry(value -> {
        if (Math.random() < 0.7) throw new RuntimeException("随机失败");
        return value + " -> 成功";
    }, 3)) // 最多重试3次
    .execute();
```

#### 支持的线程池类型

| 类型          | 说明             | 适用场景             |
| ------------- | ---------------- | -------------------- |
| VIRTUAL       | 虚拟线程池       | 高并发 IO 密集型任务 |
| CPU_INTENSIVE | CPU 密集型线程池 | 计算密集型任务       |
| IO_INTENSIVE  | IO 密集型线程池  | IO 密集型任务        |
| SCHEDULED     | 定时任务线程池   | 定时和延迟任务       |
| SINGLE_THREAD | 单线程池         | 需要顺序执行的任务   |

#### AsyncFunction 高级功能

```java
// 带重试机制
AsyncFunction.withRetry(function, maxRetries)

// 带超时控制
AsyncFunction.withTimeout(function, timeoutMs)

// 带日志记录
AsyncFunction.withLogging(function, logger)

// 并行执行多个函数
AsyncFunction.parallel(function1, function2, function3)

// 延迟执行
AsyncFunction.delayed(function, delayMs)
```

## 配置说明

### 运行环境配置

```properties
# JVM 参数建议
-Xms256m
-Xmx512m
-Dfile.encoding=UTF-8

# 日志级别
logging.level.com.chua.example=DEBUG
```

### 示例配置

```properties
# 协议示例配置
example.protocol.http.timeout=5000
example.protocol.websocket.url=ws://echo.websocket.org
example.protocol.tcp.port=8888
example.protocol.udp.port=9999

# 路径变量示例配置
example.pathvariable.debug=true
example.pathvariable.cache.enabled=true
```

## 注意事项

1. **环境要求**: Java 21+
2. **依赖管理**: 需要 utils-support-common-starter 作为依赖
3. **网络访问**: 部分示例需要网络连接（如 HTTP 协议示例）
4. **端口占用**: TCP/UDP 示例可能需要特定端口
5. **权限要求**: 某些功能可能需要特定的系统权限

## 🌐 Spring Boot Web API 示例

### API 端点说明

#### 用户管理 API (`/api/users`)

| 方法   | 路径                        | 描述         | 参数                |
| ------ | --------------------------- | ------------ | ------------------- |
| GET    | `/api/users`                | 获取用户列表 | page, size, keyword |
| GET    | `/api/users/{id}`           | 获取用户详情 | id                  |
| POST   | `/api/users`                | 创建新用户   | UserCreateDto       |
| PUT    | `/api/users/{id}`           | 更新用户信息 | id, UserUpdateDto   |
| DELETE | `/api/users/{id}`           | 删除用户     | id                  |
| GET    | `/api/users/{id}/roles`     | 获取用户角色 | id                  |
| GET    | `/api/users/{id}/addresses` | 获取用户地址 | id                  |
| PATCH  | `/api/users/{id}/status`    | 更新用户状态 | id, status          |

#### 产品管理 API (`/api/products`)

| 方法   | 路径                            | 描述         | 参数                                    |
| ------ | ------------------------------- | ------------ | --------------------------------------- |
| GET    | `/api/products`                 | 获取产品列表 | page, size, keyword, categoryId, status |
| GET    | `/api/products/{id}`            | 获取产品详情 | id                                      |
| POST   | `/api/products`                 | 创建新产品   | ProductCreateDto                        |
| PUT    | `/api/products/{id}`            | 更新产品信息 | id, ProductCreateDto                    |
| DELETE | `/api/products/{id}`            | 删除产品     | id                                      |
| GET    | `/api/products/{id}/images`     | 获取产品图片 | id                                      |
| GET    | `/api/products/{id}/attributes` | 获取产品属性 | id                                      |
| PATCH  | `/api/products/{id}/status`     | 更新产品状态 | id, status                              |
| PATCH  | `/api/products/{id}/stock`      | 更新产品库存 | id, stock                               |

#### 订单管理 API (`/api/orders`)

| 方法  | 路径                       | 描述         | 参数                                           |
| ----- | -------------------------- | ------------ | ---------------------------------------------- |
| GET   | `/api/orders`              | 获取订单列表 | page, size, userId, status, startDate, endDate |
| GET   | `/api/orders/{id}`         | 获取订单详情 | id                                             |
| POST  | `/api/orders`              | 创建新订单   | Order                                          |
| PUT   | `/api/orders/{id}`         | 更新订单信息 | id, Order                                      |
| PATCH | `/api/orders/{id}/cancel`  | 取消订单     | id, reason                                     |
| PATCH | `/api/orders/{id}/pay`     | 支付订单     | id, paymentMethod                              |
| PATCH | `/api/orders/{id}/ship`    | 订单发货     | id, expressCompany, expressNo                  |
| PATCH | `/api/orders/{id}/confirm` | 确认收货     | id                                             |
| GET   | `/api/orders/{id}/items`   | 获取订单项   | id                                             |
| GET   | `/api/orders/statistics`   | 获取订单统计 | userId                                         |

### API 使用示例

#### 创建用户

```bash
curl -X POST "http://localhost:8080/api/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "Test123456",
    "confirmPassword": "Test123456",
    "phone": "13800138000",
    "realName": "测试用户",
    "age": 25,
    "gender": 1,
    "bio": "这是一个测试用户"
  }'
```

#### 获取用户列表

```bash
curl -X GET "http://localhost:8080/api/users?page=1&size=10&keyword=test"
```

#### 创建产品

```bash
curl -X POST "http://localhost:8080/api/products" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试产品",
    "code": "TEST001",
    "categoryId": 1,
    "brand": "测试品牌",
    "price": 99.99,
    "costPrice": 50.00,
    "stock": 100,
    "minStock": 10,
    "weight": 500.00,
    "description": "这是一个测试产品",
    "isRecommended": 1,
    "isNew": 1
  }'
```

#### 创建订单

```bash
curl -X POST "http://localhost:8080/api/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "totalAmount": 199.99,
    "productAmount": 189.99,
    "shippingFee": 10.00,
    "paymentMethod": 1,
    "receiverName": "张三",
    "receiverPhone": "13800138000",
    "receiverAddress": "广东省深圳市南山区科技园南区深南大道10000号",
    "remark": "请尽快发货"
  }'
```

### 响应格式

所有 API 都使用统一的响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2025-07-23T10:30:00",
  "traceId": "uuid-trace-id",
  "pageInfo": {
    "page": 1,
    "size": 10,
    "total": 100,
    "totalPages": 10,
    "hasNext": true,
    "hasPrevious": false,
    "isFirst": true,
    "isLast": false
  }
}
```

### 错误处理

API 提供了完善的错误处理机制：

- **400**: 参数错误或验证失败
- **401**: 未授权访问
- **403**: 禁止访问
- **404**: 资源不存在
- **500**: 服务器内部错误

错误响应示例：

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": ["username: 用户名不能为空", "email: 邮箱格式不正确"],
  "timestamp": "2025-07-23T10:30:00",
  "traceId": "uuid-trace-id"
}
```

## 🔄 ModelView 内容协商功能

### 功能概述

ModelView 是一个强大的内容协商和格式转换系统，能够根据 HTTP 请求的 `Accept` 头和响应的 `Content-Type` 头自动进行数据格式转换。支持 JSON、XML、HTML、纯文本等多种格式之间的相互转换。

### 核心组件

#### 1. ModelView 接口

```java
public interface ModelView {
    ServletResponse processView(ServletRequest request, ServletResponse response);
    boolean supports(String sourceMediaType, String targetMediaType);
    String[] getSupportedSourceMediaTypes();
    String[] getSupportedTargetMediaTypes();
}
```

#### 2. ContentConverter 接口

```java
public interface ContentConverter {
    byte[] convert(byte[] sourceData, String sourceMediaType, String targetMediaType, Charset charset);
    byte[] convertFromObject(Object object, String mediaType, Charset charset);
    <T> T convertToObject(byte[] data, String mediaType, Class<T> targetType, Charset charset);
    boolean canConvert(String sourceMediaType, String targetMediaType);
}
```

#### 3. ModelViewManager 管理器

```java
ModelViewManager manager = ModelViewManager.getInstance();
manager.processView(request, response);
```

### 支持的格式转换

| 源格式 | 目标格式 | 转换器               | 说明                     |
| ------ | -------- | -------------------- | ------------------------ |
| JSON   | XML      | JsonContentConverter | JSON 对象转换为 XML 结构 |
| JSON   | HTML     | JsonContentConverter | JSON 格式化为 HTML 显示  |
| JSON   | 纯文本   | JsonContentConverter | JSON 美化格式输出        |
| 纯文本 | JSON     | TextContentConverter | 文本包装为 JSON 字符串   |
| 纯文本 | HTML     | TextContentConverter | 文本转换为 HTML 页面     |
| 纯文本 | XML      | TextContentConverter | 文本包装为 XML CDATA     |
| 对象   | JSON     | JsonContentConverter | Java 对象序列化为 JSON   |
| 对象   | XML      | JsonContentConverter | Java 对象转换为 XML      |

### 使用示例

#### 基本内容转换

```java
// 创建转换器
JsonContentConverter converter = new JsonContentConverter();

// 对象转 JSON
Map<String, Object> data = Map.of("name", "张三", "age", 25);
byte[] jsonData = converter.convertFromObject(data, MediaTypes.APPLICATION_JSON);

// JSON 转 XML
byte[] xmlData = converter.convert(jsonData,
    MediaTypes.APPLICATION_JSON, MediaTypes.APPLICATION_XML);

// JSON 转对象
Map<String, Object> result = converter.convertToObject(jsonData,
    MediaTypes.APPLICATION_JSON, Map.class);
```

#### 内容协商示例

```java
// 创建 ModelView
DefaultModelView modelView = new DefaultModelView();

// 模拟请求和响应
ServletRequest request = createRequest("Accept: text/xml");
ServletResponse response = createResponse(jsonData, "application/json");

// 执行内容协商
ServletResponse result = modelView.processView(request, response);
// 响应会自动转换为 XML 格式
```

#### 自定义转换器

```java
public class CsvContentConverter implements ContentConverter {
    @Override
    public byte[] convert(byte[] sourceData, String sourceMediaType,
                         String targetMediaType, Charset charset) {
        if (MediaTypes.isJson(sourceMediaType) && "text/csv".equals(targetMediaType)) {
            return convertJsonToCsv(sourceData, charset);
        }
        throw new ConversionException("不支持的转换");
    }

    @Override
    public boolean canConvert(String sourceMediaType, String targetMediaType) {
        return MediaTypes.isJson(sourceMediaType) && "text/csv".equals(targetMediaType);
    }

    // 实现其他必需方法...
}

// 注册自定义转换器
DefaultModelView modelView = new DefaultModelView();
modelView.addConverter(new CsvContentConverter());
```

#### 过滤器集成

```java
// 创建 ModelView 过滤器
ModelViewFilter filter = new ModelViewFilter();
filter.setContentNegotiationEnabled(true);
filter.setVerboseLogging(true);

// 在协议服务器中注册过滤器
protocolServer.addFilter(filter);
```

### 配置选项

#### MediaTypes 常量

```java
// 常用媒体类型
MediaTypes.APPLICATION_JSON     // application/json
MediaTypes.APPLICATION_XML      // application/xml
MediaTypes.TEXT_HTML           // text/html
MediaTypes.TEXT_PLAIN          // text/plain
MediaTypes.TEXT_CSV            // text/csv

// 工具方法
MediaTypes.isJson(mediaType)    // 检查是否为 JSON 类型
MediaTypes.isXml(mediaType)     // 检查是否为 XML 类型
MediaTypes.isText(mediaType)    // 检查是否为文本类型
MediaTypes.getMainType(mediaType) // 获取主媒体类型
```

#### 转换器优先级

```java
public class HighPriorityConverter implements ContentConverter {
    @Override
    public int getPriority() {
        return 1; // 数值越小优先级越高
    }
}
```

### 最佳实践

1. **合理设置优先级**：确保最常用的转换器有较高优先级
2. **异常处理**：转换失败时应返回原始响应，不影响正常流程
3. **性能考虑**：对于大数据量转换，考虑使用流式处理
4. **缓存策略**：对于相同的转换请求，可以考虑缓存结果
5. **日志记录**：启用详细日志有助于调试转换问题

### 运行示例

```bash
# 运行 ModelView 示例
java -cp target/classes com.chua.example.support.protocol.ModelViewExample

# 或使用交互式运行器
java -cp target/classes com.chua.example.support.ExampleRunner
# 然后选择选项 3
```

## 🏗️ 引导构建器框架示例

### 功能概述

引导构建器框架是一个强大的链式构建器模式实现，支持多步骤的对象构建流程。它结合了建造者模式、责任链模式和流式接口的优点，提供了类型安全、易于扩展的构建解决方案。

### 核心特性

- **🔗 链式调用**：流畅的 API 设计，支持方法链式调用
- **📋 强制顺序**：按预定义顺序执行构建步骤，避免遗漏
- **🛡️ 类型安全**：完整的泛型支持，编译时类型检查
- **🔍 状态跟踪**：完整的构建器生命周期状态管理
- **👂 事件监听**：丰富的事件回调，支持监控和扩展
- **⚡ 错误处理**：完善的异常处理和恢复机制
- **🔄 重置重试**：支持构建器重置和重新配置

### 示例说明

#### 1. 基础构建器示例 (BasicGuideBuilderExample)

演示最基本的引导构建器使用方法：

```java
// 创建管理器并配置构建器链
GuideBuilderManager<ApplicationConfig> manager = new GuideBuilderManager<>();
manager.addBuilder(DatabaseConfigBuilder::new)
       .addBuilder(CacheConfigBuilder::new)
       .addBuilder(SecurityConfigBuilder::new);

// 执行分步骤构建
DatabaseConfigBuilder dbBuilder = (DatabaseConfigBuilder) manager.start();
CacheConfigBuilder cacheBuilder = (CacheConfigBuilder) dbBuilder
    .host("localhost").port(3306).database("myapp")
    .username("root").password("password")
    .validate().next();

SecurityConfigBuilder securityBuilder = (SecurityConfigBuilder) cacheBuilder
    .redis().host("localhost").port(6379)
    .maxSize(5000).ttlHours(2)
    .validate().next();

ApplicationConfig config = (ApplicationConfig) securityBuilder
    .development().jwtSecret("my-secret")
    .allowedOrigins("http://localhost:3000")
    .validate().next();
```

#### 2. 监听器示例 (GuideBuilderListenerExample)

演示如何使用监听器监控构建过程：

```java
// 添加监听器
manager.addListener(new DetailedBuildListener())
       .addListener(new ProgressTracker())
       .addListener(new PerformanceMonitor());

// 监听器会自动接收构建过程中的各种事件
public class DetailedBuildListener implements GuideBuilderListener<ApplicationConfig> {
    @Override
    public void onBuildStarted(GuideBuilderManager<ApplicationConfig> manager, int totalBuilders) {
        System.out.println("🚀 开始构建流程，总共 " + totalBuilders + " 个构建器");
    }

    @Override
    public void onProgressUpdated(GuideBuilderManager<ApplicationConfig> manager,
                                 int currentIndex, int totalBuilders, double progress) {
        System.out.println("📊 构建进度: " + (int)(progress * 100) + "%");
    }
}
```

#### 3. 错误处理示例 (GuideBuilderErrorHandlingExample)

演示如何处理构建过程中的错误：

```java
try {
    dbBuilder.host("")  // 故意设置无效配置
            .validate();
} catch (IllegalArgumentException e) {
    System.out.println("捕获验证错误: " + e.getMessage());

    // 修正配置后重试
    dbBuilder.reset()
            .host("localhost")
            .database("test_db")
            .username("test_user")
            .password("test_password")
            .validate();
}
```

#### 4. 自定义构建器示例 (CustomGuideBuilderExample)

演示如何创建自定义的构建器：

```java
public class NetworkConfigBuilder extends AbstractGuideBuilder<NetworkConfigBuilder, ServerConfig> {
    private String host = "localhost";
    private int port = 8080;

    public NetworkConfigBuilder host(String host) {
        this.host = host;
        return self();
    }

    public NetworkConfigBuilder port(int port) {
        this.port = port;
        return self();
    }

    @Override
    public NetworkConfigBuilder validate() {
        if (StringUtils.isEmpty(host)) {
            throw new IllegalArgumentException("主机地址不能为空");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("端口必须在1-65535范围内");
        }
        return self();
    }
}
```

#### 5. 条件构建示例 (ConditionalGuideBuilderExample)

演示如何在构建过程中使用条件逻辑：

```java
// 条件构建方法
public EnvironmentConfigBuilder when(boolean condition, Consumer<EnvironmentConfigBuilder> action) {
    if (condition) {
        action.accept(this);
    }
    return self();
}

// 使用条件构建
envBuilder.environment("development")
         .when(true, builder -> builder.enableHotReload())   // 开发环境启用热重载
         .when(false, builder -> builder.enableSsl())        // 开发环境不启用SSL
         .ifDevelopment(builder -> {
             builder.setLogLevel("DEBUG");
             builder.enableDetailedLogging();
         });
```

#### 6. 异步构建示例 (AsyncGuideBuilderExample)

演示如何在异步环境中使用构建器：

```java
// 异步构建
CompletableFuture<AsyncConfig> future = CompletableFuture.supplyAsync(() -> {
    GuideBuilderManager<AsyncConfig> manager = new GuideBuilderManager<>();
    manager.addBuilder(AsyncDataSourceBuilder::new)
           .addBuilder(AsyncCacheBuilder::new);

    AsyncDataSourceBuilder dataSourceBuilder = (AsyncDataSourceBuilder) manager.start();
    return (AsyncConfig) dataSourceBuilder
        .connectionUrl("jdbc:mysql://async-db:3306/test")
        .username("async_user")
        .simulateAsyncValidation()  // 模拟异步验证
        .validate()
        .next();
}, executor);

AsyncConfig config = future.get(30, TimeUnit.SECONDS);
```

#### 7. 验证构建示例 (ValidationGuideBuilderExample)

演示如何进行复杂的验证：

```java
@Override
public UserInfoBuilder validate() {
    // 用户名验证
    if (!USERNAME_PATTERN.matcher(username).matches()) {
        throw new IllegalArgumentException("用户名必须是3-20位字母、数字或下划线");
    }

    // 邮箱验证
    if (!EMAIL_PATTERN.matcher(email).matches()) {
        throw new IllegalArgumentException("邮箱格式不正确");
    }

    // 年龄验证
    if (age < 0 || age > 150) {
        throw new IllegalArgumentException("年龄必须在0-150之间");
    }

    return self();
}

// 自定义验证规则
public UserInfoBuilder validateWithCustomRules() {
    validate(); // 执行基本验证

    // 自定义业务规则
    if (username.toLowerCase().contains("admin")) {
        throw new IllegalArgumentException("用户名不能包含'admin'");
    }

    return self();
}
```

### 运行示例

```bash
# 运行基础构建器示例
java -cp target/classes com.chua.example.support.builder.BasicGuideBuilderExample

# 运行监听器示例
java -cp target/classes com.chua.example.support.builder.GuideBuilderListenerExample

# 运行错误处理示例
java -cp target/classes com.chua.example.support.builder.GuideBuilderErrorHandlingExample

# 运行自定义构建器示例
java -cp target/classes com.chua.example.support.builder.CustomGuideBuilderExample

# 运行条件构建示例
java -cp target/classes com.chua.example.support.builder.ConditionalGuideBuilderExample

# 运行异步构建示例
java -cp target/classes com.chua.example.support.builder.AsyncGuideBuilderExample

# 运行验证构建示例
java -cp target/classes com.chua.example.support.builder.ValidationGuideBuilderExample
```

### 最佳实践

1. **构建器设计**：继承 `AbstractGuideBuilder` 而不是直接实现接口
2. **验证策略**：在 `validate()` 方法中进行参数验证
3. **错误处理**：使用具体的异常类型和清晰的错误消息
4. **监听器使用**：使用监听器进行日志记录和监控
5. **性能优化**：使用懒加载创建构建器实例

## 扩展开发

如需添加新的示例：

1. 在相应的包下创建示例类
2. 实现 `main` 方法
3. 在 `ExampleRunner` 中添加菜单选项
4. 更新 README 文档

### 二维码（ZXing）示例

- com.chua.example.support.qr.QrRoundDotRoundEyeExample：艺术码点（ROUND_DOT） + 圆角环形码眼，运行后弹出 Swing 预览
- com.chua.example.support.qr.QrCirclePointCircleEyeExample：圆点 + 圆形码眼
- com.chua.example.support.qr.QrWithLogoExample：带 Logo 的圆润风格二维码
- 工具类：com.chua.example.support.util.ImageSwingUtils（Swing 弹窗展示图片）

运行方式：直接执行对应类的 main 方法即可。

如需扩展 Spring Boot API：

1. 在 `controller` 包下创建新的控制器
2. 在 `pojo` 包下创建对应的实体类
3. 在 `dto` 包下创建数据传输对象
4. 更新全局异常处理器（如需要）

## 许可证

本项目采用 Apache License 2.0 许可证。
