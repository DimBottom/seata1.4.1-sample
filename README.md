# Seata使用实例

## 介绍

git地址：https://gitee.com/DimBottom/seata1.4.1-sample

本实例提供 Seata 1.4.1 Nacos 的使用方法，借鉴[《使用Seata彻底解决Spring Cloud中的分布式事务问题！》]( https://juejin.cn/post/6844904001528397831)  该篇博客，该博客使用的是 0.9.0，部分配置不同，sample 项目使用其提供的代码。

> 当然也参考了其他诸多博客以及官方文档（博客）。

## Seata Server Nacos 配置

相比于 0.9.0 的版本，1.4.1 版本配置较为人性化了，但是由于官方文档不是特别友好，导致项目配置坑还是比较多的。

该 Sample 使用 windows 10 环境。server 可以从官方 git 下载：https://github.com/seata/seata

### registry.conf 配置：

注册配置，以 nacos 形式注册，配置文件存储在 nacos 中的 seata.properties 配置文件中。

> 目前官网文档仅告知以 shell 的形式注入配置至 Nacos。
>
> 实际上以及支持单个文件配置，详情见 Issue：https://github.com/seata/seata/issues/3482

```nginx
registry {
  type = "nacos"
  nacos {
    application = "seata-server"
    serverAddr = "127.0.0.1:8848"
    group = "SEATA_GROUP"
    namespace = "seata" # 决定了seate-server注册到的命名空间
    cluster = "default"
    username = "nacos"
    password = "nacos"
  }
}

config {
  type = "nacos"
  nacos {
    serverAddr = "127.0.0.1:8848"
    namespace = "seata" # 决定了配置文件所在的命名空间
    group = "SEATA_GROUP"
    username = "nacos"
    password = "nacos"
    dataId = "seata.properties"
  }
}
```

> 命名空间使用`seata`，某机以`public`测试时发现不断出现`change`记录，但另外的机器未复现该问题。若有相关问题可以通过该方法尝试修复。

### nacos 中 seata.properties 配置：

file.conf 不需要配置了，上文 config 的 type 选择的是 `nacos`，因此配置不再从 file.conf 中读取了。

seata.properties 配置详情见 config.txt 文件。

> config.txt 文件在 https://github.com/seata/seata/blob/develop/script/config-center/config.txt 中可以看到，配置内容参考官方文档。

需要配置存储模式，这里使用 mysql 模式，这里需要注意的是，seata-server 文件中的 lib 缺少 mysql jar包，直接从该目录下面的 jdbc 目录中移出需要的版本。

```properties
# 分组配置
service.vgroupMapping.fsp_tx_group=default
store.mode=db
store.db.datasource=druid
store.db.dbType=mysql
store.db.driverClassName=com.mysql.cj.jdbc.Driver
store.db.url=jdbc:mysql://127.0.0.1:3306/seata-server?useUnicode=true
store.db.user=root
store.db.password=root
store.db.minConn=5
store.db.maxConn=30
store.db.globalTable=global_table
store.db.branchTable=branch_table
store.db.queryLimit=100
store.db.lockTable=lock_table
store.db.maxWait=5000
```

> ，其实这里的分组配置是无效的，分组配置需要在 Nacos 的 SEATA_GROUP 中创建命名格式为`service.vgroupMapping.{分组命名}`，比如 Data Id 为`service.vgroupMapping.fsp_tx_group`，Group 为 `SEATA_GROUP`的配置，格式为`TEXT`，内容为 default。和 sh 导入的配置类似即可。

### 数据库配置

当 store mode 为 db 时，需要创建指定的数据库。

sql 见：https://github.com/seata/seata/tree/develop/script/server/db 文件夹。

> 大部分脚本都可以在 scriopt 中找到。

### 启动

完成上面配置后，即可启动 seata-server.bat，可以在 Nacos 中查询到服务注册成功。

## Spring Boot Client 配置

### Pom.xml 配置

早期版本的配置和与`1.30+`格式有所区别。

推荐使用`seata-spring-boot-starter`。

```xml
<dependency>
    <groupId>io.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
    <version>1.4.1</version>
</dependency>
```

> 如果使用的是 2020.0.x 的 SpringCloud，则使用 `seata-spring-boot-starter` 导入依赖，因为 openfeign:3.0.x 的缘故导致类缺失。
>
> 另外，实际测试发现，数据源代理和`MybatisPlus`有冲突，固定到`1.4.1`后问题修复。

参考 seata-storage-service 的 pom 中配置。

> 可以~~推荐~~使用`spring-cloud-starter-alibaba-seata`进行依赖导入，见另外两个service配置。
>
> ```xml
> <dependency>
>     <groupId>com.alibaba.cloud</groupId>
>     <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
>     <version>2.2.5.RELEASE</version>
> </dependency>
> ```
>
> Sample 中版本号是由`spring-cloud-alibaba-dependencies`控制的。
>

### application.yaml配置

```yaml
seata:
  enabled: true # 开启 seata 事务
  application-id: ${spring.application.name}
  tx-service-group: fsp_tx_group
  service:
    vgroup-mapping:
      fsp_tx_group: default
  config:
    type: nacos
    nacos:
      serverAddr: 127.0.0.1:8848
      group: SEATA_GROUP
      namespace: seate
  registry:
    type: nacos
    nacos:
      application: seata-server # 与服务端配置一致，默认为 seata-server
      server-addr: 127.0.0.1:8848
      namespace: seate
```

> 这里还有一个数据源代理的配置，默认为 true，如果使用多数据源时请关闭，在多数据源中开启 seata 支持。

### 数据库配置

Seata Client 客户端需要 undo_log 表支持，因此要创建该表：

sql 见：https://github.com/seata/seata/tree/develop/script/client/at/db 文件夹。

### 使用`@GlobalTransactional`开启注解。

如：

```java
@GlobalTransactional(name = "fsp-create-order", rollbackFor = Exception.class)
public void create(Order order) {
}
```

> 被调用的服务不需要该注解。

## Seata Docker Server 配置

> seata-server 支持以下环境变量：
>
> - **SEATA_IP**：可选, 指定seata-server启动的IP, 该IP用于向注册中心注册时使用, 如eureka等
>
> - **SEATA_PORT**：可选, 指定seata-server启动的端口, 默认为 `8091`
>
> - **STORE_MODE**：可选, 指定seata-server的事务日志存储方式, 支持`db` ,`file`,redis(Seata-Server 1.3及以上版本支持), 默认是 `file`
>
> - **SERVER_NODE**：可选, 用于指定seata-server节点ID, 如 `1`,`2`,`3`..., 默认为 `根据ip生成`
>
> - **SEATA_ENV**：可选, 指定 seata-server 运行环境, 如 `dev`, `test` 等, 服务启动时会使用 `registry-dev.conf` 这样的配置
>
> - **SEATA_CONFIG_NAME**：可选, 指定配置文件位置, 如 `file:/root/registry`, 将会加载 `/root/registry.conf` 作为配置文件，如果需要同时指定 `file.conf`文件，需要将`registry.conf`的`config.file.name`的值改为类似`file:/root/file.conf`：

Windows10：

```shell
docker run -itd --name seata-server ^
        -p 8091:8091 ^
        -e SEATA_IP="127.0.0.1" ^
        -e SEATA_CONFIG_NAME=file:/root/seata-config/registry ^
        -v H:\shared\seata\default\:/root/seata-config ^
        seataio/seata-server
```

Linux：

```sh
docker run -itd --name seata-server \
        -p 8091:8091 \
        -e SEATA_IP="127.0.0.1" \
        -e SEATA_CONFIG_NAME=file:/root/seata-config/registry \
        -v /usr/shared/seata/default:/root/seata-config \
        seataio/seata-server
```

> 务必指定`SEATA_PORT`为seate_server外部可以访问的IP。建议使用固定的宿主机IP，这样可以同时被外部程序和docker程序访问。

registry.conf 配置：

```nginx
registry {
  type = "nacos"
  nacos {
    application = "seata-server"
    serverAddr = "{docker-bridge-gateway}:8848"
    group = "SEATA_GROUP"
    namespace = "seata"
    cluster = "default"
    username = "nacos"
    password = "nacos"
  }
}

config {
  type = "nacos"
  nacos {
    serverAddr = "{docker-bridge-gateway}:8848"
    namespace = "seata"
    group = "SEATA_GROUP"
    username = "nacos"
    password = "nacos"
    dataId = "seata-docker.properties"
  }
}
```

nacos 中 seata-docker.properties 配置：

```properties
store.mode=db
store.db.datasource=druid
store.db.dbType=mysql
store.db.driverClassName=com.mysql.cj.jdbc.Driver
store.db.url=jdbc:mysql://{docker-bridge-gateway}:3306/seata-server?useUnicode=true
store.db.user=root
store.db.password=root
store.db.minConn=5
store.db.maxConn=30
store.db.globalTable=global_table
store.db.branchTable=branch_table
store.db.queryLimit=100
store.db.lockTable=lock_table
store.db.maxWait=5000
```

> 关于上面的`{docker-bridge-gateway}`请自行替换相应的ip，测试环境为：Windows10、nacos-docker、mysql-docker。不建议相应容器 bridge ip，重启后可能会被重新分配。

### 其他详情见 Sample

1. sample 中依据的相关数据库建表文件可以在 resources 中找到，模块与数据库对应关系如下：

   > seata server=>seata-server
   >
   > seata-storage-service => seata-storage
   >
   > seata-account-service=>seata-account
   >
   > seata-order-service=>seata-order

2. Sample 的测试接口为：http://localhost:8180/order/create?userId=1&productId=1&count=10&money=100

3. 目前只有 Nacos 的配置，之后可能会继续研究。