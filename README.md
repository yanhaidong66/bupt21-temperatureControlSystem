# 非常失败的框架设计
在做设计时，只做了分层架构，在controller之后加了一个调度层。
如果是一次性开发完成的单体软件还好，但是，因为后期需求的变更，直接将复杂度搞爆炸了。
# 我总结了我犯下的错误
1. 将大量的业务逻辑分散，写了很多工具类，导致后期一处需求改动需要变动很多零散的地方。
2. 没有做详设，有了大体框架直接就动手了。
3. 参数校验做的很差劲，分散在各个层。
4. 前后端交互简陋，异常直接跑到前端。
5. 算法上只投入了很少精力，只想着快速收工，可行性一直没有思考。
6. 我应该找一个人专门搞前端，前端我也花了不少时间（因为我不会用框架）。
7. 简单地使用数据库增删改查的思维来思考业务，在领域层面没有过多地考虑，导致全是贫血的DO来隐式表达逻辑，当需求变动时，表设计需要大动，导致无以为继，不敢去改逻辑。Domain过度地依赖基础设施。
8. 表设计混乱，表对象和业务对象都是一个，也没有DTO，Eentity梭哈到底。
9. 一些细节应该快速评估路过的，反而花了很多时间。
10. UT写了但是，在变更中没有使用UT，没有真正地使用UT来验证变更。