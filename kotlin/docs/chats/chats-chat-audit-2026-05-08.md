# Аудит экранов Chats и Chat

Дата: 2026-05-08  
Область: список диалогов `Chats`, экран треда `Chat`, typing, pinned messages, video circles, анимации сообщений.

Код не менялся. Это продуктово-технический аудит по текущему состоянию репозитория.

## Короткий вывод

Главная проблема: `Chats` и `Chat` уже имеют часть низкоуровневой инфраструктуры для realtime, delivery, pinned, typing и video circles, но UI-флоу собраны не как единая модель активности диалога. Из-за этого:

- typing живет только внутри открытого `Chat`, а в `Chats` для него нет ни модели, ни подписки, ни UI;
- pinned messages есть в API/БД, но экран `Chat` показывает только те закрепы, которые случайно попали в текущее окно сообщений;
- video circle preview строится из локального/удаленного видео на лету, без стабильного thumbnail-контракта;
- закрепление диалога в `Chats` отсутствует на всех уровнях: API/domain/local/UI;
- отправка/получение обычных сообщений выглядит функционально, но без отдельной motion-системы.

## Текущий флоу Chats

1. `ChatsRoute` создает или получает `ChatsListStateHolder`.
2. `ChatsListStateHolder` подписывается только на `repository.observeChats()`.
3. Android-реализация `AndroidChatsListRepository` собирает список из:
   - `chatsRepository.observeConversations()`;
   - `notificationsRealtimeRepository.repliesState`;
   - `presenceRepository.presences`;
   - `chatsRepository.observeLocalReadStates()`.
4. `publishChats()` добавляет `Replies`, затем все conversations, сортируя `Saved Messages` выше остальных и дальше по `lastMessage.createdAt ?: updatedAt`.
5. `SharedChatsScreen` мапит `ChatListItemModel` в `ChatsListItemUiModel`.
6. `ChatsListContent` рисует `LazyColumn` с аватаром, title, preview, delivery icon, unread badge.

Как должно быть:

1. Экран списка должен получать не только conversation snapshot, но и live activity per conversation: typing, pinned dialog state, mute/archive, draft, upload/send state.
2. Сортировка должна учитывать pinned dialogs, затем реальную последнюю активность, не ломая `Saved Messages`.
3. Preview должен уметь временно заменяться на live status: `печатает…`, `записывает видео…`, `отправка…`.
4. Для действий над диалогом нужен long press / swipe menu / context action row.

## Текущий флоу Chat

1. `SharedDirectChatRoute` грузит `DirectChatStateHolder.load(initialRequest)`.
2. `DirectChatStateHolder` сначала пробует `loadCachedInitial()`, затем `loadInitial()`.
3. После загрузки запускаются:
   - `observeRealtime(conversationId)`;
   - `observeLocalMessages(conversationId)`;
   - `observePresence(header)`.
4. Сообщения приходят в `DirectChatScreenState.HasData.messages`.
5. `toSharedChatUiState()` конвертирует их в `ChatScreenUiState.HasData`.
6. `pinnedMessages` вычисляется как `messages.filter(isPinned)`.
7. `SharedDirectChatScreen` показывает `SharedPinnedMessagesBar`, только если `hasDataState.pinnedMessages` не пустой.
8. `HasDataState` рисует `LazyColumn` с rows, bubble, context menu, scroll-to-bottom и video circle slot.

Как должно быть:

1. Thread state должен иметь отдельные поля: `messages`, `pinnedMessages`, `typingState`, `mediaTransferState`, `conversationSettings`.
2. Pinned messages должны грузиться отдельным API/local flow (`getPinnedMessages` + `observePinnedMessages`), а не вытаскиваться из текущего окна timeline.
3. Realtime pin/unpin должен обновлять pinned bar, local cache и timeline flag без вставки старого сообщения в видимый список как обычного нового элемента.
4. Typing должен быть не только subtitle header, но и частью chat/live activity model.
5. Вставка новых сообщений должна проходить через motion layer, а не только через изменение списка и scroll.

## Найденные проблемы

### 1. В `Chats` не отображается typing

Где видно:

- `feature/shared/src/commonMain/kotlin/me/floow/shared/chats/model/ChatListItemModel.kt` не содержит typing/live activity поля.
- `feature/chats/src/main/java/me/floow/chats/uilogic/chats/AndroidChatsListRepository.kt` слушает conversations, replies, presence, read states, но не chat realtime typing.
- `feature/shared/src/commonMain/kotlin/me/floow/shared/chats/ui/SharedChatsScreen.kt` и `core/uikit/src/commonMain/kotlin/me/floow/uikit/chats/common/ChatsListContent.kt` умеют показать только static preview.
- `DirectChatStateHolder` умеет принимать `TypingUpdated`, но применяет это только к header subtitle открытого треда.

Текущее поведение:

- если собеседник печатает в открытом `Chat`, subtitle может стать `печатает…`;
- если пользователь находится в `Chats`, preview диалога остается последним сообщением.

Как надо:

- `Chats` должен подписываться на typing events по активным conversation ids;
- `ChatListItemModel` должен иметь `activityState`, например `Typing(displayName, expiresAt)`;
- UI preview должен временно показывать `печатает…` с приоритетом выше last message;
- TTL должен очищать typing без ручного refresh.

### 2. В `Chat` не подгружаются и некорректно отображаются закрепленные сообщения

Где видно:

- API/domain/local уже есть: `ChatsApi.getPinnedMessages`, `ChatsRepositoryImpl.getPinnedMessages`, `DirectChatsLocalStore.observePinnedMessages`.
- Но `AndroidChatThreadRepository.loadInitial()` грузит только `getMessages(...)` или anchored window, pinned flow не используется.
- `ChatThreadRepository` вообще не exposes pinned messages.
- `SharedChatUiAdapters.toSharedChatUiState()` делает `pinnedMessages = messages.filter(isPinned)`.
- `SharedDirectChatScreen` показывает pinned bar только из этого derived списка.

Текущее поведение:

- закрепленное сообщение видно в bar только если оно находится в текущем загруженном окне сообщений;
- старый закреп вне последних 50/80 сообщений не появится;
- порядок закрепов идет по timeline order, а не по `pinnedAt DESC`;
- realtime `MessagePinnedUpdated` в Android мапится в `MessageUpserted`, из-за чего старое закрепленное сообщение может быть вставлено в timeline, хотя пользователь сейчас находится внизу.

Как надо:

- `DirectChatScreenState.HasData` должен иметь отдельное `pinnedMessages`;
- загрузка треда должна параллельно грузить `getPinnedMessages(conversationId, limit)`;
- экран должен подписываться на `observePinnedMessages(conversationId)`;
- pinned bar должен сортироваться по `pinnedAt`, а не по дате сообщения;
- клик по pinned должен делать anchored jump, а если message не загружен, подгружать window around message.

### 3. Preview видео у видеокружков нестабилен

Где видно:

- `ChatRoute` выбирает `previewPath = thumbnailPath ?: localVideoPath ?: playablePath`.
- `ChatMessageContent.VideoCircle.thumbnailPath` существует, но в remote realtime mapping не заполняется.
- `MessageCirclePlayer.VideoCircleThumbnail` на каждую композицию делает `MediaMetadataRetriever.getFrameAtTime(...)`.
- `resolveLocalMediaSource()` допускает `http://` и `https://` для retriever.

Текущее поведение:

- для локального клипа preview чаще всего берется как первый кадр из локального видео;
- для remote-only видео thumbnail фактически извлекается из remote URL на устройстве;
- пока кадр не извлечен, пользователь видит shell-заглушку;
- при списке из нескольких видеокружков возможны повторные decode/network work.

Как надо:

- при записи надо генерировать thumbnail-файл и сохранять его вместе с local pending message;
- backend/API должен возвращать thumbnail/preview URL для video circle media;
- UI должен грузить preview через image pipeline/cache, а не через `MediaMetadataRetriever` в каждой bubble;
- remote video не должен дергаться retriever-ом ради первого кадра, особенно по `http://`.

### 4. Нет возможности закрепить диалог в `Chats`

Где видно:

- `ChatListItemModel` не имеет `isPinned`.
- `DirectChatConversation` не имеет pinned/sort rank поля.
- `ChatsApi` не содержит методов pin/unpin conversation.
- `DirectChatConversationEntity` не хранит pinned dialog state.
- `ChatsListContent` имеет только `clickable`, без long press/swipe/context menu.

Текущее поведение:

- можно открыть диалог;
- нельзя закрепить/открепить диалог;
- сортировка не может поднять важный чат наверх, кроме hardcoded `Saved Messages`.

Как надо:

- добавить conversation setting `isPinned`, `pinnedAt`, возможно `pinOrder`;
- API/local/domain должны поддерживать pin/unpin conversation;
- список должен сортировать `Saved Messages`, pinned dialogs, остальные;
- UI должен иметь long press menu или swipe action: `Закрепить`, `Открепить`, далее можно добавить mute/archive/delete.

### 5. Нет красивых анимаций отправки/получения сообщений

Где видно:

- `HasDataState` использует `LazyColumn.itemsIndexed(... key = item.key)`, но без `animateItem`/placement transition для обычных сообщений.
- Есть fade только для floating date separator и highlight color для selected/highlighted row.
- Для video circle есть отдельная entrance animation, но текстовые incoming/outgoing сообщения просто появляются.

Текущее поведение:

- отправленное сообщение вставляется в список и экран скроллится вниз;
- обычные bubbles не имеют появления, “flight” от input, пружины, fade/slide или status transition;
- получение сообщения выглядит как мгновенное изменение списка.

Как надо:

- outgoing: bubble должна вылетать из composer/send button зоны, мягко занимать место, затем менять статус `sending -> sent -> read`;
- incoming: легкий slide/fade от нижней границы с avatar/title choreography;
- list placement: `animateItem`/custom placement для перестановок и вставок;
- delivery icons должны анимироваться отдельно от bubble layout.

## Дополнительные проблемы

Пользователь запросил “еще 4”, но перечисление дает 2 + 2 + 1, поэтому ниже 5 дополнительных находок.

### Скорость/оптимизация 1. `Chats` refresh делает лишний network round-trip

Где видно:

- `AndroidChatsListRepository.refreshChats()` сначала вызывает `ensureSavedMessagesConversation()`, затем `getConversations(limit = 50, cursor = null)`.

Риск:

- каждый ручной/первичный refresh списка может делать POST/ensure saved conversation перед GET списка;
- на холодном старте это задерживает первый useful render.

Как надо:

- ensure saved conversation делать один раз при bootstrap или lazy при первом открытии `Saved`;
- список должен открываться из local cache сразу, а сеть догружать conversation page в фоне.

### Скорость/оптимизация 2. У списка `Chats` нет pagination flow

Где видно:

- `refreshChats()` всегда просит только `limit = 50, cursor = null`;
- `ChatsScreenState.HasData` не содержит `hasMore/nextCursor/isLoadingMore`.
- `ChatsListContent` не имеет trigger для load more.

Риск:

- активные пользователи видят только первую страницу;
- попытка поднять limit увеличит время загрузки и объем маппинга.

Как надо:

- добавить paged list state;
- хранить `nextCursor`;
- догружать по достижению конца списка;
- pinned dialogs и live activity должны накладываться поверх paging аккуратно.

### Перфоманс 1. Merge сообщений имеет квадратичную сложность на частых обновлениях

Где видно:

- `DirectChatStateHolder.mergeMessages()` делает fold;
- каждый `upsertMessage()` ищет existing через `firstOrNull`/`indexOfFirst` по текущему списку;
- `applyLocalMessagesPage()` вызывает `mergeMessages(current.messages + page.items)` на каждом local flow update.

Риск:

- при больших тредах, частом realtime, retry pending и media updates merge начинает стоить заметно дороже;
- чем больше сообщений загружено через `loadMore`, тем тяжелее каждое новое событие.

Как надо:

- держать индекс по `clientMessageId` и server `id`;
- merge делать за O(n), одним проходом;
- отдельно хранить window/timeline state и outbox overlay.

### Перфоманс/безопасность 2. Remote video preview извлекается через media retriever

Где видно:

- `MessageCirclePlayer.extractFirstFrame()` принимает `http://` и `https://`;
- `VideoCircleThumbnail` запускает это из `produceState` для каждой bubble.

Риск:

- лишняя сеть и декодирование при скролле;
- потенциальный privacy leak: устройство само ходит за media URL ради thumbnail;
- `http://` допускает cleartext media preview.

Как надо:

- использовать backend-generated thumbnail URL;
- грузить preview через общий image/cache слой;
- запретить `http://` для remote media или прогонять через безопасный media CDN/cache.

### Безопасность/приватность. Логи могут содержать чувствительные данные

Где видно:

- `logFailureResponse` пишет весь response body.
- `ChatsApiImpl` вызывает `logFailureResponse` на chat endpoints.
- `AuthApiImpl` пишет `Body text` для auth responses, где может быть session token.

Риск:

- chat text, media URLs, ids и auth token могут попасть в debug logs/crash reports;
- это особенно опасно для личных сообщений.

Как надо:

- логировать только status, endpoint name, request id/correlation id;
- body редактировать whitelist-ом безопасных полей;
- auth token и message text никогда не писать в logs.

### Интересная проблема. `typingUserNames` есть в UI-state, но фактически не используется

Где видно:

- `ChatScreenUiState.HasData.typingUserNames` существует.
- `DirectChatStateHolder` хранит `latestTypingState`, но выводит его через header subtitle.
- `toSharedChatUiState()` не заполняет `typingUserNames`.

Почему интересно:

- в UI-контракте уже была задумана модель typing внутри timeline/body;
- реальная реализация ушла в header subtitle;
- из-за этого нельзя просто переиспользовать typing indicator между `Chat` и `Chats`.

Как надо:

- решить один canonical typing model;
- либо убрать dead field, либо заполнить его и рисовать typing bubble/status row;
- общий live activity model потом использовать и для `Chats`.

## Дополнительные улучшения

1. Добавить unified `ConversationActivityState`.

Один источник для `typing`, `recording video`, `uploading`, `draft`, `last local activity`. Его можно показывать и в `Chats`, и в `Chat`, и в notification logic.

2. Сделать полноценный actions layer для `Chats`.

Long press/swipe menu: pin/unpin, mute, mark read/unread, archive/delete. Даже если backend пока поддержит только pin, UI-контракт лучше заложить расширяемым.

3. Улучшить pinned UX.

Pinned bar должен показывать count, краткий preview, переход к конкретному сообщению, animated collapse/expand, быстрый unpin. Для нескольких закрепов лучше горизонтальный pager/stack, а не просто список.

4. Добавить motion spec для сообщений.

Единые правила: outgoing flight, incoming fade/slide, delivery status morph, failed shake/retry affordance, video circle thumbnail-to-player transition. Это даст ощущение живого мессенджера, а не просто списка.

## Приоритеты

P0:

- Подключить pinned messages как отдельный state/flow.
- Исправить typing в `Chats`.
- Запретить raw sensitive logs.

P1:

- Добавить conversation pin model/API/local/UI.
- Переделать video circle thumbnails на стабильный preview contract.
- Оптимизировать merge сообщений.

P2:

- Motion system для отправки/получения.
- Pagination для `Chats`.
- Unified conversation activity model.

## Места, с которых начинать

- `feature/chats/src/main/java/me/floow/chats/uilogic/chats/AndroidChatsListRepository.kt`
- `feature/shared/src/commonMain/kotlin/me/floow/shared/chats/model/ChatListItemModel.kt`
- `feature/shared/src/commonMain/kotlin/me/floow/shared/chats/uilogic/direct/DirectChatStateHolder.kt`
- `feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatThreadRepository.kt`
- `feature/shared/src/commonMain/kotlin/me/floow/shared/chats/ui/SharedChatUiAdapters.kt`
- `feature/chats/src/main/java/me/floow/chats/ChatRoute.kt`
- `feature/chats/src/main/java/me/floow/chats/videocircle/MessageCirclePlayer.kt`
- `core/data/src/main/java/me/floow/data/repos/ChatsRepositoryImpl.kt`
- `core/database/src/main/java/me/floow/database/localstore/DirectChatsLocalStoreImpl.kt`
- `core/api/src/commonMain/kotlin/me/floow/api/util/extensions/logFailureResponse.kt`
