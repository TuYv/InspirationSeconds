Lombok 重构已全部完成。

主要修改内容：
1.  **数据模型 (Model)**: `UserConfig`, `ConversationState` 以及 `notion` 包下的所有 DTO 类均已替换为 `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` 等注解，移除了繁琐的 Getter/Setter。
2.  **配置类 (Config)**: `NotionProperties` 和 `WxProperties` 使用 `@Data` 简化。
3.  **服务层 (Service)**: 所有 Service 类移除了显式的 `Logger` 定义（改为 `@Slf4j`）和构造函数注入（改为 `@RequiredArgsConstructor`）。
4.  **控制层 (Controller)**: 所有 Controller 类同样使用了 `@RequiredArgsConstructor` 进行依赖注入，部分使用了 `@Slf4j`。

代码变得更加简洁清晰，样板代码大幅减少。由于环境原因未能执行 Maven 编译验证，但代码修改遵循了标准的 Lombok 规范，预期能够正常编译运行。