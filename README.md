# 股权归属 / 行权核对系统

虚构股权计划的归属与行权数量核对系统。**所有规则、条件、场景均为虚构，仅用于数量核对演示，不构成税务、法律或投资建议。**

- **backend/**：Spring Boot 3 + Java 17，所有股数计算使用 `BigDecimal`（`NUMERIC(18,0)`，0 位小数、HALF_UP），显式展示五个独立数量，不用单一余额覆盖阶段。
- **frontend/**：Angular 17 独立组件，授予时间轴（月度节点）+ 五阶段额度解释。
- **数据库**：PostgreSQL，表结构由 Flyway (`V1__init.sql`) 创建；无外部数据库时后端默认以 `embedded` profile 启动进程内真实 PostgreSQL（zonky embedded-postgres）。

## 数量口径（按查询日 D 回看）

| 数量 | 含义 |
| --- | --- |
| 未归属 unvested | 节点归属日晚于 D，或条件批次在 D 当天尚未人工标记满足 |
| 已归属 vested | 归属日 ≤ D 且（非条件节点或条件满足日 ≤ D）的节点股数之和 |
| 已行权 exercised | 申请日 ≤ D 且确认日 ≤ D 的申请数量 |
| 待确认占用 pending | 申请日 ≤ D、且在 D 当天仍处于待确认（未确认/未取消）的申请数量 |
| 可行权 exercisable | vested − exercised − pending |

恒等校验（服务端每次查询都会断言）：

```
total = unvested + vested
vested = exercised + pending + exercisable
```

**归属规则（虚构）**：授予日起每月一个节点；月底授予（如 1/31）自动落在各月最后一天（2/28、4/30…）；
悬崖期内节点股数为 0，悬崖节点一次性归属累积量；累计股数 `round(总数 × 月序 / 总月数, HALF_UP)`，
节点股数为相邻累计之差，节点合计严格等于总数。条件批次只在人工标记后，自满足日起归属。

**行权规则（虚构）**：申请按申请日的可行权额度校验，不得超出；待确认申请立即占用额度；
确认后计入已行权；取消未确认申请后，自取消日起释放额度；历史查询按各事件日期回看当时状态。

## 运行

后端（默认 embedded profile，自动启动进程内 PostgreSQL）：

```bash
cd backend
mvn spring-boot:run
# 用外部 PostgreSQL：
# SPRING_PROFILES_ACTIVE=external \
# SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/equity \
# mvn spring-boot:run
# 或先启动 db/docker-compose.yml
```

前端：

```bash
cd frontend
npm install
npm start   # http://localhost:4200 ，/api 代理到 8080
```

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/grants` | 登记授予并展开悬崖/月度节点（可带条件批次） |
| GET | `/api/grants` | 授予列表 |
| GET | `/api/grants/{id}/snapshot?date=YYYY-MM-DD` | 五阶段数量 + 节点/申请明细 |
| POST | `/api/grants/{id}/nodes/{month}/satisfy?satisfiedAt=YYYY-MM-DD` | 人工标记条件批次满足 |
| POST | `/api/grants/{id}/exercises` | 发起行权申请 `{quantity, asOf}`（超额返回 409） |
| POST | `/api/exercises/{id}/confirm?date=` | 确认申请 |
| POST | `/api/exercises/{id}/cancel?date=` | 取消未确认申请，释放额度 |

## 核对场景

集成测试 `EquityTimelineIntegrationTest` 覆盖：**月底授予 + 12 个月悬崖 + 条件批次 + 部分行权 + 超额拒绝 + 取消释放 + 历史回看**，
逐阶段断言五个数量（而非只对一个余额）：

```bash
cd backend && mvn test
```

关键阶段（2026-01-31 授予 4800 股、48 个月、12 个月悬崖、第 12 节点为条件批次）：

| 查询日 | 未归属 | 已归属 | 已行权 | 待确认 | 可行权 | 事件 |
| --- | --- | --- | --- | --- | --- | --- |
| 2027-01-30 | 4800 | 0 | 0 | 0 | 0 | 悬崖前一天 |
| 2027-01-31 | 4800 | 0 | 0 | 0 | 0 | 悬崖日到但条件未标记 |
| 2027-02-14 | 4800 | 0 | 0 | 0 | 0 | 回看标记日前 |
| 2027-02-15 | 3600 | 1200 | 0 | 0 | 1200 | 条件人工标记满足 |
| 2027-02-16 | 3600 | 1200 | 0 | 700 | 500 | 申请行权 700（待确认） |
| 2027-02-17 | 3600 | 1200 | 0 | 700 | 500 | 再申请 600 被拒（可用仅 500） |
| 2027-02-20 | 3600 | 1200 | 0 | 1200 | 0 | 再申请 500，额度占满 |
| 2027-02-21 | 3600 | 1200 | 0 | 1200 | 0 | 取消前回看，仍占用 |
| 2027-02-22 | 3600 | 1200 | 0 | 700 | 500 | 取消第二笔，额度释放 |
| 2027-02-23 | 3600 | 1200 | 0 | 700 | 500 | 确认前回看 |
| 2027-02-24 | 3600 | 1200 | 700 | 0 | 500 | 确认第一笔 |
| 2027-02-28 | 3500 | 1300 | 700 | 0 | 600 | 第 13 月度节点 +100（2 月最后一天） |
| 2027-04-30 | 3300 | 1500 | 700 | 0 | 800 | 第 14、15 月度节点各 +100 |
| 2030-01-31 | 0 | 4800 | 700 | 0 | 4100 | 全部归属完成 |
