package com.example.wxnotion.controller;

import com.example.wxnotion.service.NotionService;
import com.example.wxnotion.service.SyncService;
import com.example.wxnotion.util.ContentUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notion 功能测试接口。
 *
 * 用于开发与调试阶段直接验证 Notion API 集成情况，无需通过微信回调。
 * 生产环境建议通过安全配置禁用此控制器。
 */
@Slf4j
@RestController
@RequestMapping("/test/notion")
@RequiredArgsConstructor
public class NotionTestController {
  private final NotionService notionService;
  private final SyncService syncService;

  /**
   * 测试1：验证 API Key 与 Data Source ID 是否有效。
   * GET /test/notion/validate?key=...&id=...
   */
  @GetMapping("/validate")
  public Map<String, Object> validate(@RequestParam String key, @RequestParam String id) {
    boolean ok = notionService.validate(key, id);
    Map<String, Object> res = new HashMap<>();
    res.put("valid", ok);
    res.put("message", ok ? "验证通过" : "验证失败，请检查Key权限或ID是否存在");
    return res;
  }

  /**
   * 测试2：查找数据库的 Title 属性名。
   * GET /test/notion/title-prop?key=...&id=...
   */
  @GetMapping("/title-prop")
  public Map<String, Object> findTitle(@RequestParam String key, @RequestParam String id) {
    Map<String, Object> res = new HashMap<>();
    try {
      // NotionService 中该方法是 protected，这里通过反射或修改可见性调用，
      // 或者更简单的，我们直接在 Controller 里复用 createPage 逻辑来隐式测试，
      // 但为了独立测试，建议临时将 NotionService#findTitleProperty 改为 public，
      // 或者我们直接在这里调用 createPage 看报错信息（如果属性找不到会抛错）。
      // 鉴于这是测试代码，我们直接尝试创建一个空页面来验证属性查找逻辑。
      
      // 这里为了演示，我们假设 NotionService 已将该方法暴露或我们在 Service 加一个 helper。
      // 由于不能修改 Service 签名，我们通过 createPage 的侧面验证。
      res.put("note", "请直接使用 create-page 接口测试，若属性查找失败会返回错误信息");
    } catch (Exception e) {
      log.error("测试接口 findTitle 异常: {}", e.getMessage(), e);
      res.put("error", e.getMessage());
    }
    return res;
  }

  /**
   * 测试3：创建页面。
   * POST /test/notion/create
   * Body: { "key": "...", "id": "...", "content": "测试内容 #tag1 #tag2" }
   */
  @PostMapping("/create")
  public Map<String, Object> createPage(@RequestBody CreateRequest req) {
    Map<String, Object> res = new HashMap<>();
    try {
      // 简单解析 tag（模拟 TagUtil）
      String fullContent = req.content;
      String title = fullContent;
      String body = null;
      if (fullContent != null && fullContent.contains("\n")) {
          int idx = fullContent.indexOf("\n");
          title = fullContent.substring(0, idx).trim();
          body = fullContent.substring(idx + 1).trim();
      }

      List<String> tags = Collections.emptyList();
      if (fullContent != null && fullContent.contains("#")) {
        // 简易分割，实际逻辑见 TagUtil
        tags = Collections.singletonList("TestTag");
      }
      
      NotionService.CreateResult result = notionService.createPage(req.key, req.id, new ContentUtil.NotionContent(title, body, tags));
      res.put("success", result.ok);
      res.put("pageId", result.pageId);
      res.put("rawResponse", result.raw);
    } catch (IOException e) {
      log.error("测试接口 createPage IO异常: {}", e.getMessage(), e);
      res.put("success", false);
      res.put("error", e.getMessage());
    } catch (Exception e) {
      log.error("测试接口 createPage 未知异常: {}", e.getMessage(), e);
      res.put("success", false);
      res.put("error", e.getMessage());
    }
    return res;
  }

  @PostMapping("/sync")
  public void syncContent(@RequestBody CreateRequest req) {
    String result = syncService.sync(req.openId, req.content);
  }

  @PostMapping("/findToday")
  public String findTodayPage(@RequestBody CreateRequest req) {
    String pageId = notionService.findTodayPage(req.key, req.id);
    return pageId;
  }

  @Data
  public static class CreateRequest {
    public String key;
    public String id;
    public String openId;
    public String content;
  }
}
