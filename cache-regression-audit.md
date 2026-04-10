# Аудит регрессий local cache относительно `dev`

## Проблема

После KMP migration инфраструктура хранения не исчезла целиком, но часть экранов потеряла `local-first` или `cached initial` поведение относительно ветки `dev`.

Нельзя больше думать так:
- `Room есть` -> значит cache parity сохранен

Надо думать так:
- `storage layer жив`
- `DI layer жив`
- `feature flow реально использует local data как раньше или нет`

Итоговая оценка состояния по cache parity сейчас: `6.8/10`.

## Что точно живо

### Room и local stores не выпилены

Подтверждено:
- [databaseModule.kt](/Users/bogdan/Flow%20V1/Mobile/app/src/main/java/me/floow/app/di/databaseModule.kt) всё ещё поднимает `Room.databaseBuilder(..., "flowme.db")`
- `AppDatabase`, DAO и local stores всё ещё регистрируются
- живы:
  - `ProfileLocalStore`
  - `PostsLocalStore`
  - `FeedSyncLocalStore`
  - `RepliesInboxLocalStore`
  - `DirectChatsLocalStore`

Оценка: `8.5/10`

### Data layer для chats/replies всё ещё умеет работать с локалкой

Подтверждено:
- [ChatsRepositoryImpl.kt](/Users/bogdan/Flow%20V1/Mobile/core/data/src/main/java/me/floow/data/repos/ChatsRepositoryImpl.kt) всё ещё использует `DirectChatsLocalStore`
- там остались:
  - `observeConversations()`
  - `observeMessages()`
  - fallback в local store
  - local anchored window
- [NotificationsRealtimeRepositoryImpl.kt](/Users/bogdan/Flow%20V1/Mobile/core/data/src/main/java/me/floow/data/repos/NotificationsRealtimeRepositoryImpl.kt) всё ещё использует `RepliesInboxLocalStore`

Оценка: `8/10`

## Подтвержденные регрессии

### 1. Direct Chat потерял cached initial после shared migration

Статус: `подтверждено`

Проблема:
- shared [DirectChatStateHolder.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/uilogic/direct/DirectChatStateHolder.kt) ожидает `loadCachedInitial(...)`
- Android adapter [AndroidChatThreadRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatThreadRepository.kt) раньше не реализовывал этот метод
- в итоге shared chat стартовал без локального снапшота, хотя `DirectChatsLocalStore` под ним жил

Что изменено:
- в [AndroidChatThreadRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatThreadRepository.kt) добавлен `loadCachedInitial(...)`
- cached snapshot теперь собирается из:
  - `observeConversations().first()`
  - `observeMessages(...).first()`
  - `getAnchoredMessagesWindow(...)` для anchor/message-link сценария

Проверка:
- `:feature:chats:compileDebugKotlin` — `OK`
- `:app:compileProductionDebugKotlin` — `OK`

Оценка проблемы до фикса: `8/10`
Оценка после фикса: `8.7/10`

### 2. Replies потеряли часть persisted read/open-anchor semantics

Статус: `подтверждено`

На `dev`:
- старый [RepliesOverlayViewModel.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/replies/RepliesOverlayViewModel.kt) держал:
  - `NotificationsReadCursorStore`
  - local read cursor
  - open anchor pipeline
  - overlay read pipeline

Сейчас:
- shared [RepliesStateHolder.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/uilogic/replies/RepliesStateHolder.kt) сильно проще
- [AndroidRepliesRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/replies/AndroidRepliesRepository.kt) отдаёт данные через realtime repo
- old-style persisted `last seen / anchor / local read cursor` parity не дожата

Что это значит:
- storage не исчез
- UI/runtime semantics стали слабее

Оценка: `7.5/10`

### 3. Profile потерял old-style local-first seed

Статус: `подтверждено`

На `dev`:
- старый [ProfileScreenViewModel.kt](/Users/bogdan/Flow%20V1/Mobile/feature/profile/src/main/java/me/floow/profile/uilogic/profile/ProfileScreenViewModel.kt) использовал:
  - `ProfileLocalStore`
  - `PostsLocalStore`
  - `UsernameToIdCache`
- экран seed’ился из локалки до сети

Сейчас:
- путь идёт через [ProfileRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/profile/src/main/java/me/floow/profile/ui/profile/ProfileRoute.kt) и shared [ProfileStateHolder.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/profile/uilogic/ProfileStateHolder.kt)
- Android adapter [AndroidProfileRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/androidMain/kotlin/me/floow/shared/profile/uilogic/AndroidProfileRepository.kt) не возвращает старый local-first сценарий
- current shared profile flow выглядит заметно более network-first

Что это значит:
- архитектура лучше
- cache parity хуже

Оценка:
- архитектура: `8.5/10`
- cache parity относительно `dev`: `6/10`

### 4. Edit Profile / Edit Post потеряли часть local sync story

Статус: `подтверждено частично`

На `dev`:
- edit flows плотнее работали с local stores

Сейчас:
- shared [EditProfileStateHolder.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/profile/uilogic/edit/EditProfileStateHolder.kt)
- shared [EditPostStateHolder.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/profile/uilogic/addpost/EditPostStateHolder.kt)
- local cache sync story выглядит слабее, чем на старом Android-only flow

Это пока не равно “сломано”, но это уже weaker persistence model.

Оценка: `6.5/10`

## Экраны без доказанного cache regression

### Feed

Статус: `явного регресса не доказано`

Почему:
- shared [SharedFeedStateHolder.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/feed/uilogic/SharedFeedStateHolder.kt) по сути портирует старую модель
- там всё ещё используются:
  - `FeedSyncLocalStore`
  - `PostsLocalStore`
  - `ProfileLocalStore`

Вывод:
- storage и sync path в feed живы
- если есть пользовательский баг, его надо доказывать отдельным сценарием, не общими словами

Оценка риска: `3/10`

### Chats List

Статус: `сильного cache regression не доказано`

Почему:
- [AndroidChatsListRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chats/AndroidChatsListRepository.kt) сидит на:
  - `observeConversations()`
  - `NotificationsRealtimeRepository`
  - `PresenceRepository`
- chats list по-прежнему питается от старого Android repo/local-store контура

Оценка риска: `4/10`

### Login

Статус: `не про local cache`

Проблема там была не storage, а lifecycle/OAuth:
- [MainActivity.kt](/Users/bogdan/Flow%20V1/Mobile/app/src/main/java/me/floow/app/MainActivity.kt) не обрабатывал Google OAuth code на cold start

Это уже починено.

Оценка cache-риска: `2/10`

### Comments

Статус: `прямого доказательства нет`

Пока нет подтверждения, что comments потеряли local-first поведение именно относительно `dev`.

Оценка риска: `4/10`

### Explore

Статус: `не главный storage-sensitive экран`

Оценка риска: `2/10`

### ChatsSearch

Статус: `не доказано`

Оценка риска: `3/10`

### Post View

Статус: `не доказано`

Есть shared migration, но явный cache regression именно по post screen пока не подтвержден.

Оценка риска: `4/10`

## Приоритет задач

### P0

#### Вернуть full local-first parity для Profile

Почему:
- это самый чувствительный пользовательский экран после chats
- на `dev` профиль имел понятный cached seed
- сейчас shared migration сделала его чище, но слабее по ощущениям

Что делать:
- проверить и восстановить cached initial profile/posts seed
- отдельно проверить edit-profile local sync
- отдельно проверить edit-post local sync

Оценка важности: `9/10`

### P1

#### Добить Replies persisted behavior

Почему:
- storage жив
- runtime semantics урезаны

Что делать:
- вернуть local read/open-anchor semantics
- не откатывать shared replies назад
- дожать Android adapter или shared state holder

Оценка важности: `8/10`

### P1

#### Закрыть emoji regression в direct chat

Почему:
- это уже не cache, а shared UI regression
- panel реально отвалилась после migration

Что уже сделано:
- в dirty tree уже лежит wiring emoji panel через:
  - [ChatRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/ChatRoute.kt)
  - [SharedDirectChatRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/ui/SharedDirectChatRoute.kt)
  - [SharedDirectChatScreen.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/ui/SharedDirectChatScreen.kt)

Оценка важности: `8/10`

### P2

#### Перепройти cache parity по Comments / Post / ChatsSearch только если есть конкретные баг-репорты

Почему:
- сейчас нет доказанного общего regression story
- лезть туда без симптомов тупо

Оценка важности: `5/10`

## Что сейчас лежит в dirty tree

Подтвержденные незакоммиченные изменения:
- [MainActivity.kt](/Users/bogdan/Flow%20V1/Mobile/app/src/main/java/me/floow/app/MainActivity.kt)
- [ChatRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/ChatRoute.kt)
- [chatsModule.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/di/chatsModule.kt)
- [AndroidChatThreadRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatThreadRepository.kt)
- [SharedDirectChatRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/ui/SharedDirectChatRoute.kt)
- [SharedDirectChatScreen.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/ui/SharedDirectChatScreen.kt)

## Как это коммитить

### Нельзя делать один commit

Почему:
- там 3 разные истории
- login fix не относится к emoji
- emoji fix не относится к local-first cache fix
- DI fix в chats относится к baseline, а не к UI

Оценка одного общего commit: `2/10`

### Правильная разбивка на коммиты

#### Commit 1. Login hotfix

Смысл:
- cold-start Google OAuth handling

Файл:
- [MainActivity.kt](/Users/bogdan/Flow%20V1/Mobile/app/src/main/java/me/floow/app/MainActivity.kt)

Message:
- `login: handle google oauth redirect on cold start`

Оценка: `9/10`

#### Commit 2. Chats baseline / DI hotfix

Смысл:
- восстановление корректного DI для shared-era chats

Файл:
- [chatsModule.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/di/chatsModule.kt)

Message:
- `chats: fix shared repository wiring in android module`

Оценка: `8.5/10`

#### Commit 3. Chats cache parity hotfix

Смысл:
- вернуть cached initial поведение для direct chat

Файл:
- [AndroidChatThreadRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatThreadRepository.kt)

Message:
- `chats: restore cached initial snapshot for shared direct chat`

Оценка: `9.5/10`

#### Commit 4. Chats emoji UI hotfix

Смысл:
- вернуть emoji panel в shared direct chat path

Файлы:
- [ChatRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/ChatRoute.kt)
- [SharedDirectChatRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/ui/SharedDirectChatRoute.kt)
- [SharedDirectChatScreen.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/ui/SharedDirectChatScreen.kt)

Message:
- `chats: restore emoji panel in shared direct chat`

Оценка: `9/10`

## Как работать дальше

### Если продолжаешь чинить cache regressions

Порядок:
1. `profile cached initial parity`
2. `replies persisted cursor/open-anchor parity`
3. `only then` любые вторичные cache audits

### Если переключаешься на новую фичу

Порядок:
1. разрезать текущий dirty tree на 4 коммита выше
2. убедиться, что `dev-alpha` зелёная
3. новую фичу ветвить уже от `dev-alpha`

## Вывод

Главная правда сейчас такая:
- `Room` не умер
- `cache story` после KMP migration просела не везде, а выборочно
- наиболее доказанные regressions были в:
  - `Direct Chat`
  - `Replies`
  - `Profile`
- `Direct Chat` уже частично поправлен
- следующий реальный долг по cache parity: `Profile`

Общая оценка:
- storage infrastructure: `8.5/10`
- cache parity с `dev`: `6.8/10`
- чистота текущего набора правок после нормальной нарезки на коммиты: `9/10`
