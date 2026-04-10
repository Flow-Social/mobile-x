# KMP Context

## Проблема

Проект `Flow` исторически был Android-first. Сейчас идет перенос продукта на `shared`/`web (wasmJs)` без создания второго отдельного клиента. Главная цель: не делать "похожую веб-версию", а переносить в `shared` продуктовую логику, UI contract и поведение Android-клиента, оставляя платформам только transport/host glue.

Текущее состояние в среднем: `8.3/10`.

## Что это за проект

`Flow` — модульное Kotlin Multiplatform/Compose приложение.

### Структура

- `app`
  - Android host
  - навигация
  - DI wiring
  - platform-specific screen/overlay host
- `core`
  - `api` — network API и realtime transport для Android/backend contract
  - `auth` — auth-логика
  - `data` — repositories/store/session ownership
  - `database` — локальные данные
  - `domain` — доменные модели/логика
  - `uikit` — UI компоненты, тема, shared visual building blocks
  - `mock` — моки/заглушки
- `feature`
  - `feed`
  - `chats`
  - `explore`
  - `login`
  - `profile`
  - `chatssearch`
  - `post`
  - `comments`
  - `shared` — текущий owner KMP/web migration path

### Базовые правила архитектуры

- Сначала переиспользование из `core:uikit`, потом новые компоненты.
- Бизнес-логика и продуктовые модели не тащить в `uikit`.
- В `shared` переносить Android-derived behavior, а не писать отдельный web-only сценарий.
- Если Android и web расходятся по product behavior, канон — Android.
- Platform-specific код должен отвечать только за:
  - host/container
  - transport/auth adapter
  - filesystem/browser/platform APIs

## Что уже сделано

### 1. Shared/Web chat path сильно выровнен с Android

Сделано:
- `Saved Messages` приведен ближе к Android contract:
  - title/icon/header path
  - ownership сообщений
  - убран сломанный special-case с пустым `peerUserId`
- починен `context menu` path в shared chat
- возвращен Android-like scroll ownership:
  - `scrollToBottomRequestToken`
  - viewport callbacks
  - owner-controlled auto-scroll/follow-bottom
- сделан Android-like optimistic send:
  - local optimistic bubble
  - temporary ids
  - `clientMessageId`
  - `SENDING / FAILED / SENT`
  - retry без дублей
- read statuses при открытии чата теперь пересчитываются от `peerLastReadMessageId`, как в Android

Оценка блока: `8.5/10`.

### 2. Shared chat runtime вычищен

Сделано:
- убран legacy `getChats()` path
- введен единый `SharedChatSessionCache`
- часть orchestration вынесена из UI state holder
- cache ownership перестал быть размазан по нескольким местам

Оценка блока: `9/10`.

### 3. Web realtime/websocket path переработан

Сделано:
- realtime разделен на:
  - thin transport
  - shared session/runtime manager
- presence path переведен на session-scoped модель
- улучшена diagnostics для web socket flow
- web auth path переведен на `ws_ticket`

Оценка блока: `8/10`.

### 4. Backend подготовлен под web runtime

Сделано в backend:
- добавлен `ws_ticket`
- middleware умеет `ticket`
- настроен CORS для deployed web origin
- preflight/login path после фиксов перестал резаться CORS

Оценка блока: `8.5/10`.

### 5. Production wasm build и deploy path уже рабочие

Сделано:
- починен `wasmJsBrowserDistribution`
- production bundle теперь собирается нормально
- bundle пригоден для deploy на Cloudflare Pages
- подготовлен deployable output directory

Оценка блока: `8.5/10`.

### 6. Mobile web host частично починен

Сделано:
- убран системный баг с `100vh` в wasm host
- высота root на mobile web переведена на dynamic viewport через `visualViewport`

Оценка блока: `8.5/10`.

## Что важно понимать про текущий подход

Мы **не** хотим:
- писать второй web-клиент
- плодить web-only screen logic
- дублировать Android поведение "примерно похоже"
- чинить продуктовые расхождения css-костылями, если сломан owner contract

Мы хотим:
- один shared source of truth
- Android-derived behavior
- platform-specific только в transport/host

## Что еще не добито

### 1. Не все экраны доведены до Android parity

Уже сильно продвинуты:
- `Chats`
- `Direct Chat`
- `Saved Messages`
- `Replies` частично
- часть `Profile`/overlay path

Еще требуют анализа и/или доводки:
- `Feed`
- `Explore`
- `Comments`
- `Post`
- `ChatsSearch`
- `Login`
- `Profile` целиком, включая hero/buttons/content/edit/create-post overlays
- fullscreen/media viewer path
- mobile web host behavior на реальных девайсах

### 2. Нужен системный аудит миграции экранов

Сейчас нет одного нормального audit snapshot:
- какие экраны уже готовы к migration/use in shared
- какие частично готовы
- какие еще завязаны на Android-only host/runtime

Это следующая важная задача.

## Что нужно проанализировать дальше

Ниже задача для следующей ИИ/агента.

### Задача

Проанализируй текущий статус migration-to-shared по всем основным экранам и flows.

### Цель

Составить честную карту:
- какие экраны уже готовы к shared/web migration
- какие готовы частично
- какие не готовы
- почему именно не готовы
- какой минимальный путь доведения до Android parity

### Что считать экраном/flow

Минимум проверить:
- `Login`
- `Feed`
- `Chats List`
- `Direct Chat`
- `Saved Messages`
- `Replies`
- `ChatsSearch`
- `Profile`
- `Edit Profile`
- `Create Post`
- `Edit Post`
- `Post View`
- `Comments`
- `Explore`
- fullscreen/media viewer
- main shell / overlays / bottom navigation behavior

### Что смотреть при анализе

Для каждого экрана/flow ответить:

1. Есть ли уже shared owner path?
2. Кто owner логики сейчас:
- Android-only
- shared
- гибрид/обертка
3. Есть ли отдельный web-only fork?
4. Насколько UI и behavior соответствуют Android канону?
5. Есть ли временный мусор/adapter layer/костыли?
6. Что мешает честно считать экран migrated?
7. Какой нужен следующий шаг:
- reuse existing shared path
- вынести Android logic в shared
- убрать wrapper/fork
- оставить platform-specific host glue

### Формат ожидаемого результата

Нужен короткий, жесткий, инженерный аудит:

Для каждого экрана:
- статус:
  - `готов`
  - `частично готов`
  - `не готов`
- owner path
- главные расхождения
- что уже ок
- что сломано/грязно
- оценка `1-10`
- конкретный следующий шаг

Потом общий итог:
- что уже можно считать migrated
- что еще нет
- где самый большой технический риск
- где самый полезный следующий кусок работы

## Текущая общая оценка прогресса

- архитектура shared/web: `8.5/10`
- chat runtime/parity: `8.5/10`
- realtime/websocket path: `8/10`
- deploy readiness: `8.5/10`
- mobile web host/adaptation: `8/10`
- общий прогресс миграции на web: `8.3/10`

## Короткий вывод для следующей ИИ

Контекст такой:
- база уже не разваливается
- критичный chat/realtime/deploy фундамент уже собран
- главная следующая работа — не переписывать все с нуля, а сделать честный migration audit экранов и добить оставшиеся Android/Web drift'ы без новых fork'ов
