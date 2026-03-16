# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Does

WeChat Service Account (服务号) integration with Notion. Users configure their Notion API key and database ID via WeChat chat messages. Subsequent messages are synced to Notion as pages. A daily AI summary (08:00 CST) fetches yesterday's Notion content and pushes it back to users via WeChat customer service API.

## Commands

### Backend (Maven)
```bash
mvn spring-boot:run          # Run locally (H2 in-memory DB)
mvn clean package            # Build fat JAR
mvn test                     # Run all tests
mvn test -Dtest=ClassName    # Run single test class
```

### Frontend (Vue 3 + Vite)
```bash
cd frontend
npm install
npm run dev      # Dev server on :5173, proxies /api and /wx to localhost:8080
npm run build    # Outputs to frontend/dist/
```

### Docker
```bash
docker compose up -d         # Build and start (reads .env)
docker compose logs -f       # Follow logs
docker compose exec wx-notion env | grep -E "WX_|NOTION_|AI_"  # Check injected env vars
```

## Architecture

### Request Flow

```
WeChat POST /wx/portal
  └→ WxPortalController
       └→ HandlerWxPortalService (@Async)
            ├→ ConfigFlowService        # if user is in config state
            │    └→ NotionService       # validate API key / database ID
            └→ SyncService             # normal message → Notion page
                 └→ WechatService      # push reply via customer service API
```

### Configuration State Machine
`ConfigFlowService` tracks per-user state in `conversation_state` table:
- `WAITING_API_KEY` → `WAITING_DATABASE_ID` → complete
- Notion API key is AES/CBC/PKCS5 encrypted before storing in `user_config`

### AI Summary
`DailySummaryService` runs at `0 0 8 * * ?` (Asia/Shanghai). Fetches yesterday's Notion pages via `NotionService.retrieveBlockChildren()`, sends to `AiService` (SiliconFlow OpenAI-compatible API), pushes result to all active users. Weekly summary runs on Mondays.

### Guest Mode
Users without their own Notion token share an admin-managed workspace. Identified by `is_guest=true` in `user_config`. Guest root page ID configured via `NOTION_GUEST_ROOT_PAGE_ID`.

### Notion API Layer
`NotionApiFacade` wraps the Notion SDK with unified error handling. `NotionService` uses it for all API calls. Title property name is detected dynamically (Notion databases can have different title column names).

### OAuth Web Flow
`WxOAuthController` handles WeChat web authorization (snsapi_userinfo). Frontend at `wx.soloship.top`, backend API at `wx.api.soloship.top`. QR code polling for PC browsers via `/wx/oauth/qr/*` endpoints.

## Key Files

| File | Purpose |
|------|---------|
| `application.yml` | Base config, H2 DB, default env var values |
| `application-prod.yml` | MySQL config, activated by `SPRING_PROFILES_ACTIVE=prod` |
| `src/main/resources/schema.sql` | Tables: `user_config`, `conversation_state` |
| `src/main/resources/prompts/` | AI prompt templates loaded by `PromptManager` |
| `frontend/vite.config.ts` | Dev proxy: `/api`, `/wx` → `localhost:8080` |

## Database Tables

- **`user_config`**: `open_id`, encrypted `api_key`, `database_id`, `is_guest`, `status`, `nickname`, `avatar_url`
- **`conversation_state`**: `open_id`, `state` (enum), `temp_api_key`

## Environment Variables

All required vars are in `.env` (Docker Compose reads this automatically). Key ones:

- `WX_APP_SECRET` — WeChat AppSecret, used to init WxJava `WxMpService`
- `WX_ENCODING_AES_KEY` — 43-char key for WeChat message encryption (安全模式)
- `AES_KEY` — 32-char hex, internal AES key for storing Notion tokens
- `NOTION_ADMIN_TOKEN` — Notion integration token for guest workspace
- `AI_API_KEY` — SiliconFlow API key (OpenAI-compatible)

## WeChat Platform Requirements

Two separate portals must be configured:
1. **微信开发者平台** (open.weixin.qq.com) — JS接口安全域名
2. **微信公众平台** (mp.weixin.qq.com) → 公众号设置 → 功能设置 — **网页授权域名** (required for OAuth)

Server config (消息推送): URL must be `https://<domain>/wx/portal`, mode 安全模式, format XML.
