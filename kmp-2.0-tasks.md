# План зачистки легаси (KMP 2.0 Tasks)

## 1. Зачистка фейковых заглушек (Garbage Collection)
Убрать старые попытки переноса UI, которые теперь заменены полноценными `SharedRoute`.
- **[DELETE]** `feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/FeedScreen.kt` — удалить файл с текстовой заглушкой `Text("Лента")`. Реальный код уже живет в `me/floow/shared/feed/ui/SharedFeedRoute.kt`.
- **[MODIFY]** Проверить остальные файлы в `feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/` (например, пустые профили) и снести их, если дублируют реальные.

## 2. Зачистка старых Android-features (`src/main/java`)
Истончить `src/main` слои в фичах. Большая часть логики уже уехала в общие холдеры.
- **[MODIFY] `feature/feed/src/main/java/`**:
  - Удалить или спрятать под дебаг-флаги отладочный мусор вроде `AnalysisDebugCard.kt`.
  - Выкосить старые Android View-компоненты (карточки постов, свайпы), если они на 100% заменены в `SharedFeedRoute`.
  - Сжать `FeedRoute.kt` исключительно до роли тонкого хоста (DI, передача системных пермишшенов, Lifecycle).
- **[MODIFY] `feature/profile`, `feature/explore`, `feature/chats`**:
  - Вырезать старые ViewModel-и (типа legacy `ChatsScreenViewModel`), если они больше не участвуют в production runtime (согласно текущим статусам миграции).
  - Удостовериться, что в роутах остались только `koinInject`, привязка к `LocalLifecycleOwner` и коллбеки навигации. Никаких state mutations.

## 3. Shell Policy Unification (Роутинг и Оверлеи)
Выровнять `FlowNavHost` (Android) и `MainFlowShell` (Web/Wasm), следуя канону Android.
- **[MODIFY] `MainFlowShell.kt` и `FlowNavHost.kt`**:
  - Установить общий механизм навигации для диалогов и оверлеев. Избавиться от хардкодных заглушек в вебе (когда вместо открытия окна кидался снекбар).
  - Настроить каноничное (Android-like) закрытие диалогов и работу бэк-стека для веба.

## 4. Final Parity Hardening
Вычистить остаточные расхождения.
- Выровнять read cursor синхронизацию, чтобы бейджи на кнопках обновлялись одновременно.
- Упаковать оставшиеся вызовы Share Intents в удобные платформонезависимые expect/actual функции.
