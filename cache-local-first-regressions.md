# Cache / Local-First Regressions vs `dev`

Короткий аудит по экранам `chats`, `replies`, `profile` относительно ветки `dev`.

Цель файла:
- зафиксировать только фактические regressions по cache/local-first
- отделить доказанные поломки от ослабленного поведения
- не смешивать это с KMP migration narrative целиком

## Summary

- `chats`: есть доказанный regression. Local storage не умер, но cached-first старт shared chat flow был сломан.
- `replies`: полного обвала не видно, но persisted read/open-anchor semantics стали слабее.
- `profile`: Room/local stores живы, но old-style local-first seed и часть local sync поведения уже не дотянуты до parity с `dev`.

## Chats

Оценка состояния: `сломано 8/10`, после фикса adapter path `лучше 8.5/10`

### Что точно было сломано

- Shared [DirectChatStateHolder](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/uilogic/direct/DirectChatStateHolder.kt) ожидает `loadCachedInitial(...)`.
- Android adapter [AndroidChatThreadRepository](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatThreadRepository.kt) раньше не реализовывал этот путь.
- В интерфейсе [ChatThreadRepository](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/uilogic/direct/ChatThreadRepository.kt) дефолтный `loadCachedInitial(...)` возвращал `null`.
- Итог: shared direct chat поднимался без cached initial snapshot, хотя нижний storage слой на Android оставался жив.

### Что только ослаблено

- Cached-first поведение зависело от adapter glue, а не от shared owner слоя.
- История и conversation могли дотягиваться уже после сети, а не стартовать из локалки как на `dev`.

### Что осталось живо

- Room и локальные stores не удалены.
- [ChatsRepositoryImpl](/Users/bogdan/Flow%20V1/Mobile/core/data/src/main/java/me/floow/data/repos/ChatsRepositoryImpl.kt) всё ещё работает через local store path.
- `observeConversations()` и `observeMessages()` внизу остались.

### Вывод

По `chats` regression был реальный и технически доказанный: не исчезла база, а пропал cached-first adapter path.

## Replies

Оценка состояния: `ослаблено 7/10`

### Что точно сломано

- Полного доказательства, что весь replies cache умер, нет.
- Явного аналога старого persisted read cursor flow в новом shared replies path не видно.

### Что только ослаблено

- На `dev` старый [RepliesOverlayViewModel](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/replies/RepliesOverlayViewModel.kt) опирался на local read cursor / open anchor semantics.
- Новый shared [RepliesStateHolder](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/chats/uilogic/replies/RepliesStateHolder.kt) заметно проще.
- В новом Android adapter [AndroidRepliesRepository](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/replies/AndroidRepliesRepository.kt) нет признаков полного возврата старой local-first логики overlay/read-state.

### Что осталось живо

- Room как инфраструктура на Android не исчез.
- Replies-related local storage в проекте не вырезан как класс.
- Базовый data path жив, но UX/state semantics стали беднее.

### Вывод

По `replies` честная формулировка такая: не тотальный cache-loss, а деградация persisted поведения относительно `dev`.

## Profile

Оценка состояния: `ослаблено 7/10`

### Что точно сломано

- На `dev` профиль seed'ился из локалки через старый [ProfileScreenViewModel](/Users/bogdan/Flow%20V1/Mobile/feature/profile/src/main/java/me/floow/profile/uilogic/profile/ProfileScreenViewModel.kt): `ProfileLocalStore`, `PostsLocalStore`, `UsernameToIdCache`.
- На `dev-alpha` новый flow идёт через shared [ProfileStateHolder](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/profile/uilogic/ProfileStateHolder.kt) и Android adapter [AndroidProfileRepository](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/androidMain/kotlin/me/floow/shared/profile/uilogic/AndroidProfileRepository.kt).
- В этом новом пути old-style cached initial profile/posts seed уже не восстановлен.

### Что только ослаблено

- Edit profile и edit post теперь живут на shared state holders, но local sync story слабее прежней.
- [ProfileRepositoryImpl](/Users/bogdan/Flow%20V1/Mobile/core/data/src/commonMain/kotlin/me/floow/data/repos/ProfileRepositoryImpl.kt) и [PostsRepositoryImpl](/Users/bogdan/Flow%20V1/Mobile/core/data/src/commonMain/kotlin/me/floow/data/repos/PostsRepositoryImpl.kt) не выглядят как old-style local-first защита для profile screen.
- После migration архитектура стала чище, но parity со старым local-first профилем не дотянута.

### Что осталось живо

- Room/global local stores не умерли.
- [ProfileRoute](/Users/bogdan/Flow%20V1/Mobile/feature/profile/src/main/java/me/floow/profile/ui/profile/ProfileRoute.kt) и shared migration в целом архитектурно лучше.
- Инфраструктура локального хранения в проекте есть, проблема именно в новом adapter/repository path.

### Вывод

По `profile` это не обнуление cache, а потеря части old-style local-first поведения при переходе на shared owner flow.

## Итоговая сводка

- `chats`
  - точно сломано: cached initial direct chat path
  - ослаблено: local-first startup UX
  - живо: Room/local stores/data observers
- `replies`
  - точно сломано: не доказано как полный cache-loss
  - ослаблено: read cursor / anchor / persisted overlay semantics
  - живо: базовый Android storage/data path
- `profile`
  - точно сломано: old-style cached initial profile/posts seed
  - ослаблено: edit-profile/edit-post local sync parity
  - живо: Room/local stores и сама shared migration архитектура

## Что коммитить и как

Этот файл должен идти отдельным docs-коммитом.

Почему:
- в worktree уже есть runtime fixes по login/chats/emoji
- audit не должен смешиваться с functional hotfix
- иначе потом невозможно понять, где документ, а где реальная починка

Нормальный commit для этого файла:

```text
docs: add cache and local-first regression audit vs dev
```

Ненормально:
- мешать этот md в commit с runtime fixes
- править код и аудит одним commit
- использовать этот файл как замену bugfix-PR
