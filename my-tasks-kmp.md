# KMP Tasks Progress

## Completed Tasks

### Task 1: Зачистка фейковых заглушек (Garbage Collection) ✅
- [x] Удалён `feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/FeedScreen.kt` — заглушка с `Text("Лента")`
- [x] Удалён `feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/FlowStarAvatar.kt` — нигде не используется
- [x] Удалён `feature/shared/src/commonMain/kotlin/me/floow/shared/home/ui/HomeScreen.kt` + вся директория `home/` — заглушка с `Text("Flow Home")`

### Task 2a: Зачистка мёртвых feed components (Android) ✅
- [x] Удалена директория `feature/feed/src/main/java/me/floow/feed/ui/components/` — старые Android View-компоненты (SwipeablePostCard, PostCard, AnalysisDebugCard, AnalysisToast, SwipeButtons, ImageOverlayGrid, UserProfileInfoBlock, AvatarStack и др.)
- [x] Удалена директория `feature/feed/src/main/java/me/floow/feed/models/` — RecommendationReason, SwipeInfo
- [x] `FeedRoute.kt` оставлен — это тонкий адаптер к SharedFeedRoute

### Task 2b: Зачистка мёртвых chats файлов (Android) ✅
Удалено 19 файлов:
- [x] `ui/chats/ChatsScreen.kt` — заменён на SharedChatsScreen
- [x] `ui/chats/components/ChatListItem.kt` — заменён shared UI adapters
- [x] `ui/chats/components/ChatsList.kt` — заменён SharedChatsScreen
- [x] `ui/chats/states/HasDataState.kt` — использовался только в ChatsScreenViewModel
- [x] `uilogic/chats/ChatsScreenViewModel.kt` — заменён ChatsListStateHolder
- [x] `uilogic/chats/ChatsScreenUiState.kt` — использовался только в ChatsScreenViewModel
- [x] `uilogic/chats/generateRandomChats.kt` — debug генератор
- [x] `uilogic/chat/ChatScreenViewModel.kt` — заменён DirectChatStateHolder
- [x] `uilogic/chat/ChatScreenViewModelTimeline.kt` — хелпер для ChatScreenViewModel
- [x] `uilogic/chat/ChatScrollAnchorController.kt` — использовался только в ChatScreenViewModel
- [x] `uilogic/chat/ChatTypingController.kt` — использовался только в ChatScreenViewModel
- [x] `uilogic/chat/ChatOutgoingController.kt` — использовался только в ChatScreenViewModel
- [x] `uilogic/chat/ChatReadController.kt` — использовался только в ChatScreenViewModel
- [x] `uilogic/chat/generateChatMessages.kt` — debug генератор
- [x] `uilogic/replies/RepliesOverlayViewModel.kt` — заменён RepliesStateHolder
- [x] `uilogic/shared/PeerReadHelpers.kt` — дубликат shared версии

**Сохранены** (используются в FlowNavHost или shared модуле):
- `ChatsRoute.kt` — тонкий адаптер, импортируется из FlowNavHost
- `ChatRoute.kt` — тонкий адаптер, импортируется из FlowNavHost
- `RepliesOverlayRoute.kt` — используется в FlowNavHost
- `uilogic/chat/DirectChatOpenMode.kt` — используется в FlowNavHost
- `uilogic/replies/RepliesOverlayOpenMode.kt` — используется в FlowNavHost
- `uilogic/chats/Chat.kt` — data model, используется контрактами
- `uilogic/chats/SavedMessagesChatContract.kt` — extension isSavedMessages() используется в FlowNavHost
- `uilogic/chats/RepliesInboxChatContract.kt` — extension isRepliesInboxChat() используется в FlowNavHost
- `uilogic/shared/ReadAnchorUseCases.kt` — импортируется из SharedDirectChatRoute
- `uilogic/shared/TypingStateHelper.kt` — импортируется из SharedDirectChatRoute
- `uilogic/shared/UriSanitizer.kt` — импортируется из SharedDirectChatRoute
- `di/chatsModule.kt` — DI регистрация

### Task 2c: Зачистка мёртвых profile файлов (Android) ✅
Удалено 13 файлов:
- [x] `uilogic/addpost/LocalImageFileReader.kt` — дубликат shared версии
- [x] `uilogic/profile/ProfileScreenState.kt` — заменён shared версией
- [x] `ui/profile/ProfileActionContextAndroid.kt` — мост к удалённому ProfileScreenState
- [x] `uilogic/edit/EditProfileState.kt` — заменён shared версией
- [x] `ui/edit/EditState.kt` — старый композабл, заменён SharedEditProfileFormContent
- [x] `ui/profile/ProfileImageVariants.kt` — internal, дубликат shared resolveProfileListUrls
- [x] `ui/profile/LikesLabel.kt` — тонкая обёртка, нигде не импортируется
- [x] `ui/profile/ProfileScreenTopBar.kt` — тонкая обёртка, нигде не импортируется
- [x] `ui/profile/segments/summary/AboutMeProfileSummaryPage.kt` — тонкая обёртка
- [x] `ui/profile/segments/summary/AvatarUsernameProfileSummaryPage.kt` — тонкая обёртка
- [x] `ui/profile/segments/summary/ProfileSummarySegment.kt` — тонкая обёртка
- [x] `ui/profile/segments/content/ProfileContentSegment.kt` — старая реализация, заменена shared
- [x] `ui/profile/segments/buttons/ProfileButtonsSegment.kt` — старая реализация, заменена shared
- [x] `ui/profile/components/ProfilePostCard.kt` — использовался только в ProfileContentSegment
- [x] `ui/addpost/CreatePostOverlayKeyboardCoordinator.kt` — дубликат shared версии
- [x] `ui/addpost/CreatePostOverlayTokens.kt` — дубликат shared версии

**Сохранены** (активно используются):
- `ProfileRoute.kt` — тяжёлый Android-адаптер (3 вызова из FlowNavHost)
- `ProfileScreen.kt` — state dispatcher, используется ProfileRoute
- `ProfileScreenSuccessState.kt` — bump sheet UI + BLE wiring, используется ProfileScreen
- `EditProfileRoute.kt` — адаптер с AndroidSingleImagePicker
- `EditProfileScreen.kt` — используется EditProfileRoute
- `EditProfileBottomSheet.kt` — используется ProfileRoute
- `CreatePostOverlayRoute.kt` — адаптер, вызывается из FlowNavHost
- `EditPostOverlayRoute.kt` — адаптер, вызывается из FlowNavHost
- `CreatePostOverlayScreen.kt` — используется обоими route
- Bump feature (все файлы) — Android-specific BLE/accelerometer
- `AddPostViewModel.kt` — нужен для mock-build пути
- `di/profileModule.kt` — DI регистрация

### Task 2d: Cleanup FlowNavHost imports ✅
- [x] Удалён unused import `me.floow.profile.uilogic.profile.ProfileScreenState` из FlowNavHost.kt

## Pending Tasks

### Task 3: Shell Policy Unification — выровнять FlowNavHost и MainFlowShell ✅
- [x] Установить общий механизм навигации для диалогов и оверлеев (добавлен `SharedShellOverlay`)
- [x] Избавиться от хардкодных заглушек в вебе
- [x] Настроить каноничное (Android-like) закрытие диалогов и работу бэк-стека для веба (`overlayStack` в MainFlowShell)

### Task 4: Final Parity Hardening ✅
- [x] Выровнять read cursor синхронизацию (добавлен `WasmDirectMessagesReadCursorStore`, синхронизирован `openMode`)
- [x] Упаковать Share Intents в expect/actual функции (добавлен `systemShareText`)
