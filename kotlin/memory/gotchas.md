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
