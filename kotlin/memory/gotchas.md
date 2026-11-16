# Gotchas — Грабли, боли и критические правила

> ⚠️ **ВНИМАНИЕ**: Этот файл содержит список вещей, которые ни в коем случае нельзя нарушать. Нарушение приведет к падению продакшна, зависанию UI или утечкам памяти!

---

## 1. Инфраструктура и Сервер (31.77.9.57)

### 1.1. Соседний проект девушки (`prostranstvo`)
- На сервере `31.77.9.57` параллельно работает боевой сайт девушки Богдана:
  - Процесс PM2: `prostranstvo` на порту `127.0.0.1:5173`.
  - База данных: `prostranstvo_db`.
  - В Nginx корень `/` и `/api/events` проксируются на него!
- **ЖЕЛЕЗНОЕ ПРАВИЛО**: Никогда не трогать директорию `/var/www/prostranstvo`, базу `prostranstvo_db` и процесс PM2 с id `0`.

### 1.2. Конфигурация Nginx
- Никогда не пиши настройки Flow в `/etc/nginx/sites-available/default`! Деплой-скрипты стороннего проекта его перезаписывают.
- Конфигурация Flow **ДОЛЖНА ЖИТЬ ТОЛЬКО В** `/etc/nginx/conf.d/flow.conf`.
- Порт 80/443 обслуживают Flow API (`/api/v1/`, `/api/v2/`), а порт 8081 обслуживает Flow Admin Web.
- Порт 8081 защищен HTTP Basic Auth (`auth_basic "Flow Admin"`, пароли в `/etc/nginx/.htpasswd`).

### 1.3. Эндпоинт аварийного спасения (`RescueSync`)
- **ПРАВИЛО**: Ни в коем случае не возвращать `ParseUnverified` и не доверять `UserID` из JSON.
- Токен должен быть валидным JWT с подписью. Обновление постов разрешено только автору (`posts.author_id = resolvedUserID`).
- Вызов `setval` для sequence базы разрешен только в безопасном диапазоне (`<= 10000000`), чтобы исключить DoS через переполнение sequence.

### 1.4. Защита от SSRF при обработке внешних картинок
- В `post_optimize.go` запросы к внешним URL должны проверяться через кастомный `DialContext` на уровне IP. Запрещены приватные сети (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), loopback (`127.0.0.1`, `::1`), link-local (`169.254.0.0/16`) и любые порты, кроме 80 и 443.

---

## 2. Безопасность и Авторизация

### 2.1. Очистка Room при логауте
- **ПРАВИЛО**: При обычном логауте из настроек (`LogoutUseCase.kt`) по умолчанию база данных **ОЧИЩАЕТСЯ** (`clearLocalCache = true`), чтобы исключить утечку чужих чатов и профилей при смене пользователя на устройстве.

### 2.2. Флоу завершения регистрации Google
- При первичной регистрации через Google (`hasPendingRegistration() == true`) клиент обязан завершать регистрацию через `POST /api/v1/auth/google/complete` (`authApi.completeGoogleRegistration(...)`), а **НЕ** через `PUT /profile` (который падает с 401, так как постоянного токена еще нет).
- При успехе сохранять постоянный токен через `authenticationManager.writeAuthToken(token)`.
- При отмене экрана редактирования обязательно вызывать `authenticationManager.clearPendingRegistration()`.
- В `EditProfileStateHolder`: `uploadIfNeeded` обязан возвращать `Result<String?>` (с распаковкой `.getOrElse { return@launch }`), чтобы отсутствие аватара/фона (`null`) не прерывало сохранение и не оставляло вечный спиннер.

### 2.3. S3 Presign и SigV4 Content-Type
- При генерации presigned PUT URL бэкенд подписывает `ContentType` (`s3.PutObjectInput.ContentType`).
- Клиент (Android/Web) обязан передавать заголовок `Content-Type`, строго совпадающий с запрошенным в presign, иначе S3 вернет `403 SignatureDoesNotMatch`.
- Разрешенные типы S3 presign: `post`, `avatar`, `background`, `chat_video_circle`, `chat_video_circle_thumbnail`, `chat_voice`, `chat_image`.

### 2.4. Cleartext HTTP в Android
- Атрибут `android:usesCleartextTraffic="true"` удален из релизного `AndroidManifest.xml`. Незашифрованный HTTP разрешен **ТОЛЬКО** в отладочных сборках через `app/src/debug/AndroidManifest.xml` (для работы с локальным эмулятором `10.0.2.2`).

---

## 3. Android: Compose, Жизненный цикл и Анимации

### 3.1. Compose StateHolders из Koin
- **ПРАВИЛО**: Все вызовы `koinInject` / `koin.get` для StateHolder в Composable **ОБЯЗАТЕЛЬНО** оборачивать в `remember { ... }`.
- StateHolders зарегистрированы как `factory`. Без `remember` создается новый инстанс на каждый кадр рекомпозиции, стирается ввод пользователя и утекают корутины.

### 3.2. LazyColumn с `reverseLayout = true` и спейсеры
- **ПРАВИЛО**: Каждый элемент в `itemsIndexed` при `reverseLayout = true` **ОБЯЗАН** быть обернут в единый контейнер (`Column(modifier = Modifier.fillMaxWidth())`).
- Если внутри слота `item` эмитятся 2 Composable (сообщение и `Spacer`), из-за `reverseLayout = true` Compose размещает `Spacer` **СВЕРХУ** сообщения, ломая межкластерные отступы.

### 3.3. Пружинные анимации Dp и краш Modifier.padding
- **ПРАВИЛО**: Никогда не анимировать `Dp` для `Modifier.padding(...)` через упругие пружины (`spring(dampingRatio = Bouncy)`) без защиты `.coerceAtLeast(0.dp)`.
- Отскок в отрицательную область вызывает фатальный краш: `IllegalArgumentException: Padding must be non-negative`.
- Для отступов ставить `DampingRatioNoBouncy`, а упругие отскоки делать строго через `graphicsLayer { scaleX = ... }`.

### 3.4. Флаг FLAG_ALT_FOCUSABLE_IM и окно Activity (Зависание приложения)
- **КРИТИЧЕСКИЕ ГРАБЛИ**: Никогда не устанавливать `FLAG_ALT_FOCUSABLE_IM` на окно активити (`MainActivity.window`)!
- В `ChatAttachmentPickerSheet` диалог использует этот флаг, чтобы открываться поверх клавиатуры без перехвата фокуса.
- Поиск окна диалога должен идти строго циклом вверх по `parent` до `DialogWindowProvider`. Если флаг попадет на окно `MainActivity`, приложение намертво перестает реагировать на любые тачи.

### 3.5. Оптимистичные фото в UI (Zero Layout Shift)
- При отправке изображений бабл вставляется в стейт мгновенно с исходным `content://` URI, а габариты извлекаются через `BitmapFactory.Options(inJustDecodeBounds = true)` за 1 мс.
- Запрещено откладывать появление бабла до завершения компрессии (пользователь посчитает, что нажатие не сработало).

### 3.6. Порядок Modifier для коротких баблов (`defaultMinSize` vs `padding`)
- Использовать `.defaultMinSize(minWidth = 38.dp, minHeight = 36.dp).widthIn(max = 263.dp).padding(horizontal = 13.dp, vertical = 9.dp)` с `contentAlignment = Alignment.Center` в родительском Box. Без этого 1-символьные сообщения съезжают влево с гигантской дырой справа.

### 3.7. Слой отрисовки хвостика у медиа-альбомов (`ChatTailVector`)
- Вектор хвостика `ChatTailVector` в `Box` должен вызываться **первым**, ДО `imageContent`. В таком порядке скругленный 20dp угол фото накладывается поверх основания хвоста, и наружу выглядывает только аккуратный кончик.

---

## 4. Тестирование и Сборка

### 4.1. Зависание unit-тестов корутин с System.currentTimeMillis()
- **ГРАБЛИ**: Запуск тестов с бесконечными циклами ожидания реального времени (`System.currentTimeMillis()`) вешает Gradle Test Executor на 100% CPU навсегда, потому что виртуальный `advanceUntilIdle()` не двигает системные часы.
- Для быстрой верификации в агентах запускать компиляцию модулей (`./gradlew :core:uikit:compileDebugKotlin :feature:chats:compileDebugKotlin`) или строго изолированные тесты без циклов реального времени.

---

## 5. iOS Multiplatform (KMP), Koin DI и AVFoundation

### 5.1. Koin DI в общем коде: падения из-за Android-only зависимостей
- **КРИТИЧЕСКИЕ ГРАБЛИ**: В общем модуле `SharedCommonModule.kt` нельзя безусловно биндить реализации, зависящие от Android-only компонентов (например, `AndroidRepliesRepository(get(), get(), get())`, требующий `NotificationsRealtimeRepository` из Android `:core:data`). На iOS это вызывает фатальный краш в рантайме: `NoBeanDefFoundException` при открытии экранов (например, `SharedRepliesRoute`).
- **ПРАВИЛО**: Всегда использовать безопасный фоллбэк: `getOrNull<NotificationsRealtimeRepository>() ?: StaticRepliesRepository()`, либо выносить платформенные репозитории в platform-specific DI модули.

### 5.2. `ParametersHolder.get()` vs проверка типов
- **ГРАБЛИ**: Использование позиционного `params.get<T>(index)` при резолве ViewModel/StateHolder падает с `IndexOutOfBoundsException`, если фабрика вызвана с неполным или перемешанным набором аргументов.
- **ПРАВИЛО**: Использовать `params.values.filterIsInstance<T>().firstOrNull()` или типизированный `getOrNull<T>()`.

### 5.3. AVFoundation Video Circle & Voice: состояние AVAssetWriter
- **ГРАБЛИ**: Вызов `AVAssetWriter.finishWritingWithCompletionHandler` на iOS, когда запись была прервана, отменена или не успела стартовать (статус writer не равен `AVAssetWriterStatusWriting`), выбрасывает нативное исключение `NSInternalInconsistencyException` и валит приложение (`SIGABRT`).
- **ПРАВИЛО**: Всегда проверять:
  ```kotlin
  if (writer.status == AVAssetWriterStatusWriting) {
      writer.finishWritingWithCompletionHandler { ... }
  } else {
      writer.cancelWriting()
  }
  ```

---

## 6. Автоматизация UI, Скриншоты и Twombly

### 6.1. Правило точечных исправлений (No Blind Mass Re-runs)
- **ГЛАВНОЕ ПРАВИЛО**: Если на холсте Twombly уже есть 20 экранов и не заскринились только 2-3 (или пользователь попросил добавить конкретные экраны), **ЗАПРЕЩЕНО** сносить все фреймы и запускать полный 40-экраный сьют заново.
- Полный перегон тратит время, перегружает машину и может сломать фреймы, которые уже были идеальными. Пиши точечный скрипт, снимай ровно недостающие экраны и обновляй их поштучно.

### 6.2. Асинхронная загрузка контента (Лента/Feed) перед снимком
- **ГРАБЛИ**: Попытка снять скриншот сразу после `launch` приложения приводит к захвату чёрного/пустого экрана загрузки (файл ~68 КБ вместо >1 МБ).
- **ПРАВИЛО**: Всегда опрашивать состояние (`snapshot`) на наличие маркеров готового контента (например, `"0 комментариев"`, `"@y9finsi"`) и давать 1.5–2 секунды на полную отрисовку картинок Coil/Compose перед вызовом `xcrun simctl io booted screenshot`.

### 6.3. Интеракция с оверлеями и навигация на экранах с Dynamic Island
- **Контекстное меню сообщений**: Оверлей `MessageContextMenuOverlay` открывается по клику на бабл сообщения. Чтобы надёжно снять его, проверяй наличие в дереве действий (`"Ответить"`, `"Скопировать"`).
- **Безопасное закрытие оверлея**: Кликать строго по пустому фону (например, `(50, 700)`), а не по верхней части экрана, иначе тап попадёт в кнопку «Ответить» и откроет клавиатуру.
- **TopBar на устройствах с Dynamic Island (iPhone 17, 16 Pro и др.)**: Координаты системных кнопок навбара находятся ниже, чем на старых iPhone: `y ≈ 111` (а не 94). Для кнопки возврата из чата используй клик по лейблу/стрелке `(28, 111)`.

### 6.4. In-place обновление фреймов Twombly
- Twombly кэширует внутренние хэши/блобы контента (`content`). Простое изменение файла на диске не обновляет отображение на канвасе.
- Для обновления существующего фрейма используй:
  ```bash
  twombly update <frame_id> <path/to/image.png>
  ```
  Это сохраняет комментарии, координаты и структуру холста. Новые экраны добавляются через `twombly add <path> --x N --y N --title "..."`.

### 6.5. Зависания `agent-device` и очистка фоновых ресурсов
- Если фоновый раннер `AgentDeviceRunner` завис или был оборван посреди snapshot, SpringBoard блокирует повторный запуск тестов ошибкой `Application failed preflight checks (Busy)`.
- **Лечение**: Сбросить зависший раннер:
  ```bash
  xcrun simctl uninstall booted com.callstack.agentdevice.runner.uitests.xctrunner
  ```
- **ОБЯЗАТЕЛЬНАЯ ОЧИСТКА ПОСЛЕ РАБОТЫ**: Никаких висящих в фоне эмуляторов и сборщиков, греющих процессор:
  ```bash
  xcrun simctl terminate booted me.floow.iosApp
  xcrun simctl shutdown all
  killall Simulator 2>/dev/null || true
  killall xcodebuild 2>/dev/null || true
  pkill -f AgentDeviceRunner 2>/dev/null || true
  cd Mobile/kotlin && ./gradlew --stop
  ```
