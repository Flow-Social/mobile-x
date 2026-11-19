# Backend — Go API Service

## 1. Стек и архитектура
- **Язык / фреймворк**: Go 1.22+, Gin framework (`github.com/gin-gonic/gin`)
- **Исходники**: `Backend/backend-go/`
- **Запуск бинарника на сервере**: `/var/www/flow-backend/server` через systemd `flow-backend`
- **Переменные окружения**: `/var/www/flow-backend/.env`

## 2. База данных PostgreSQL (`flow_db`)
- Пользователь: `flow_user`, пароль в `.env` и `CREDENTIALS_ADMIN.txt`
- Основные таблицы:
  - `users` (id, email, username, name, avatar, bio, google_id, password_hash, total_likes_received, last_seen_at, token_version)
  - `posts` (id, author_id, category, description, image_urls, likes_count, comments_count, created_at)
  - `comments`, `categories`, `conversations`, `messages`, `device_push_tokens`
- **Индексы**: частичные индексы `idx_posts_created_at` и `idx_posts_category_created_at` (с фильтром `WHERE deleted_at IS NULL`) для быстрой пагинации ленты.

## 3. Авторизация, безопасность и сокеты
1. **Google OAuth (PKCE) & JWT**:
   - Android клиент выполняет OAuth flow через CustomTabs, обменивает auth code на `id_token` у Google.
   - Клиент отправляет токен на `POST /api/v1/auth/google`.
   - Бэкенд валидирует аудиторию (`googleAndroidClientID` и `googleWebClientID`).
   - Если пользователь найден по `google_id` или по `email`, аккаунт связывается и выдается JWT.
   - **Срок жизни токена**: снижен с 365 дней до 60 дней (`defaultTokenTTL`). В токен включен `token_version`.
   - **Логаут и отзыв сессий**: эндпоинт `POST /api/v1/auth/logout` инкрементирует `token_version` в БД. Функция `auth.GenerateToken` автоматически считывает актуальный `token_version` пользователя, а `middleware.AuthRequired` (`validateCredential`) мгновенно отклоняет токены с устаревшей версией (`token_version < dbTokenVersion`) со статусом 401 Unauthorized.
2. **Rescue Sync (`POST /api/v1/rescue/sync`)**:
   - Аварийный эндпоинт для спасения локальных данных из Room (профиль, посты, токены).
   - Требует валидный подписанный JWT (`auth.ValidateToken`). Не доверяет arbitrary `user_id` из JSON.
   - Защита постов: перезапись разрешена только автору (`posts.author_id = resolvedUserID`).
   - Синхронизация sequence `users_id_seq` и `posts_id_seq` ограничена `<= 10_000_000` (защита от sequence overflow DoS).
3. **Хранилище S3 & Оптимизация медиа**:
   - Presign PUT подписывает заголовок `Content-Type` через AWS SigV4, блокируя подмену MIME-типов.
   - Разрешенные типы включают `post`, `avatar`, `background`, `chat_video_circle` и `chat_video_circle_thumbnail`.
   - Оптимизация постов защищена ограничением размера через `io.LimitReader` (`postExternalMaxBytes`), а загрузка 3 вариантов картинок (`lq`, `preview`, `full`) выполняется параллельно через `sync.WaitGroup`.
4. **Алгоритм ленты (Feed)**:
   - `UndoSwipe`: выполняется за $O(1)$ через точечный откат дельт категории и автора (`rollbackBaseDeltas`) без повторной симуляции всей истории свайпов (защита от DoS).
   - `buildSequenceFromBucketsDetailed`: подбор кандидатов при ограничении streak не сбрасывает кандидатов другой категории, продвигая указатель только по уже просмотренным (`seen`).
   - `getLastCategories`: детерминированная сортировка `ORDER BY s.created_at DESC, s.id DESC`.
   - `loadRecycledCandidates`: механизм рециклинга контента при исчерпании основных корзин. Подмешивает только старые скипы пользователя (`s.is_liked = FALSE AND s.created_at < NOW() - INTERVAL '14 days'`) с пессимизацией скора в 4 раза и причиной `FEED_REASON_RECYCLED` («Вспомнить забытое»). Лайкнутые посты навсегда исключены из рекомендательной ленты.
   - `writeNeighbors`: оптимизированная пакетная вставка соседей в один `INSERT ... ON CONFLICT` без шторма микро-транзакций и без отсечения давних пользователей (все накопленные профили интересов участвуют в коллаборативной фильтрации).
5. **Realtime WebSocket чатов**:
   - При превышении лимита 3 соединений на пользователя (`SubscribeWithLimit`) старое соединение принудительно вытесняется (FIFO), предотвращая блокировку пользователя зомби-сокетами при частой смене сетей.
   - Параллельная запись в сокет защищена `writeMu`.
   - **Тайпинг-маска для записи кружков**: эвент тайпинга поддерживает поле `action` (`typing` / `recording_video`) через WebSocket и REST `POST /api/v2/chats/conversations/:id/typing`, транслируя собеседнику статус записи кружка.
   - **Удаление диалогов (`DELETE /api/v2/chats/conversations/:conversationId`)**:
     - `for_everyone=false`: локальное удаление для себя через проставление `cleared_at = NOW()` в `conversation_participants`. Диалог скрывается в `ListConversations` (`AND (cp.cleared_at IS NULL OR lm.created_at IS NULL OR lm.created_at > cp.cleared_at)`), история сообщений до этой отметки не отдается. Новое сообщение от собеседника возвращает диалог в список с чистого листа.
     - `for_everyone=true`: полное удаление диалога (`DELETE FROM conversations`), каскадное удаление в БД и рассылка realtime события `conversation_deleted` всем участникам через WebSocket.
   - **Чтение сообщений и курсоры (Telegram Parity, 20 сентября 2026)**:
     - `ChatsRealtimeWS` принимает входящее сообщение от клиента с типом `read_up_to` (или `read`) и `message_id`, выполняя `service.MarkReadUpTo` прямо через открытый WebSocket без лишних HTTP roundtrips.
     - `RealtimeSnapshot` и событие `hello` содержат `peer_last_read_message_id`, что исключает ошибочное трактование собственного `last_read_message_id` клиентом как прочтение собеседником.
6. **Admin Service Account**:
   - `ADMIN_SERVICE_EMAIL=admin@flow.me`
   - Права администратора определяются функцией `isAdminRequest(c *gin.Context)` в `internal/handlers/admin.go`.

## 4. Медиа-сообщения и контракты чата (Этап 1: Voice & Video Circles)
1. **База данных PostgreSQL**:
   - Таблица `messages` расширена колонкой `media_waveform TEXT NULL` (хранит JSON-массив амплитуд `[int,...]`, 0..100).
2. **S3 Presign**:
   - Добавлен пресайн-тип `chat_voice` (`kind = "chat_voice"`). Разрешенные MIME-типы: `audio/ogg`, `audio/opus`, `audio/aac`, `audio/mp4`, `audio/m4a`, `audio/mpeg`.
3. **API отправки сообщений (`POST /api/v1/chats/messages`)**:
   - Поддерживает nested объект `media` с полями `type`, `url`, `thumbnail_url`, `duration_ms`, `width`, `height`, `waveform`.
   - Полная обратная совместимость с flat-полями (`video_circle_url` и т.д.).
   - **Thumbnail URL (Telegram Parity)**: функция `validateMediaThumbnail` поддерживает как компактные base64-превью (`data:image/jpeg;base64,...` и `data:image/webp;base64,...` до 16 КБ), так и HTTP/HTTPS URL (до 2048 симв.). В `SendMessage` исправлена потеря `thumbnail_url` для сообщений типа `image` — теперь миниатюра персистентно сохраняется в колонку `media_thumbnail_url` и транслируется в WebSocket realtime событиях. Для `video_circle` добавлена строгая валидация `media.thumbnail_url`.
   - Тип сообщения `voice`: `type = "voice"`, fallback текст `"Голосовое сообщение"`, сохранение `media_waveform`, лимит длительности до 600 секунд (10 минут).
   - Push-уведомления содержат превью `"Голосовое сообщение"` и `"Видеосообщение"`.

5. **Медиа-сообщения чата (Этап 2: Images, Albums & S3 Presign Fix)**:
   - В таблицу `messages` добавлены колонки `group_id VARCHAR(64) NULL` и `media_position INT NOT NULL DEFAULT 0`. Создан частичный индекс `idx_messages_group_id` (`WHERE group_id IS NOT NULL`).
   - **S3 Presign Allowlist (`s3_presign.go`)**: исправлен `chat_image` в allowlist строк 226 (`IsAllowedURLForUser`), добавлен префикс ключа `media/chat/images/{userID}/{randomPart}{ext}`. Добавлены тесты `s3_presign_test.go` (`TestIsChatImageURLAllowedForUser`).
   - **Строгая серверная валидация альбомов (`internal/chats/service.go`)**:
     - Ограничение позиции: `0 <= media_position < 5`.
     - Все сообщения альбома обязаны принадлежать одному `conversation_id` и одному `sender_id`.
     - Максимум 5 сообщений в альбоме (`ErrInvalidAlbum` -> `400 INVALID_ALBUM`).
     - Запрет коллизий и дубликатов позиций (`ErrDuplicateMediaPosition` -> `400 DUPLICATE_MEDIA_POSITION`) с сохранением полной идемпотентности повтора по `client_message_id`.
   - API отправки (`POST /api/v1/chats/messages`): поддержка `type = "image"`, параметров `group_id`, `media_position`, полей ширины `width` и высоты `height`. Fallback текст для push и старых клиентов: `"Фотография"`.
   - Сообщения с одинаковым `group_id` возвращаются в истории и объединяются клиентом в Telegram-style альбомы по порядку `media_position`.
   - **Production Recovery & Album Overhaul (12 сентября 2026)**:
     - Синхронизирован бинарник `flow-backend` (sha256: `c69d7baa98419a223c134ec99ae46be3547a4807bf2a66c216f6a45c78e3b259`).
     - `loadMessageByID` считывает `group_id`, `media_position`, `media` поля через `messagePageSelectSQL` + `scanMessageRows`.
     - Добавлено диагностическое логирование в `ChatsSendMessage` (`internal/handlers/chats.go`) при 500 и 400 INVALID_MEDIA.
     - Добавлены unit-тесты `TestChatImageMediaAllowed` (`chats_test.go`) и `TestLoadMessageByID_ImageMedia` (`service_initial_test.go`).
     - Подтвержден live production smoke-тест: presign `chat_image`, S3 PUT, single image send (id=38), idempotency retry (deduped=true), album send (id=39, 40), history reading.
     - **Атомарная отправка альбомов (`POST /api/v2/chats/conversations/:conversationId/messages/album`)**:
       - Эндпоинт `ChatsSendAlbumMessage` в `internal/handlers/chats.go`. Принимает JSON `{"items": [{"client_message_id", "media": {...}, "caption": "..."}]}`.
       - Метод `SendAlbumMessages` в `internal/chats/service.go`: атомарная Postgres транзакция (`tx`). Лимит альбома расширен с 5 до 10 фотографий (`maxAlbumSize = 10`).
       - Всем элементам альбома присваивается единый серверный `group_id` (UUIDv4) и последовательные позиции `media_position` (0..N-1). Подпись (caption) сохраняется на первом элементе (`media_position == 0`).
       - Идемпотентность: дедупликация по `client_message_id` в таблице `dedup_tokens` внутри транзакции.
       - Обновление `conversations.last_message_id` последним сообщением альбома, инкремент `unread_count` участников, realtime-рассылка событий каждого сообщения через WebSocket.

## 5. Деплой бэкенда
- В корне проекта есть скрипт: `./deploy_backend.sh`
- Он компилирует Go бинарник под `linux/amd64`, синкает на сервер, выполняет миграции и перезапускает `systemctl restart flow-backend`.

## 6. Реализованный media follow-up (13 сентября 2026)
- Серверная валидация image media принимает только ограниченный `data:image/jpeg;base64` micro-thumbnail: decoded payload не более 8 KiB; содержимое thumbnail не логируется.
- Atomic album endpoint и лимит 10 оставлены без нового realtime-типа: backend сохраняет альбом транзакционно, клиент детерминированно объединяет отдельные события по `out/in + groupId`.
- Локальная Go-проверка не выполнена: в текущем окружении команда `go` отсутствует. Перед backend deploy необходимо прогнать `go test ./internal/chats ./internal/handlers ./internal/storage` в окружении с Go.

## 7. Клиентская оптимизация WebSockets и Push ACK (15 сентября 2026)
- **Удаление токена из query-параметров (`CommentsRealtimeApiImpl.kt`)**: передача `?token=...` в URL WebSocket-подключения полностью удалена. Авторизация переведена строго на стандартный HTTP-заголовок `Authorization: Bearer <token>`, предотвращая логирование конфиденциальных токенов пользователей в журналах запросов Nginx.
- **Экспоненциальный бэкофф и проверка сети (`CommentsRealtimeApiImpl.kt`, `ChatsRealtimeApiImpl.kt`)**: интервалы переподключения сокетов увеличены с агрессивных 1.5s–3s до экспоненциальных ступеней `5s -> 10s -> 30s`. Перед любой попыткой реконнекта сокеты ожидают восстановления интернет-соединения через `networkMonitor.isOnline.first { it }`, предотвращая шторм повторных подключений к серверу при офлайне.
- **Устранение тройного дублирования Push ACK (`ChatPushAckSender.kt`)**: логика подтверждения пушей переведена на строгую цепочку фоллбеков без параллельной отправки: сначала отправка через активный WebSocket -> при неудаче одиночный прямой REST HTTP-запрос -> и только при сбое сети постановка задачи в очередь WorkManager (`PushAckScheduler.enqueue(...)`).

## 8. Атомарное батчевое удаление сообщений (19 сентября 2026)
- **Эндпоинты**:
  - `POST /api/v2/chats/conversations/:conversationId/messages/delete-batch`
  - `DELETE /api/v2/chats/conversations/:conversationId/messages/batch`
  - Принимают JSON: `{"message_ids": [1, 2, 3]}` (валидация: массив не пуст, размер `<= 100`).
- **Транзакционный метод `DeleteMessagesBatch` (`internal/chats/service.go`)**:
  - Строгая сортировка `ids` и блокировка `SELECT ... FOR UPDATE` для предотвращения дедлоков в Postgres при параллельных запросах.
  - Мягкое удаление пачкой: `UPDATE messages SET deleted_at = CURRENT_TIMESTAMP WHERE id = ANY($1)`.
  - Корректный пересчет `conversations.last_message_id` (наибольший оставшийся незаделеченный `id`).
  - Пересчет `conversation_read_state` для участников диалога без гонок.
  - Публикация realtime-событий `message_deleted` для каждого удаленного сообщения участникам через сокеты.

## Личные чаты: границы истории и read-up-to

- DTO диалога отдаёт `first_unread_id` и `read_state_version`; окно `messages/around` отдаёт `has_older` и `has_newer`. Якорь за границей очищенной истории возвращает 404.
- Go обновляет курсор и счётчик непрочитанных в транзакции под блокировкой строки диалога. С ней же сериализованы отправка, удаление и закрепы. Отправка сохраняет накопленные непрочитанные автора. Отложенное подтверждение допустимо для уже удалённого сообщения того же диалога.
