# Shared Chat Stage Smoke

## Problem

- Browser realtime migration is not closed until `/chats/realtime/ws` and `/presence/ws` are proven on a live stage session.
- UI acceptance is invalid if chat falls back to blocking placeholders after websocket failures.

## Expected State After Smoke

- shared direct chat, replies, and fullscreen stay on the Android-derived UI path
- browser websocket auth works through query-token
- reconnect, replay, resubscribe, read, typing, and presence are proven on stage
- no UI rollback into shared-only loading states
- score target after pass: `10/10`

## Preconditions

1. Stage build is running the current shared chat/runtime code.
2. Browser auth already succeeds and a valid session token exists.
3. Two accounts are available.
4. There is at least one existing direct conversation.
5. DevTools is open on `Network -> WS`.

## Direct Chat Smoke

1. Open the shared shell and navigate to a direct chat.
2. Confirm `/chats/realtime/ws` opens with:
   - `token`
   - `conversation_id`
   - optional `after_seq`
   - optional `replay_limit`
3. Confirm the first server event is `hello`.
4. Send a message from the browser session.
5. Confirm `message_created` or `message_updated` arrives and the message renders without full-screen loading fallback.
6. Send a message from the second account.
7. Confirm the browser receives the incoming message live.
8. Mark the message as read from the second account.
9. Confirm `read_up_to_updated` arrives live.
10. Type from the second account.
11. Confirm `typing` arrives live.

## Presence Smoke

1. Confirm `/presence/ws?token=...` opens in parallel.
2. Confirm the client sends a `subscribe` payload after open.
3. Confirm `presence_snapshot` arrives.
4. Change the peer online state from the second account.
5. Confirm `presence_changed` arrives and the header updates without reopening the chat.

## Reconnect And Replay

1. Force-close the chat websocket in DevTools.
2. Confirm reconnect starts with backoff and the screen does not lock into `Загрузка чата`.
3. Confirm the reconnect request carries `after_seq > 0` once events were already consumed.
4. While disconnected, send a message from the second account.
5. Confirm replay catches the missed event after reconnect.
6. If the server emits `resync_required`, confirm the client resubscribes and the conversation recovers.

## Fullscreen Checks

1. Open post fullscreen from the shared post screen.
2. Confirm fullscreen uses the unified viewer contract path, not the deleted `FullscreenPostImageViewer`.
3. While the chat socket is disconnected or reconnecting, confirm fullscreen does not get stuck and does not trigger shared-only blocking placeholders.

## Pass Criteria

- `/chats/realtime/ws` and `/presence/ws` both authenticate through query-token on stage.
- live send, receive, read, typing, and presence all work.
- reconnect, replay, and resubscribe all recover.
- direct chat, replies, and fullscreen never fall back to blocking shared-only loading UI.
- no manual UI rollback or web-only workaround is needed.

## Known Risks To Watch

- `feature/shared/src/wasmJsMain/kotlin/me/floow/shared/chats/uilogic/direct/WasmChatPresenceContract.kt`
  Presence subscribe previously dropped non-numeric `peerUserId`; verify raw string ids now work on stage.
- `feature/shared/src/wasmJsMain/kotlin/me/floow/shared/chats/uilogic/direct/WasmChatRealtimeContract.kt`
  Reconnect now refreshes the auth token inside the loop; verify long-lived reconnect does not reuse stale auth.
