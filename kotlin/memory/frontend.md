# Frontend — Android Client & Admin Web

> 💡 **Архив детальных чейнджлогов**: Полная история ежедневных доработок и калибровок (сентябрь 2026) перенесена в [`memory/archive/frontend-changelog-2026-09.md`](./archive/frontend-changelog-2026-09.md). Этот файл содержит только актуальную архитектуру, ключевые соглашения и правила разработки.

---

## 1. Общий стек и структура репозитория

| Компонент | Путь | Стек | Роль |
|---|---|---|---|
| **Android Client** | `Mobile/` | Kotlin, Jetpack Compose, KMP (`commonMain`), Koin DI, Room, Ktor, Coil 3 | Основное мобильное приложение соцсети. |
| **Admin Web** | `Backend/adminpanel/` | Next.js 14 (React 18), TypeScript, Tailwind CSS, PM2 (`flow-admin`, port 3000) | Панель модерации и управления контентом. |

---

## 2. Android Client (`Mobile/`) — Архитектура

### 2.1. Модульная структура
- `:core:common` — базовые модели, утилиты, форматирование времени (`FlowTimeFormatters`).
- `:core:database` — Room база данных (`AppDatabase`), DAO, миграции (`DatabaseMigrations.kt`).
- `:core:network` — Ktor HTTP клиент, сетевые модели, WebSocket контракты, Auth Interceptor.
- `:core:uikit` — дизайн-система Flow, токены темы (`FlowTheme`), общие контролы:
  - `chat/` — компоненты баблов (`ChatBubble.kt`, `ChatAlbumBubble.kt`, `VoiceMessageBubble.kt`, `MessageCirclePlayer.kt`), поле ввода (`MessageInputField.kt`, `UnifiedRecordingCapsule.kt`).
  - `viewer2/` — Telegram-grade корневой медиавьюер (`FullscreenImageViewerV2.kt`, `ViewerContentLayer.kt`).
  - `sheets/` — полноэкранные модальные шторки (`FlowModalSheetOverlay.kt`).
  - `text/` — форматирование текста, Markdown, спойлеры и ссылки (`TextWrap.kt`, `FlowTextFormatter.kt`).
- `:feature:chats` — экран переписки (`ChatRoute.kt`, `DirectChatStateHolder.kt`, `AndroidVideoCircleSyncCoordinator.kt`).
- `:feature:feed` / `:feature:shared` — рекомендательная лента с 3D-веером карточек (`SharedFeedScreen.kt`, `SharedFeedStateHolder.kt`).
- `:feature:profile` — экран профиля (`ProfileRoute.kt`, `EditProfileSheetRoute.kt`, Bump шторка).
- `:feature:comments` — общие компоненты комментов (`SharedCommentsRoute.kt`).
- `:app` — точка входа `MainActivity.kt`, корневая навигация `FlowNavHost.kt`.

### 2.2. Архитектурные правила и конвенции
1. **Clean Architecture + MVI / StateHolder**:
   - Бизнес-логика экранов инкапсулирована в `StateHolder` (например, `DirectChatStateHolder`).
   - Все `StateHolder` регистрируются в Koin как `factory` и в Compose **ОБЯЗАТЕЛЬНО** внедряются через:
     ```kotlin
     val stateHolder = remember { koin.get<DirectChatStateHolder>() }
     ```
     *Без `remember` StateHolder пересоздается на каждую рекомпозицию, сбрасывая UI-стейт и плодя корутины!*
2. **Сетевой стек и авторизация**:
   - Базовый URL API: `https://31.77.9.57.nip.io/api/v1` (`AuthApiConfig.kt`).
   - Авторизация: Google OAuth (PKCE) через CustomTabs + постоянный JWT токен.
   - Первичная регистрация: при `is_new_user = true` используется `pending_token` и завершение через `POST /api/v1/auth/google/complete`.
3. **Локальная база данных (Room)**:
   - Хранит кэш профиля, чаты, сообщения и исходящую очередь медиа.
   - При обычном логауте (`LogoutUseCase`) база очищается (`clearLocalCache = true`).
   - При аварийном протухании сессии данные сначала спасаются через `RescueSyncManager.syncIfNeeded()`.

---

## 3. UI & Графика: Железные правила производительности (120 FPS)

1. **Изоляция рекомпозиций (Draw Phase)**:
   - Все значения анимаций (`alpha`, `scale`, `translationX/Y`, `clip`) должны передаваться через лямбды в `Modifier.graphicsLayer { ... }` или `Modifier.drawBehind { ... }`.
   - Запрещено читать быстро меняющиеся `Animatable` или `animateFloatAsState` напрямую в теле Composable, если это триггерит релейаут всего дерева.
2. **ReverseLayout LazyColumn в чатах**:
   - Список сообщений чата использует `reverseLayout = true`.
   - Любой элемент списка в `itemsIndexed` **ОБЯЗАН** быть обернут в единый контейнер:
     ```kotlin
     Column(modifier = Modifier.fillMaxWidth()) {
         MessageBubble(...)
         Spacer(Modifier.height(spacingAfter))
     }
     ```
     *Если вызывать `Spacer` отдельным Composable внутри `item`, из-за `reverseLayout` Compose поставит его СВЕРХУ сообщения, сломав межкластерные отступы.*
3. **Пружинные анимации Dp**:
   - Никогда не анимировать `Dp` для `Modifier.padding(...)` через пружины с отскоком `Bouncy` без защиты `.coerceAtLeast(0.dp)`. Отрицательный отступ роняет приложение с фатальным `IllegalArgumentException: Padding must be non-negative`.
4. **Рендеринг процедурных спойлеров (`TextWrapCanvas.kt`)**:
   - Никаких аллокаций в кадре: массивы точек предвыделены, отрисовка через `Canvas.drawRawPoints`.
   - Квантование шума на 30 FPS (`noiseQuantum`), волна раскрытия интерполируется плавно на 120 FPS.
5. **Медиавьюер (`FullscreenImageViewerV2.kt`)**:
   - Живет на верхнем уровне корневого Compose-дерева (`FlowNavHost` с `zIndex(100f)`).
   - Координаты элементов для бесшовного полета передаются через `origin.boundsInWindow()`.
6. **Оптимизация скролла и мгновенного открытия чата (Telegram Parity, 20 сентября 2026)**:
   - **LazyColumn `contentType`**: Запрещено использовать рефлексию `::class.simpleName` в лямбде `contentType`. Используются статические строковые токены (`"date"`, `"text"`, `"reply"`, `"image"`, `"voice"`, `"video_circle"`, `"post_preview"`), предотвращая лишние аллокации строк и пересоздание нод при флинге.
   - **Замер координат баблов**: `Modifier.onGloballyPositioned` для `rowBoundsByMessageKey` и `bubbleBoundsByMessageKey` защищен флагом `if (!isScrollBusy || isVideoCircle)`. Во время активного скролла координаты в `SnapshotStateMap` не пишутся, устраняя мутации стейта и паразитные микрофризы (120 FPS).
   - **Мгновенный старт без скелетона и изоляция стейта между чатами**: `DirectChatStateHolder` инициализирует `_state` синхронно из `SharedChatSessionCache.cachedThreadSnapshot` прямо в `load()` до запуска корутины. Если снимка для открываемого чата нет в сессии — стейт СИНХРОННО сбрасывается в `DirectChatScreenState.Loading`, исключая показ сообщений предыдущего собеседника. `ChatRoute.kt` оборачивает `stateHolder` в `remember(initialRequest, chatRealtimeContract, chatPresenceContract)`, гарантируя выделение изолированного стейта под каждый диалог.
   - **Тактильный Swipe to Reply**: `replyDraggable.kt` выполняет `haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)` ровно в момент преодоления 70% порога оттяжки, а возврат сообщения анимируется через пружинную физику `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)`. Иконка `ReplyMarker` плавно нарастает, смещается и вращается в реальном времени вслед за пальцем `(scale, alpha, rotation, translation)`.
   - **Двухуровневый рендеринг медиа**: `ChatAlbumBubble.kt` показывает размытый микро-превью (`thumbnailUrl` с `blur(14.dp)`) в качестве немедленной подложки, поверх которой плавно догружается четкий полноразмерный оригинал `fullImageModel`. Высота ячеек заморожена через фиксированные `rowHeights` и `itemRatio`, исключая layout shifts.
7. **Механизм чтения сообщений и статусов (Telegram Parity, 20 сентября 2026)**:
   - **Дебаунс чтения (3 секунды)**: По аналогии с `MessagesController.ReadTask` в Telegram, скролл сообщений в `DirectChatStateHolder` не спамит сервер. Локальный стейт обновляется немедленно, а отправка на сервер дебаунсится на 3 секунды (`READ_DEBOUNCE_DELAY_MS`). При закрытии экрана (`dispose()`, `flushReadAndOpenAnchorOnClose()`) отложенное чтение мгновенно сбрасывается на сервер (`NonCancellable`).
   - **Честный критерий видимости в Compose (`isVisibleEnoughToBeRead`)**: `HasDataState.kt` не читает сообщение при случайном касании края экрана на 1 пиксель. Требуется, чтобы не менее 60% высоты баббла находилось в области видимости (`visibleFraction >= 0.6f`), либо длинное сообщение занимало более 60% высоты экрана.
   - **Прямой транспорт через WebSocket**: `ChatRealtimeSessionManager` поддерживает отправку `read_up_to` прямо через активный сокет, разгружая WorkManager/HTTP.
   - **Фикс ложных галочек**: В событии `hello` сокета `last_read_message_id` больше не интерпретируется как статус прочтения собеседником. Собеседник читает только через явное поле `peer_last_read_message_id`.

---

## 4. Медиа-пайплайн чата (Telegram Parity)

1. **Durable Media Outbox (`AndroidVideoCircleSyncCoordinator.kt`)**:
   - Отправка фото, альбомов, видеокружков и войсов сохраняется в Room (`direct_chat_outbox_commands`).
   - Файлы лежат в `filesDir/chat_media_outbox/{clientMessageId}/` и удаляются только после ACK сервера.
   - Незавершенные выгрузки автоматически возобновляются после перезапуска приложения.
2. **Стриминг S3**:
   - Выгрузка идет через Ktor `OutgoingContent.WriteChannelContent` чанками по 64 КБ с передачей `onProgress: (Float) -> Unit`. Heap не забивается OOM-аллокациями.
3. **Оптимистичный UI и Micro-Thumbnails**:
   - При отправке фото создается 24x24 / 32x32 stripped Base64 превью (~300 байт) для мгновенного отображения размытого силуэта.
   - Габариты фото извлекаются синхронно без декодирования битмапа через `BitmapFactory.Options.inJustDecodeBounds` для предотвращения скачков геометрии (Zero Layout Shift).
4. **Альбомы фото (до 10 штук)**:
   - Группируются сервером и клиентом по `group_id` и `media_position` (0..N-1).
   - Сервер обязан сохранять клиентский `idempotencyKey` как постоянный `groupID`, исключая генерацию новых случайных UUID при отправке.
   - Сжатие медиа выполняется параллельно в фоне через `async(Dispatchers.IO)` ДО добавления в UI стейт. Это дает точные размеры `width/height` и финальный путь к файлу сразу.
   - Все элементы альбома добавляются в UI атомарно ОДНИМ вызовом `DirectChatStateHolder.addLocalImages(...)` за один проход `_state.update`. Это исключает промежуточную перестройку сетки и моргание картинок при последовательном добавлении по одной штуке.
   - Постоянный путь к сжатому файлу (`context.cacheDir/chat_images/img_uuid.jpg`) записывается в `domainMessage.media.localPath` и сохраняется в Room. Папка `chat_media_outbox/{clientMessageId}` используется только как временная очередь и удаляется в `cleanupMediaOutbox`, не затрагивая основной локальный файл картинки (исключает исчезновение локального файла и скачивание с S3 после отправки).
   - `uiKey` исходящего альбома на клиенте привязывается к `album_${groupId}_$chunkIdx` и остается 100% иммутабельным на всех фазах (до отправки, во время, при ACK сервера и при чтении из Room). Это предотвращает удаление/пересоздание Compose-ноды в LazyColumn, моргание `animateItem` и сброс скролла.
   - Дефолтный `spacingAfter` для `ImageMessage` при временном отсутствии `layoutState` зафиксирован на 16.dp (вместо 2.dp), исключая прыжок высоты при рекомпозиции.
   - Отправка пакетом через `POST /api/v2/chats/conversations/:id/messages/album`.
5. **Выделение и удаление сообщений и альбомов (Telegram Parity, 19 сентября 2026)**:
   - **Выделение альбома целиком vs точечный чекбокс (Telegram-style)**:
     - Клик или лонг-тап по баблу сообщения или любой ячейке фото в альбоме по умолчанию выделяет **весь альбом целиком** (`toggleMessageSelection(messageId)` через `groupId`). В `HasDataState.kt` лонг-тап по изображению (`imageContent.onLongClick`) теперь напрямую инициирует выделение сообщения/альбома через `onChatBubbleLongClick(message)` (вместо всплывающего контекстного меню), что позволяет включать режим выбора долгим тапом прямо по фото, а не только по пустой области строки. Если все фото альбома уже выделены — выбор со всех снимается; если нет — выделяются все фото альбома.
     - В активном режиме селекшна обычный тап по любой ячейке `SingleImageCell` или по круглому чекбоксу точечно переключает выбор ровно одной конкретной фотографии (`onToggleSelection(item.id)`).
     - Полноэкранный вьюер в режиме селекшна блокируется, счетчик в тулбаре отображает точное количество выбранных ID (`selectedMessageIds.size`).
   - **Немедленное удаление и персистентность (устранение воскрешения сообщений)**:
     - В диалогах подтверждения удаления `AlertDialog` удаление выполняется немедленно через `stateHolder.deleteMessages(listOf(id))` (без 4-секундной задержки отмены, так как юзер уже явно подтвердил действие).
     - Все локальные и серверные коммиты удаления (`commitPendingDelete`, `removeMessageImmediately`, `deleteMessages`, `cancelPendingOutgoingMessage`, `cancelLocalImage`, `cancelLocalVoice`, `cancelLocalVideoCircle`) выполняются в `scope.launch(NonCancellable) { withContext(NonCancellable) { ... } }`, что гарантирует завершение транзакций в Room и сети даже при немедленном выходе из чата (`onDispose`).
     - В `DirectChatStateHolder` память об отмененных клиентом сообщениях `cancelledClientMessageIds` не очищается при `dispose()`, предотвращая повторную вставку отмененных сообщений из кэша Room при возврате в чат.
     - В `AndroidVideoCircleSyncCoordinator.cancel()` при отмене фото альбома атомарно отменяются в `localStore.cancelOutgoing` и outbox все связанные элементы альбома под `NonCancellable`.
   - **Удаление альбомов vs отдельных фото**:
     - Контекстное меню по лонг-тапу на альбоме вызывает диалог подтверждения: «Удалить только эту фотографию» (удаляет выбранное `message.id`) или «Удалить весь альбом» (`stateHolder.deleteAlbum(groupId)`).
     - Батчевое удаление через тулбар корзины передает список выбранных ID в один вызов `stateHolder.deleteMessages(selectedMessageIds)`.
     - Устранен веер параллельных HTTP-запросов и корутин: репозиторий вызывает атомарный эндпоинт `ChatsApi.deleteMessagesBatch(conversationId, messageIds)`, предотвращая race conditions и дедлоки в БД.
6. **Видеокружки**:
   - Запись и компрессия в нативный 1:1 квадрат 360x360 (`LAYOUT_SCALE_TO_FIT_WITH_CROP`).
   - Полет кружка в чат стартует синхронно на 0-м кадре при отпускании пальца (`Instant Zero-Latency Takeoff`).
   - Мгновенная генерация base64 `TinyThumb` 24x24 px из первого кадра (`TinyThumbUtils.generateMicroThumbFromFile`), без лишнего отдельного аплоада файла превью на S3.
   - Потоковый кэш `SimpleCache` (`VideoCircleCacheProvider`, 256 МБ).
7. **Голосовые сообщения**:
   - Запись Ogg/Opus (API 29+) и AAC (API 28) с вычислением 48-точечного `media_waveform`.
   - Кэширование и оффлайн: `VoiceMediaCacheProvider` (128 МБ `SimpleCache` + `DefaultMediaSourceFactory`) в `VoicePlayerManager`. Все входящие голосовые кэшируются на диск при первом воспроизведении и доступны оффлайн.
   - Статус буферизации: `isBuffering` в `VoicePlaybackState` для индикации сетевой подгрузки.
   - Воспроизведение через `FlowVoicePlaybackService` (ExoPlayer + MediaSessionService) в фоне со шторкой уведомлений.
8. **Мгновенные превью и фото (Telegram Parity)**:
   - `TinyThumbUtils`: LRU-кэш (200 элементов) и декодер `data:image/jpeg;base64,...` / `webp` в `Bitmap` (микро-JPEG 24x24 px, ~150 байт).
   - Единый `AsyncImage` Coil в `ChatAlbumBubble.kt`: `microThumbPainter` подставляется напрямую в `placeholder` с `blur(6.dp)` без создания дублирующего слоя `AsyncImage`, исключая моргание и race conditions.
9. **Менеджер автозагрузки, прогресс скачивания и экран «Данные и память» (Telegram Parity, сентябрь 2026)**:
   - `MediaDownloadController` (`core:domain`) и реализация `MediaDownloadManager` (`Mobile/core/data/src/main/java/me/floow/data/media/`): фоновый загрузчик медиа с семафором на 3 потока, приоритетами (`VISIBLE`, `BACKGROUND`), атомарным скачиванием во временный файл и перемещением в постоянный кэш.
   - Автозагрузка медиа подключена к `ChatsRepositoryImpl`: при получении сообщений через REST (`getConversationInitial`, `getMessages`, `getMessagesAround`) или Realtime WebSocket (`MessageCreated`) инициируется фоновая предзагрузка по правилам сети (Wi-Fi, Мобильный интернет, Роуминг).
   - Индикация скачивания в UI (Telegram Parity): `ChatRoute` собирает `mediaDownloadController.states` и прокидывает в `ChatAlbumBubble`. В `SingleImageCell` отображается оверлей `MediaTransferOverlay`:
     - Фаза `Downloading`: круговой индикатор `MediaTransferProgressRing` с процентами и крестиком отмены;
     - Фаза `Idle` (файл еще не скачан): кнопка со стрелкой вниз («скачать») поверх размытого микро-превью;
     - Фаза `Failed`: кнопка повтора со стрелкой перезагрузки;
   - Устранение повторного скачивания отправленных файлов:
     - В `MediaDownloadController` и `MediaDownloadManager` добавлен метод `registerLocalFile(url, localPath, mediaType)`. При успешной отправке фото, видеокружка или голосового координатор `AndroidVideoCircleSyncCoordinator` регистрирует локальный файл под хэшем URL в кэш менеджера и выставляет стейт `Completed`.
     - В `ChatsRepositoryImpl.triggerAutoDownloadForMessage`: если у сообщения уже есть локальный файл на диске (`media.localPath`), он сразу регистрируется в менеджере без сетевого запроса; для сообщений в статусе `SENDING` автозагрузка не триггерится.
     - В `SingleImageCell` (`ChatAlbumBubble.kt`): оверлеи загрузки `Downloading` и `Failed` отображаются строго при `!hasLocalFile`, исключая наложение оверлея поверх уже имеющегося на диске файла.
     - Приоритет актуальных путей: `effectiveLocalPath = downloadedLocalPath ?: initialLocalPath` с проверкой `.takeIf { File(it).exists() }`. Исключен баг, когда удаленный из кэша файл блокировал отображение скачанного фото для своих и чужих сообщений.
     - Оптимизация скорости скачивания: троттлинг эмита прогресса `_states.update` в `MediaDownloadManager` (не чаще 80 мс / дельта 5%) и буфер 64 КБ предотвращают лавину рекомпозиций в Compose и зависание стрима.
     - Приоритеты и отмена скачивания: явный клик пользователя (`MediaDownloadPriority.VISIBLE`) выполняется немедленно в обход `backgroundConcurrencyLimit = Semaphore(3)` фоновой автозагрузки, промоутя задачу из очереди. Отмена скачивания (клик по крестику или в любую точку ячейки) мгновенно закрывает сокет через `activeConnections.disconnect()`, удаляет `.tmp` файл, не сваливается в `Failed` и чисто возвращает оверлей в `Idle` со стрелкой и размером.
     - Сохранение скачанного состояния при перезапуске приложения: детерминированное имя файла кэша `media_{hash}.jpg` проверяется в `MediaDownloadManager.getDownloadState`, `getLocalPathIfDownloaded` и `enqueueAutoDownload`. При перезапуске приложения `ChatRoute` в `imageContent` мгновенно подтягивает `diskPath` и сеет стейт `Completed`. Скачанные ранее фото отображаются сразу готовыми без блюра и без кнопки «скачать».
     - Соответствие Telegram (Viewport-based auto-download): в Telegram (`DownloadController.java`) автозагрузка никогда не скачивает всю историю диалога разом. Из `ChatsRepositoryImpl` удалены слепые триггеры массовой автозагрузки для всей истории (`observeMessages`, `getConversationInitial`, `getMessages`, `getMessagesAround`). Автозагрузка работает строго как в Telegram:
        1. Для видимых ячеек экрана через `LaunchedEffect(albumMessage.images)` в `ChatRoute`: фото в зоне видимости экрана (и окно предзагрузки LazyColumn) автоматически ставятся в фоновую очередь с проверкой лимитов сети (`shouldAutoDownload`);
        2. Для свежих входящих сообщений реального времени через `ChatsRealtimeEvent.MessageCreated` в `ChatsRepositoryImpl`.
        Это исключает зависание фоновой очереди десятками старых картинок из истории чата.
     - Вытеснение фонового трафика при ручном скачивании (Telegram-style `pauseAllNextOperations`): в `MediaDownloadManager` добавлен счетчик активных пользовательских загрузок `activeForegroundDownloads: MutableStateFlow<Int>`. Когда пользователь нажимает «скачать» на любой картинке (`VISIBLE`), все фоновые автозагрузки (`BACKGROUND`) мгновенно приостанавливают вычитку чанков из сокетов через `activeForegroundDownloads.first { it == 0 }`, отдавая 100% сетевого канала и CPU приоритетной задаче пользователя. После завершения ручной загрузки фоновые задачи прозрачно продолжают работу.
   - Модели и настройки: `AutoDownloadPreset` (`LOW`, `MEDIUM`, `HIGH`, `DISABLED`) и `AutoDownloadConfig` в `core:domain`, персистентное хранение настроек в SharedPreferences через `MediaAutoDownloadSettings` (`core:uikit`).
   - Защита превью незагруженных фото: пока файл не скачан на устройство (`!hasLocalFile`), модель `AsyncImage` — строго микро-превью `item.thumbnailUrl` с `Modifier.blur(14.dp)`. Оригинал `remoteUrl` не отдается в Coil, исключая загрузку полного кадра до нажатия пользователем.
   - Статичная кнопка скачивания (Telegram Parity): кнопка 48dp с векторной стрелкой Telegram (`TelegramDownloadIcon`, ствол + крылья под 45°, stroke 2.6..3dp, round cap) без паразитного вращения (`shouldShowIndeterminateRing()` отключен для `Download`). Под кнопкой выводится точный размер файла (`sizeText`, например `1.8 МБ`).
   - Открытие после скачивания: по завершении загрузки локальный путь синхронизируется, оверлей скрывается, фото четкое. При тапе открывается `FullscreenImageViewerV2`, оборачивающий пути файловой системы в `File()` для мгновенного отображения в Coil 3.
   - Экран настроек `DataAndStorageScreen.kt` в профиле: выбор пресета, тумблеры сетей, вычисление дискового пространства с учетом кэша Coil `image_cache`. Очистка кэша Flow удаляет папки медиа, полностью очищает оперативный и дисковый кэш Coil (`SingletonImageLoader`), сбрасывает стейты `MediaDownloadController.resetAll()`, а `MediaDownloadManager.getDownloadState()` выполняет самовосстановление.
10. **Скорость и кинематика открытия чата (Telegram Parity, сентябрь 2026)**:
    - **Telegram EASE_OUT_QUINT кривая анимации**:
      - В `SwipeBackOverlay.kt` стандартный `FastOutSlowInEasing` заменен на `CubicBezierEasing(0.23f, 1f, 0.32f, 1f)` (точный эквивалент `CubicBezierInterpolator.EASE_OUT_QUINT` из Telegram `ActionBarLayout.java`).
      - Вход оверлея выполняется за 230 мс: экран мгновенно стартует за первые 40–50 мс и мягко, кинематографично «докатывается» до края без ощущения вязкости.
      - Закрытие оверлея оптимизировано до 210 мс с той же квинтовой кривой для мгновенного отклика.
    - **Устранение моргания баблов и картинок при холодном старте**:
      - В `ChatAlbumBubble.kt` устранен двойной бутерброд из двух независимых Compose-нод `AsyncImage`. Размытый thumbnail-плейсхолдер (`thumbPainter = rememberAsyncImagePainter(item.thumbnailUrl)`) передается напрямую в аргумент `placeholder` основного запроса `AsyncImage(fullImageModel, placeholder = ...)`. Это исключает визуальное мигание/двойную смену слоев при холодном открытии чата.
      - В `ChatRoute.kt` убрана искусственная задержка `delay(250)` перед `preloadChatAttachmentPicker` — фоновые диспатчеры больше не дробят рендер на два такта.
11. **Типографика и иконки статуса сообщения (Telegram Parity, сентябрь 2026)**:
    - **Шрифт времени**: в `MessageTimestampRow.kt` размер шрифта увеличен до `12.sp` (с `letterSpacing = 0.2.sp`), отступ между временем и статусом выставлен в `4.dp`.
    - **Иконки статуса доставки**: в `ChatStatusIcons.kt` унифицирован единый базовый контейнер `14.dp`, а векторные размеры согласованы с пропорциями Telegram:
      - Прочитано (две галочки): `13.dp × 8.dp`;
      - Отправлено (одна галочка): `11.dp × 9.dp`;
      - Отправка (часики `Schedule`): `12.dp × 12.dp`;
      - Ошибка (`ErrorOutline`): `12.dp × 12.dp`.
    - Все элементы сбалансированы по оптической плотности и базовой линии текста.

---

## 5. Web Admin (`Backend/adminpanel/`)

- **Размещение**: На сервере `31.77.9.57` в `/var/www/flow-admin-web/`.
- **Запуск**: Процесс PM2 `flow-admin` на внутреннем порту `127.0.0.1:3000`.
- **Внешний доступ**: `https://31.77.9.57.nip.io:8081/dashboard`
- **Безопасность**:
  - Внешний порт 8081 закрыт на уровне Nginx HTTP Basic Auth (`auth_basic "Flow Admin Area"`, `/etc/nginx/.htpasswd`).
  - Аутентификация с Go-бэкендом через сервисный аккаунт (`admin@flow.me`), JWT кэшируется в Node.js сервере (`tokenStore.ts`).
- **Сборка и перезапуск**:
  ```bash

- **TypeSafe Jev System One & Universal MCP Server (сентябрь 2026)**:
  - **Автономный репозиторий `jev-mcp` (Node.js & GitHub)**: репозиторий [y9Finsi/jev-mcp](https://github.com/y9Finsi/jev-mcp) полностью отвязан от специфики Flow.
  - **Универсальность**:
    - Ликвидированы любые захардкоженные пути к проектам. Сервер динамически определяет директорию через аргумент `directory_path` или `process.cwd()` текущего проекта.
    - Динамический поиск `.env` вверх по дереву каталогов проекта и в `$HOME/.env`.
    - Защита от зависания и сканирования корневой ФС (`/`) с лимитом `MAX_SCANNED_FILES: 8000` и `maxDepth: 12`.
  - **Инструменты**: `search_codebase`, `validate_code_targets`, `judge_with_jev`. Инструкции в `instructions.md` и README обязывают агентов передавать `directory_path` и следовать `next_action_steps`.

## Личные чаты: история и подтверждения прочтения

- Room v37, `direct_chat_progress`: ключ — аккаунт + диалог. Хранит позицию (ID, знаковое смещение или конец), границы последнего проверенного окна и максимальный неотправленный `read-up-to`. Android и iOS используют один отправитель; Android-хранилище предыдущей версии читается как запасной источник старой позиции.
- История показывает одно непрерывное окно. Явный переход, закреп и цитата используют серверный `messages/around`; `has_newer` сохраняется в клиенте. Room обновляет известные строки, но не склеивает старое окно с хвостом. Сохранённое окно можно показать офлайн; непроверенный кэш не продвигает курсор.
- Курсор продвигает видимая непрерывная последовательность входящих в активном Compose-чате. Открытие, кнопка «вниз» и отправка не считаются чтением. Закрепы панели наблюдаются из Room, а полный GET, включая пустой, заменяет их состав. Цитата не зависит от типа контента.
