# charge-demo｜双卡业务闭环 Demo

把医疗结算场景里的双卡资金链路抽象成**可运行的最小系统**（个人复刻工程，非生产系统），只讲一件事：

| 卡 | 资金归属 | 会计科目 | 方向 | 闭环 |
|---|---|---|---|---|
| 预付卡 | 自营储值，钱在自己账上 | 预收账款 | **负债** | 开卡/充值 → 消费扣款 → 退费原路退回 |
| 电子健康卡 | 第三方代收，钱在对方账上 | 应收账款 | **资产** | 开卡/充值 → 消费核销(待结算) → 周期对账结算(回款) |

两者科目方向相反，**流水分标识、报表分科目、严禁并账**（业务规则的核心约束）。

## 技术栈与启动

Java 17+ · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · H2（MySQL 兼容模式，内存库）· Jakarta Validation · JUnit 5。
**零外部服务**：不装 MySQL / Redis / Docker，直接起。

```bash
mvn spring-boot:run
```

服务端口 **8099**，H2 控制台 `/h2-console`（JDBC URL 见 `application.yml`，用户 sa / 空密码）。

若本机 `mvn` 不在 PATH，可用 Maven launcher 直启（把 `MAVEN_HOME` 换成自己的 Maven 目录）：

```bash
MAVEN_HOME="/path/to/apache-maven-3.9.9"
java -cp "$MAVEN_HOME/boot/plexus-classworlds-2.8.0.jar" \
  -Dmaven.home="$MAVEN_HOME" \
  -Dclassworlds.conf="$MAVEN_HOME/bin/m2.conf" \
  -Dmaven.multiModuleProjectDirectory="$(pwd)" \
  org.codehaus.plexus.classworlds.launcher.Launcher -f pom.xml spring-boot:run
```

## 完整业务闭环

```
开卡 ──► 充值 ──► 登记(未收费) ──► 收费
                                    │
              ┌─────────────────────┴─────────────────────┐
              ▼                                           ▼
   预付卡：消费扣款                              健康卡：消费核销
   负债 -（服务交付）                         资产 +（形成应收，待结算）
              │                                           │
              ▼                                           ▼
   退费原路退回                                对账结算（回款）
   负债 +（重新欠款）                         资产 -（应收冲减归零）
              │                                           │
              └────────────► 分科目报表 ◄───────────────┘
                  PREPAID_LIABILITY / EHC_RECEIVABLE 分开统计
```

每一笔变动都在 `card_change_log` 落流水，同时记两套口径：
- `change_amount`：卡余额变动（带符号）
- `subject_delta`：该笔对财务科目的影响，**方向按科目性质推导**

| 动作 | 预付卡（负债） | 健康卡（资产） |
|---|---|---|
| 开卡 / 充值 | `+`（欠客户更多服务） | `0`（第三方卡，不产生我方应收） |
| 消费 / 核销 | `-`（负债转收入） | `+`（开卡公司欠我） |
| 退费 | `+`（退回卡内） | `-`（冲减应收） |
| 对账结算 | — | `-`（回款到账，应收归零） |

## 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/orders` | 登记，生成唯一流水号，状态「未收费」，金额后端计算 |
| POST | `/api/orders/{serialNo}/charge` | 收费：`payMethod=1` 预付卡 / `2` 电子健康卡 |
| POST | `/api/orders/{serialNo}/refund` | 退费：仅管理员、仅已收费、原路退回 |
| GET | `/api/orders/{serialNo}` | 查收费单 |
| POST | `/api/cards` | 开卡 |
| POST | `/api/cards/{cardNo}/recharge` | 充值 |
| POST | `/api/cards/{cardNo}/void?operator=&role=ADMIN` | 作废 |
| GET | `/api/cards/{cardNo}/logs` | 使用记录全量流水 |
| GET | `/api/settlements/pending?cardNo=` | 待结算核销流水（对账清单） |
| POST | `/api/settlements?cardNo=&operator=` | 结算回款，待结算 → 已结算，冲减应收 |
| GET | `/api/reports/finance` | 分科目报表 + 待结算净额 |

## 一分钟冒烟（示例链路 200→172→200）

```bash
# 登记 28 元，返回 serialNo（下面用 CH202611130001 占位，请替换）
curl -s -X POST http://localhost:8099/api/orders -H "Content-Type: application/json" \
  -d '{"customerName":"张三","totalAmount":28,"discount":100}'

# 预付卡 DEMO001（种子余额 200）收费
curl -s -X POST http://localhost:8099/api/orders/CH202611130001/charge \
  -H "Content-Type: application/json" \
  -d '{"payMethod":1,"cardNo":"DEMO001","operator":"cashier01","role":"CASHIER"}'

# 退费（管理员）
curl -s -X POST http://localhost:8099/api/orders/CH202611130001/refund \
  -H "Content-Type: application/json" \
  -d '{"operator":"admin","role":"ADMIN","remark":"客户申请"}'

curl -s http://localhost:8099/api/cards/DEMO001/logs
curl -s http://localhost:8099/api/reports/finance

# 健康卡闭环：核销 -> 看待结算 -> 结算回款
curl -s -X POST http://localhost:8099/api/orders -H "Content-Type: application/json" \
  -d '{"customerName":"李四","totalAmount":60}'
curl -s -X POST http://localhost:8099/api/orders/CH202611130002/charge \
  -H "Content-Type: application/json" \
  -d '{"payMethod":2,"cardNo":"HC001","operator":"cashier01","role":"CASHIER"}'
curl -s http://localhost:8099/api/settlements/pending
curl -s -X POST "http://localhost:8099/api/settlements?cardNo=HC001&operator=finance"
curl -s http://localhost:8099/api/reports/finance
```

## 生产级要素落在哪

| 要求 | 位置 |
|---|---|
| 状态机（未收费 / 已收费 / 记账 / 已退费） | `enums/ChargeStatus.java` |
| 事务边界（扣款 + 流水 + 状态同提交 / 同回滚） | `ChargeService`、`CardService`、`SettlementService` 的 `@Transactional(rollbackFor = Exception.class)` |
| 行锁防并发超扣 | `mapper/CardMapper.java`、`ChargeOrderMapper.java` 的 `SELECT ... FOR UPDATE` |
| 幂等 | 唯一索引 `uk_charge_serial` + 行锁 + 状态机前置判断；重复结算也会拦截 |
| 金额只读 | `dto/Requests.java` 不含 `payAmount`；`ChargeService.register` 按折扣向下取 2 位 |
| 策略模式 | `PaymentStrategy` + `PaymentStrategyFactory`，加新支付方式不动核心服务 |
| 财务科目方向 | `CardService.computeSubjectDelta`、`enums/FinanceSubject`、`FinanceReportService` |
| 审计留痕 | `card_change_log`：变动类型、卡余额、科目金额、关联流水号/批次号、结算状态、操作人 |
| 权限 | `OperatorRole.requireAdmin`（退费 / 作废） |
| 参数校验与统一异常 | Jakarta Validation + `GlobalExceptionHandler` + `Result{code,message,data}` |
| 回归测试 | `ChargeServiceTest`：双卡完整闭环、科目方向、结算净额、7 条业务场景、并发不超扣 |

跑测试：

```bash
mvn test
```

JDK 20/21 若报 `Could not initialize inline Byte Buddy mock maker`：原因是 JVM 默认禁止自附加，参数必须加在**启动 JVM 的位置**，写成 Maven 的 `-D` 参数无效（测试与 Maven 同一 JVM 时尤其如此）：

```bash
MAVEN_OPTS="-Djdk.attach.allowAttachSelf=true" mvn test -DforkCount=0
```

用 java 直启 Maven launcher 时，把 `-Djdk.attach.allowAttachSelf=true` 加在 `java -cp` 之前。

## 业务场景覆盖

| 编号 | 场景 | 测试 |
|---|---|---|
| 1 | 预付卡完整闭环 200→172→200 | `prepaidCardMeasuredLoop` |
| 2 | 健康卡核销生成待结算流水 | `doubleCardFullBusinessLoop` / `refundBeforeSettlementOffsetsReceivable` |
| 3 | 余额不足拦截 | `insufficientBalanceRejected` |
| 4 | 卡已作废拦截 | `cardValidation` |
| 5 | 重复退费拦截 | `idempotency` |
| 6 | 重复收费拦截 | `idempotency` |
| 7 | 金额不可篡改 | `payableCalculatedByBackendOnly` |
| 补充 | 双卡含充值/结算全链路、分科目不并账、并发不超扣、越权退费 | `doubleCardFullBusinessLoop` / `concurrentChargeNeverOverdraws` / `refundRequiresAdmin` |

## 与真实生产的差距（别把 demo 当生产）

1. H2 内存库重启即丢；生产用 `schema-mysql.sql` + MySQL 8 / InnoDB，行锁与事务语义一致。
2. 流水号用进程内 `AtomicInteger`，多实例会撞号；生产换数据库序列或雪花 ID。
3. 只做角色字符串校验，没有 JWT / Spring Security / 权限中心。
4. 科目方向是「演示口径」：真实财务还要收入确认、税费、坏账准备、凭证与复核流程；预付卡消费应同步确认收入，本 demo 只做负债冲减。
5. 结算是一次性把待结算流水全部置为已结算，没有差异清单、部分结算、跨机构对账文件。
6. 未做：组合支付、跨机构通兑、会员积分、优惠券、报告与打印、灰度回滚、监控指标、审计日志落盘。

## 目录

```
charge-demo/
├── pom.xml · README.md · .gitignore
├── src/main/resources/{application.yml, schema.sql, data.sql, schema-mysql.sql}
├── src/main/java/com/klay/charge/
│   ├── common/      统一返回、错误码、业务异常、全局异常处理
│   ├── enums/       收费状态机、双卡支付方式、卡类型、财务科目、角色
│   ├── entity/      charge_order / card / card_change_log
│   ├── dto/         Requests（金额字段故意不暴露）
│   ├── mapper/      MyBatis-Plus + FOR UPDATE 行锁查询
│   ├── service/     收费 / 卡与流水 / 支付策略 / 对账结算 / 分科目报表
│   └── controller/  11 个 REST 接口
└── src/test/java/com/klay/charge/ChargeServiceTest.java
```
