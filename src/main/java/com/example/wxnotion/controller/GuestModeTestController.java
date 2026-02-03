package com.example.wxnotion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.service.MigrationService;
import com.example.wxnotion.service.NotionService;
import com.example.wxnotion.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 访客模式与数据迁移测试接口
 * (仅供开发测试使用)
 */
@RestController
@RequestMapping("/test/guest")
@RequiredArgsConstructor
@Slf4j
public class GuestModeTestController {

    private final SyncService syncService;
    private final MigrationService migrationService;
    private final UserConfigRepository userConfigRepository;
    private final NotionService notionService;
    private final NotionProperties notionProperties;

    /**
     * 1. 模拟初始化访客
     * 参数: openId (虚拟的微信OpenID)
     */
    @PostMapping("/init")
    public Map<String, Object> initGuest(@RequestParam String openId) {
        Map<String, Object> result = new HashMap<>();
        
        // 检查是否已存在
        UserConfig exist = userConfigRepository.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
        if (exist != null) {
            result.put("status", "exists");
            result.put("user", exist);
            return result;
        }

        // 调用 SyncService 的初始化逻辑 (通过发送一条空消息触发，或直接模拟初始化代码)
        // 由于 SyncService.initGuestUser 是 private 的，我们通过 sync 方法的副作用来触发
        String resp = syncService.sync(openId, "Hello Init");
        
        UserConfig newUser = userConfigRepository.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
        result.put("status", "created");
        result.put("syncResponse", resp);
        result.put("user", newUser);
        
        return result;
    }

    /**
     * 2. 模拟发送消息
     * 参数: openId, content
     */
    @PostMapping("/sync")
    public String syncMessage(@RequestParam String openId, @RequestParam String content) {
        return syncService.sync(openId, content);
    }


    /**
     * 6. (工具) 查询 Database (用于调试)
     */
    @GetMapping("/query_db")
    public NotionService.QueryResult queryDatabase(@RequestParam String databaseId) {
        String apiKey = notionProperties.getAdminToken();
        return notionService.queryDatabase(apiKey, databaseId, null);
    }
    @GetMapping("/queryDataByDate")
    public String queryDataByDate(@RequestParam String databaseId) {
        String apiKey = notionProperties.getAdminToken();
        LocalDate date = LocalDate.of(2026, 2, 1);
        return notionService.findPageByDate(apiKey, databaseId, date);
    }

    /**
     * 3. 触发转正迁移
     * 参数: openId, newToken (用户自己的Notion Token), newDbId (用户自己的DB ID)
     */
    @PostMapping("/migrate")
    public void triggerMigration(@RequestParam String srcToken,
                                               @RequestParam String srcDbId,
                                               @RequestParam String newToken, 
                                               @RequestParam String newDbId) {
        // 触发迁移
        migrationService.migrateSinglePage(srcToken, srcDbId, newToken, newDbId);

    }

    /**
     * 4. 查询用户状态
     */
    @GetMapping("/status")
    public UserConfig getUserStatus(@RequestParam String openId) {
        return userConfigRepository.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
    }
    
    /**
     * 5. (工具) 手动创建 Guest Database (绕过 UserConfig)
     */
    @PostMapping("/create_db_manual")
    public String createDbManual(@RequestParam String suffix) {
        String apiKey = notionProperties.getAdminToken();
        String rootId = notionProperties.getGuestRootPageId();
        return notionService.createDatabase(apiKey, rootId, "NoteBox_Manual_" + suffix);
    }
}
