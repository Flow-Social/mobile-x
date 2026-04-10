# Cache / Local-First Audit Relative to `dev`

Дата: 2026-04-10  
База сравнения: `dev` -> `dev-alpha`  
Скоуп: `feed`, `login`, `post`, `comments`, `explore`, `chatssearch`

## Короткий вывод

Проблема:
- явный cache/local-first регресс по этим экранам не тотальный
- самый грязный регресс уже был не тут, а в `chats` / частично `profile`
- из этого списка сильного провала не видно

После анализа:
- `feed` и `post` выглядят сохранёнными по local-store path
- `comments` не имели полноценного Room-backed local-first timeline ни на `dev`, ни сейчас, но local persisted read-cursor path жив
- `login`, `explore`, `chatssearch` не были экранами с тяжёлым Room/local-first сценарием и ими не стали

Общая оценка по этому набору экранов: `8/10`

## Audit

| Экран | На `dev` был local store / cache path | На `dev-alpha` есть сейчас | Риск регрессии | Короткий вывод |
|---|---|---|---:|---|
| `feed` | Да. `FeedViewModel` сидел на `FeedSyncLocalStore`, `PostsLocalStore`, `ProfileLocalStore` | Да. `SharedFeedStateHolder` всё ещё держит те же `FeedSyncLocalStore`, `PostsLocalStore`, `ProfileLocalStore` | `2/10` | Сильного регресса не видно. Shared migration по кэшу здесь выглядит почти паритетно |
| `login` | Нет явного screen-level local-first cache path. Экран жил от `AuthenticationManager` state | Нет явного screen-level cache path. Сейчас живёт от shared `AuthRepository` / auth state | `3/10` | Это не экран про Room/local-first контент. Регрессии кэша почти неоткуда взяться, проблемы тут скорее в OAuth lifecycle, а не в local store |
| `post` | Да, локальные path были. `PostRoute` использовал `PostsLocalStore`, `ProfileLocalStore`, `PostMediaTransferStore` | Да, те же зависимости всё ещё прокидываются в `SharedPostRoute` | `2/10` | Явного провала по local/cache path не видно. Пост-экран остаётся завязан на локальные stores |
| `comments` | Частично. Полноценного local timeline store в feature не было, но был persisted path через `CommentsReadCursorStore` | Частично. `CommentsStateHolder` всё ещё использует `CommentsReadCursorStore` | `4/10` | Полного local-first comments feed не было и на `dev`. Но persisted read-cursor path сохранился, значит тотальной деградации нет |
| `explore` | Нет. Это был почти чистый UI route без local store/cache слоя | Нет | `1/10` | Регрессировать по кэшу тут почти нечему |
| `chatssearch` | Только in-memory query cache внутри `SearchUsersScreenViewModel`, без Room/local store | То же самое: in-memory query cache внутри `SearchUsersStateHolder` | `2/10` | Поведение почти одинаковое. Это не persisted local-first экран, а transient search cache |

## По экранам чуть подробнее

### `feed`

Проблема: если бы тут кэш отвалился, это было бы видно сразу, потому что старый `FeedViewModel` реально зависел от локальных stores.

Что на `dev`:
- `FeedViewModel` использовал `FeedSyncLocalStore`
- `FeedViewModel` использовал `PostsLocalStore`
- `FeedViewModel` использовал `ProfileLocalStore`

Что на `dev-alpha`:
- `SharedFeedStateHolder` использует `FeedSyncLocalStore`
- `SharedFeedStateHolder` использует `PostsLocalStore`
- `SharedFeedStateHolder` использует `ProfileLocalStore`

Оценка: `8.5/10`

Вывод:
- по `feed` сильного cache/local-first regressions не видно
- тут shared migration выглядит аккуратнее, чем в `chats` и `profile`

### `login`

Проблема: тут легко придумать “кэш умер”, но у login-экрана и раньше не было нормального local-first content cache слоя.

Что на `dev`:
- экран жил от `AuthenticationManager.authenticationStateFlow`
- отдельного `ProfileLocalStore`/`PostsLocalStore` уровня экрана не было

Что на `dev-alpha`:
- экран живёт от shared `AuthRepository`
- Android auth path использует persistent auth infra, но это auth persistence, а не screen-level local-first cache

Оценка: `7/10`

Вывод:
- screen-level cache regression тут не главный риск
- login ломается не из-за кэша, а из-за auth wiring/lifecycle

### `post`

Проблема: если бы KMP migration вырезала local stores, post-screen быстро потерял бы local consistency после delete/update и media handoff.

Что на `dev`:
- `PostRoute` использовал `PostsLocalStore`
- `PostRoute` использовал `ProfileLocalStore`
- `PostRoute` использовал `PostMediaTransferStore`

Что на `dev-alpha`:
- `PostRoute` всё ещё инжектит `PostsLocalStore`
- `PostRoute` всё ещё инжектит `ProfileLocalStore`
- `PostRoute` всё ещё инжектит `PostMediaTransferStore`
- всё это прокидывается в `SharedPostRoute`

Оценка: `8.5/10`

Вывод:
- по `post` local/cache path сохранился
- явной регрессии относительно `dev` не видно

### `comments`

Проблема: по ощущениям comments могли выглядеть “хуже”, но надо не путать это с исчезновением Room-backed local timeline.

Что на `dev`:
- `CommentsViewModel` использовал `CommentsReadCursorStore`
- это persisted local path для read cursor
- отдельного полноценного Room-backed local comments timeline на уровне feature не видно

Что на `dev-alpha`:
- `CommentsStateHolder` всё ещё использует `CommentsReadCursorStore`
- shared route сохранил этот local persisted path

Оценка: `6.5/10`

Вывод:
- comments не выглядят как экран, где local-first timeline пропал именно из-за migration
- persisted read state сохранился
- если тут и есть регресс, он скорее не такой тяжёлый, как в `chats`

### `explore`

Проблема: её почти нет.

Что на `dev`:
- почти чистый UI route
- local store/cache path не видно

Что на `dev-alpha`:
- то же самое, просто shared UI route

Оценка: `9/10`

Вывод:
- кэш тут почти нечему терять
- migration безопасная

### `chatssearch`

Проблема: можно спутать query cache с persisted local-first storage, но это не одно и то же.

Что на `dev`:
- был in-memory LRU-like query cache в `SearchUsersScreenViewModel`
- persisted store/Room path на экран не завязан

Что на `dev-alpha`:
- тот же in-memory query cache живёт в `SearchUsersStateHolder`
- persisted local store path по-прежнему нет

Оценка: `8/10`

Вывод:
- для `chatssearch` behaviour почти тот же
- это transient search cache, не local-first storage story

## Приоритет задач после этого аудита

Проблема:
- не надо распыляться и чинить эти экраны “на всякий случай”
- из этого списка нет экрана с таким же явным cache regression, как был в `chats`

Нормальный порядок:
1. `profile` local-first parity
2. `replies` persisted read/anchor semantics
3. только потом добивать спорные или edge-case regressions в `comments` / `post`, если появится конкретный сценарий
4. `feed`, `explore`, `chatssearch`, `login` по cache/local-first сейчас не трогать без нового доказанного бага

Оценка приоритизации: `9/10`

## Как это коммитить

Проблема:
- такие фиксы нельзя мешать в один giant commit “cache fixes”
- иначе потом хер поймёшь, что реально починило поведение

Нормальная схема:
1. один регресс = одна ветка или один отдельный подслой в ветке
2. один экранный фикс = один commit, если изменение компактное
3. если экран требует нескольких шагов, резать так:
   - `state/repository contract`
   - `android adapter/local store wiring`
   - `ui/route integration`
   - `tests`, если есть

Пример нормальных commit messages:
- `chats: restore cached initial load from local store`
- `profile: restore local-first seed for shared profile route`
- `replies: bring back persisted read cursor semantics`

Что плохо:
- `fix cache`
- `kmp fixes`
- `chat/profile/comments cleanup`

Что лучше:
- узкий commit с одной причиной и одним эффектом

Оценка стратегии коммитов: `9/10`
