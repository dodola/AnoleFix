# AnoleFix
Another hotfix  另一个热修复方案
大部分代码来自InstantRun。这个方案很久之前就和小伙伴讨论过，一直没实现，现完成了Alpha版，后期再优化。
![](screenshot/1.png)
![](screenshot/2.png)

## Feature
1. 可实现动态实时修复
2. 可实现热插拔(待实现，只实现了动态修复功能，后期加上动态撤销补丁功能)
3. 兼容ART和DalvikVM虚拟机，4.0-7.0，不会出现类似hotfix方案在art下虚拟机quick引用指针错乱造成的崩溃情况
4. 补丁包动态生成，使用方式和RocooFix 一致，但某些功能待完善

## Known issue
1. 补丁打包速度待优化
2. 有些未考虑到的补丁逻辑还待补充
3. 性能会略有消耗
4. **暂时只支持开启混淆状态下的补丁打包**