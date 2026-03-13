# 版本解析调试

## 测试文件名
`agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar`

## 解析步骤

1. **移除扩展名**: `agent-0.1.0-SNAPSHOT-jar-with-dependencies`
2. **正则表达式**: `(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)(?:-[A-Za-z0-9]+)*`
3. **匹配结果**: 应该匹配到 `0.1.0-SNAPSHOT-jar-with-dependencies`
4. **提取版本**: 第一个捕获组应该是 `0.1.0`

## 可能的问题

1. 正则表达式可能需要调整
2. 文件名中的连字符和数字组合可能导致匹配问题

## 修复方案

更新正则表达式以更准确地匹配版本号：

```java
// 更严格的版本号匹配
Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)(?:-\\w+)*");
```

或者使用更宽松的匹配：

```java
// 查找第一个版本号模式
Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
```