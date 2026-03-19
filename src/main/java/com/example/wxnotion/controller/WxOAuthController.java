package com.example.wxnotion.controller;

import com.example.wxnotion.config.WxProperties;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.service.WxOAuthSessionService;
import com.example.wxnotion.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/wx/oauth")
@RequiredArgsConstructor
public class WxOAuthController {
  private final WxMpService wxMpService;
  private final WxProperties wxProperties;
  private final UserConfigRepository userConfigRepository;
  private final WxOAuthSessionService sessionService;
  private final JwtUtil jwtUtil;

  /**
   * 发起微信网页授权，获取 openId。
   * 适用于在微信内打开的 H5 页面。
   */
  @GetMapping("/start")
  public void start(@RequestParam(value = "returnUrl", required = false) String returnUrl,
                    @RequestParam(value = "state", required = false) String state,
                    HttpServletRequest request,
                    HttpServletResponse response) throws IOException {
    String safeReturnUrl = normalizeReturnUrl(returnUrl, request);
    String callbackBase = StringUtils.defaultIfBlank(wxProperties.getOauthCallbackBase(), buildBaseUrl(request));
    String callbackUrl = callbackBase + "/wx/oauth/callback?returnUrl=" + urlEncode(safeReturnUrl);
    String scope = StringUtils.defaultIfBlank(wxProperties.getOauthScope(), "snsapi_base");

    String actualState = StringUtils.defaultIfBlank(state, "STATE");
    String authUrl = wxMpService.getOAuth2Service().buildAuthorizationUrl(callbackUrl, scope, actualState);
    response.sendRedirect(authUrl);
  }

  /**
   * OAuth 回调：用 code 换 openId，然后重定向回前端页面。
   */
  @GetMapping("/callback")
  public void callback(@RequestParam(value = "code", required = false) String code,
                       @RequestParam(value = "state", required = false) String state,
                       @RequestParam(value = "returnUrl", required = false) String returnUrl,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
    String safeReturnUrl = normalizeReturnUrl(returnUrl, request);

    if (StringUtils.isBlank(code)) {
      response.sendRedirect(appendParam(safeReturnUrl, "error", "missing_code"));
      return;
    }

    try {
      WxOAuth2AccessToken token = wxMpService.getOAuth2Service().getAccessToken(code);
      String openId = token.getOpenId();
      saveUserProfileIfPossible(token, openId);
      if (StringUtils.isNotBlank(state)) {
        sessionService.markAuthed(state, openId);
      }
      String jwt = jwtUtil.issue(openId);
      response.sendRedirect(appendParam(safeReturnUrl, "token", jwt));
    } catch (Exception e) {
      log.error("OAuth 回调处理失败", e);
      response.sendRedirect(appendParam(safeReturnUrl, "error", "oauth_failed"));
    }
  }

  /**
   * PC 端扫码登录：返回二维码 URL 与 state。
   */
  @GetMapping("/qr/start")
  public Map<String, Object> qrStart(HttpServletRequest request) {
    String state = sessionService.createState();
    String callbackBase = StringUtils.defaultIfBlank(wxProperties.getOauthCallbackBase(), buildBaseUrl(request));
    String returnUrl = callbackBase + "/wx/oauth/scan-done";
    String callbackUrl = callbackBase + "/wx/oauth/callback?returnUrl=" + urlEncode(returnUrl);
    String scope = StringUtils.defaultIfBlank(wxProperties.getOauthScope(), "snsapi_userinfo");
    String authUrl = wxMpService.getOAuth2Service().buildAuthorizationUrl(callbackUrl, scope, state);

    Map<String, Object> res = new HashMap<>();
    res.put("state", state);
    res.put("qrUrl", authUrl);
    res.put("expiresIn", sessionService.ttlSeconds());
    return res;
  }

  /**
   * PC 端扫码登录状态查询。
   */
  @GetMapping("/qr/status")
  public Map<String, Object> qrStatus(@RequestParam String state) {
    WxOAuthSessionService.SessionStatus status = sessionService.getStatus(state);
    if ("SUCCESS".equals(status.status)) {
      sessionService.consume(state);
    }
    Map<String, Object> res = new HashMap<>();
    res.put("status", status.status);
    res.put("openId", status.openId);
    if ("SUCCESS".equals(status.status) && StringUtils.isNotBlank(status.openId)) {
      res.put("token", jwtUtil.issue(status.openId));
    }
    return res;
  }

  /**
   * 扫码完成提示页面（微信端）。
   */
  @GetMapping(value = "/scan-done", produces = MediaType.TEXT_HTML_VALUE)
  public String scanDone() {
    return "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'/>"
        + "<title>授权成功</title>"
        + "<style>body{font-family:Arial,Helvetica,sans-serif;background:#f7f7f7;padding:32px;color:#1f2933}"
        + ".card{background:#fff;border-radius:16px;padding:24px;box-shadow:0 10px 30px rgba(0,0,0,.08)}"
        + "h1{font-size:20px;margin:0 0 8px}p{margin:0;color:#54616c}</style></head><body>"
        + "<div class='card'><h1>授权成功</h1><p>已完成登录，请返回电脑继续。</p></div></body></html>";
  }

  private String normalizeReturnUrl(String returnUrl, HttpServletRequest request) {
    String fallback = wxProperties.getOauthReturnUrl();
    if (StringUtils.isBlank(returnUrl)) {
      return StringUtils.defaultIfBlank(fallback, "/");
    }
    if (StringUtils.isBlank(fallback)) {
      return returnUrl;
    }
    if (returnUrl.startsWith(fallback)) {
      return returnUrl;
    }
    String callbackBase = StringUtils.defaultIfBlank(wxProperties.getOauthCallbackBase(), buildBaseUrl(request));
    return returnUrl.startsWith(callbackBase) ? returnUrl : fallback;
  }

  private String buildBaseUrl(HttpServletRequest request) {
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    boolean standard = ("http".equalsIgnoreCase(scheme) && port == 80)
        || ("https".equalsIgnoreCase(scheme) && port == 443);
    return scheme + "://" + host + (standard ? "" : ":" + port);
  }

  private String appendParam(String url, String name, String value) {
    String sep = url.contains("?") ? "&" : "?";
    return url + sep + name + "=" + urlEncode(value);
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
  }

  private void saveUserProfileIfPossible(WxOAuth2AccessToken token, String openId) {
    if (StringUtils.isBlank(openId)) return;
    String scope = StringUtils.defaultString(token.getScope());
    if (!scope.contains("snsapi_userinfo")) return;
    try {
      WxOAuth2UserInfo info = wxMpService.getOAuth2Service().getUserInfo(token, "zh_CN");
      UserConfig cfg = userConfigRepository.selectByOpenId(openId);
      if (cfg == null) {
        return;
      }
      userConfigRepository.update(null, new UpdateWrapper<UserConfig>()
          .eq("open_id", openId)
          .set("nickname", info.getNickname())
          .set("avatar_url", info.getHeadImgUrl()));
    } catch (Exception e) {
      log.warn("获取或保存微信用户信息失败: {}", e.getMessage());
    }
  }
}
