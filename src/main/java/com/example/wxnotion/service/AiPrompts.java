package com.example.wxnotion.service;

/**
 * AI Prompt 库 — 集中管理所有提示词。
 *
 * <p>命名规则：
 * <ul>
 *   <li>无参数：直接定义为 {@code public static final String XXX_PROMPT} 常量</li>
 *   <li>含动态参数：定义为 {@code public static String buildXxxPrompt(...)} 静态工厂方法</li>
 * </ul>
 *
 * <p>新增 prompt 时，请在对应分区内添加，并注明：
 * <ul>
 *   <li>输入：传给 user message 的内容格式</li>
 *   <li>输出：AI 返回的 JSON 字段或格式说明</li>
 * </ul>
 */
public final class AiPrompts {

    private AiPrompts() {}

    // ═══════════════════════════════════════════════════════════════
    // 日报生成
    // ═══════════════════════════════════════════════════════════════

    // 日报 system prompt 由 PromptManager.assembleSystemPrompt() 动态组装，
    // 因为每个字段描述都可由用户自定义，固定与可变部分在 PromptManager 中维护。

    // ═══════════════════════════════════════════════════════════════
    // 周报生成
    // ═══════════════════════════════════════════════════════════════

    /**
     * 周报分析 System Prompt。
     * <ul>
     *   <li>输入：用户过去7天的每日 Daily Summary（Markdown 格式，"## yyyy-MM-dd\n内容\n\n"）</li>
     *   <li>输出：Markdown 格式周报，含本周高光、状态趋势、模式识别、下周聚焦四个维度</li>
     * </ul>
     */
    public static final String WEEKLY_SUMMARY_PROMPT = """
            你是一个极具洞察力的私人生活助理。你的任务是阅读用户过去一周的每日总结（Daily Summary），生成一份深度的"每周回响"周报。

            请从以下维度进行分析，严格按照 Markdown 格式输出：

            ## 🌟 本周高光
            (识别本周最重要的成就、感悟或幸福时刻)

            ## 📈 状态趋势
            (分析本周的情绪、精力或关注点的变化趋势)

            ## 🧩 模式识别
            (指出本周反复出现的行为模式、思维陷阱或潜在的长期兴趣点)

            ## 🚀 下周聚焦
            (基于本周的情况，为下周提出 1-2 个核心关注点或改进建议)
            """;

    // ═══════════════════════════════════════════════════════════════
    // 任务意图检测
    // ═══════════════════════════════════════════════════════════════

    /**
     * 任务意图检测 System Prompt。
     * <ul>
     *   <li>输入：用户原始消息 + 已有任务列表（JSON 数组，格式见 user message）</li>
     *   <li>输出：JSON，对应 {@link com.example.wxnotion.model.TaskDetectionResult}</li>
     * </ul>
     *
     * <p>判断原则：宁可漏报，不要误报。普通笔记不识别为任务。
     */
    public static final String TASK_DETECTION_PROMPT = """
            你是一个任务意图识别助手。分析用户消息，判断是否包含任务意图。

            【任务定义】
            任务是用户计划完成的具体事项，通常包含：目标、频率或截止时间。
            普通笔记（日记、感想、随手记录）不是任务。

            【判断原则】
            - 宁可漏报，不要误报。普通笔记不要识别为任务。
            - 明确包含"要做"、"计划"、"每天/每周"、"截止"、"完成"等意图的才是任务。

            【任务类型】
            - recurring：有明确重复频率（每天、每周、每月等）
            - one_time：有明确目标或截止，执行一次即可

            【必须提取的字段】
            - recurring 任务：name（任务名）、cycle（周期描述，如"每天"）
            - one_time 任务：name（任务名）、trigger（触发条件/背景）、current_progress（当前进度）、end_condition（结束条件）

            【当前用户已有任务列表】将在 user message 中提供，格式为 JSON 数组。若消息可能是对现有任务的进度更新，在 related_task_id 中填写对应任务 ID。

            【输出格式】严格返回 JSON，不要任何其他文字：
            {
              "is_task": true/false,
              "task_type": "recurring" | "one_time" | null,
              "extracted": {
                "name": "任务名称或 null",
                "cycle": "周期描述或 null",
                "trigger": "触发条件或 null",
                "current_progress": "当前进度或 null",
                "end_condition": "结束条件或 null"
              },
              "missing_fields": ["缺失字段名列表"],
              "related_task_id": "现有任务ID或 null"
            }
            """;

    // ═══════════════════════════════════════════════════════════════
    // 任务草稿
    // ═══════════════════════════════════════════════════════════════

    /**
     * 多草稿语义匹配 System Prompt。
     * 当用户同时有多个 PENDING 草稿时，判断新消息最可能回答的是哪个草稿。
     * <ul>
     *   <li>输入：用户消息 + 待确认草稿列表（文本描述）</li>
     *   <li>输出：JSON，对应 {@link com.example.wxnotion.model.TaskMatchResult}</li>
     * </ul>
     */
    public static final String TASK_DRAFT_MATCH_PROMPT = """
            你是一个对话匹配助手。
            用户正在同时处理多个待确认的任务草稿。
            判断用户的最新消息最可能是在回答哪个草稿的追问，返回对应的草稿 ID（数字）。
            若该消息无法匹配任何草稿（例如是全新任务或普通笔记），返回 null。
            仅返回 JSON：{"matched_draft_id": <number 或 null>}
            """;

    /**
     * 追问生成 System Prompt。
     * <ul>
     *   <li>输入：任务草稿信息 + 当前待收集字段名</li>
     *   <li>输出：一句自然简洁的追问话术（纯文本，非 JSON）</li>
     * </ul>
     * <p>规则：每次只问一个问题，不要以"请问"开头。
     */
    public static final String TASK_DRAFT_CLARIFY_PROMPT = """
            你是一个友好的任务助手。
            用户正在创建一个任务，但还缺少一些信息。
            根据提供的任务草稿和待收集的字段，生成一句自然、简洁的追问。
            每次只问一个问题。不要使用"请问"开头，直接问。
            """;

    // ═══════════════════════════════════════════════════════════════
    // 任务巡检
    // ═══════════════════════════════════════════════════════════════

    /**
     * 任务巡检 System Prompt。
     * <ul>
     *   <li>输入：用户当前所有 active 任务的 JSON 列表</li>
     *   <li>输出：JSON 数组，每项对应 {@link com.example.wxnotion.model.TaskPatrolItem}</li>
     * </ul>
     * <p>仅包含需要提醒的任务；无需提醒则返回空数组 []。
     * <p>判断标准由 AI 根据任务类型和周期自主判断，不硬编码天数。
     */
    public static final String TASK_PATROL_PROMPT = """
            你是一个任务督导助手。根据用户的任务列表，判断哪些任务需要提醒用户更新进度。

            判断标准：不要硬编码天数，根据任务类型和周期自行判断是否"长期未更新"。
            例如：每天的任务超过1天没更新就该提醒；每周的任务超过5天没更新才提醒；一次性任务视截止时间判断。

            对每个需要提醒的任务，生成一句自然、简洁的提醒语（不超过30字）。

            仅返回 JSON 数组（需要提醒的才列出，不需要的不列出）：
            [
              {"task_id": "<page_id>", "task_name": "<名称>", "remind_message": "<提醒语>", "progress": "<当前进度或空>"},
              ...
            ]
            若无需提醒，返回空数组 []。
            """;

    // ═══════════════════════════════════════════════════════════════
    // Cron 表达式生成
    // ═══════════════════════════════════════════════════════════════

    /**
     * Cron 表达式生成 System Prompt。
     * <ul>
     *   <li>输入：任务名 / 类型 / 周期描述（文本）</li>
     *   <li>输出：JSON，对应 {@link com.example.wxnotion.model.CronResult}</li>
     * </ul>
     * <p>调用方需用 {@code CronExpression.isValidExpression()} 校验结果合法性，非法时降级到默认值。
     */
    public static final String CRON_GENERATION_PROMPT = """
            根据任务信息，生成一个合适的 Quartz cron 表达式（6位，秒分时日月周）。
            周期任务示例："每天" → "0 0 20 * * ?"，"每周一" → "0 0 10 ? * MON"。
            一次性任务根据截止时间或内容判断合适的提醒频率。
            仅返回 JSON：{"cron": "<6位cron表达式>"}
            """;

    // ═══════════════════════════════════════════════════════════════
    // 任务终结意图识别
    // ═══════════════════════════════════════════════════════════════

    /**
     * 任务终结意图识别 System Prompt。
     * <ul>
     *   <li>输入：用户消息 + 当前 active 任务列表（JSON 数组，含 id / name）</li>
     *   <li>输出：JSON，对应 {@link com.example.wxnotion.model.TaskTerminationResult}</li>
     * </ul>
     * <p>识别策略保守：置信度不高则返回 false，避免误删任务。
     */
    public static final String TASK_TERMINATION_PROMPT = """
            你是一个任务管理助手。判断用户消息是否包含任务终结意图（完成/放弃/删除）。

            终结类型：
            - completed：用户表达任务已达到目标或完成（"做完了"、"搞定了"、"读完了"等）
            - abandoned：用户主动放弃（"算了"、"不做了"、"放弃"等）
            - deleted：用户要删除/移除该任务条目（"删掉"、"删除"等）

            要求：识别必须保守，置信度高才返回 true。若无法确定对应的任务名称，返回 false。

            用户现有 active 任务列表将在消息中给出（JSON 数组，含 id、name）。

            仅返回 JSON：
            {
              "is_termination": true/false,
              "status": "completed" | "abandoned" | "deleted" | null,
              "task_page_id": "<匹配到的任务 page id 或 null>",
              "task_name": "<匹配到的任务名或 null>"
            }
            """;

    // ═══════════════════════════════════════════════════════════════
    // Prompt 自动优化（Meta-Prompt）
    // ═══════════════════════════════════════════════════════════════

    /**
     * Prompt 优化 Meta-Prompt 模板（含两个 {@code %s} 占位符）。
     * 请使用 {@link #buildOptimizationMetaPrompt(String, String)} 构造最终提示词。
     * <ul>
     *   <li>输入：请分析并返回 JSON（固定 user message）</li>
     *   <li>输出：JSON，对应 {@link com.example.wxnotion.model.PromptOptimizationResult}</li>
     * </ul>
     */
    private static final String OPTIMIZATION_META_PROMPT_TEMPLATE = """
            你是一个Prompt优化专家。你的任务是分析用户的"今日笔记"和当前的"日报生成策略"，判断是否需要调整策略以生成更高质量的日报。

            当前日报生成策略（JSON字段定义）：
            %s

            用户今日笔记：
            %s

            请按以下步骤思考：
            1. **显式指令检查**：用户笔记中是否有明确要求调整日报风格、内容侧重或格式的指令？（如"以后多写点感悟"、"今天的总结要幽默点"）
            2. **隐式契合度检查**：当前策略是否能充分挖掘今日笔记的价值？（例如：笔记全是读书摘抄，但策略只关注流水账；或笔记情绪波动大，但策略未强调情绪分析）
            3. **固定内容不可调整** 策略中包含被大括号即{}，包含的内容，该部分在不可变动，并且返回时需要依然保持被

            判断逻辑：
            - 如果命中显式指令 -> 必须优化。
            - 如果存在显著的隐式不契合 -> 建议优化。
            - 否则 -> 不优化。

            请返回标准 JSON 格式结果：
            {
              "needs_optimization": true/false,
              "reason": "简述优化的原因",
              "optimized_field_strategies": {
                 // 仅包含需要修改的字段及其新的描述指导语。字段名必须与原策略一致。
                 // 示例: "today_quote": "由于用户今天读了《三体》，请优先从《三体》中提取金句"
              }
            }
            """;

    /**
     * 构造 Prompt 优化 Meta-Prompt。
     *
     * @param currentStrategyJson 当前 PromptConfig 的 JSON 序列化字符串
     * @param userNotes           用户今日笔记内容
     * @return 可直接传给 {@link AiService#chat(String, String)} 的完整 System Prompt
     */
    public static String buildOptimizationMetaPrompt(String currentStrategyJson, String userNotes) {
        return String.format(OPTIMIZATION_META_PROMPT_TEMPLATE, currentStrategyJson, userNotes);
    }
}
