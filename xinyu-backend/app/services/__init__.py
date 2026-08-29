"""业务逻辑层 — 对应 Java 各模块 Service

依赖方向: api → services → repositories / ai, 单向不回环。
所有函数第一个参数为 AsyncSession, 事务由调用方(服务方法内部)显式 commit。
"""
