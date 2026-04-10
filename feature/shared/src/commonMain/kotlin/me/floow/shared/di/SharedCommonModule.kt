package me.floow.shared.di

import me.floow.chatssearch.uilogic.SearchUsersStateHolder
import me.floow.comments.uilogic.CommentsStateHolder
import me.floow.shared.chats.uilogic.ChatsListStateHolder
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.shared.chats.uilogic.replies.RepliesStateHolder
import me.floow.shared.feed.uilogic.SharedFeedStateHolder
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache
import me.floow.shared.login.uilogic.LoginViewModel
import me.floow.shared.login.uilogic.createprofile.CreateProfileRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileStateHolder
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.addpost.CreatePostStateHolder
import me.floow.shared.profile.uilogic.addpost.EditPostStateHolder
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileStateHolder
import me.floow.shared.profile.uilogic.ProfileStateHolder
import org.koin.dsl.module

val sharedCommonModule = module {
    single { SharedChatSessionCache() }
    factory { LoginViewModel(get()) }
    factory { CreateProfileStateHolder(get<CreateProfileRepository>()) }
    factory { (userId: String?) -> ProfileStateHolder(get(), userId) }
    factory { ChatsListStateHolder(get()) }
    factory { SearchUsersStateHolder(get()) }
    factory { SharedFeedStateHolder(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CommentsStateHolder(get(), get(), get(), get()) }
    factory { RepliesStateHolder(get()) }
    factory { (chatRealtimeContract: ChatRealtimeContract?, chatPresenceContract: ChatPresenceContract?) ->
        DirectChatStateHolder(
            repository = get(),
            realtimeContract = chatRealtimeContract,
            presenceContract = chatPresenceContract,
        )
    }
    factory { (imagePicker: PlatformImagePicker) ->
        CreatePostStateHolder(get(), imagePicker, get())
    }
    factory { (initialPost: ProfilePostItem, imagePicker: PlatformImagePicker) ->
        EditPostStateHolder(initialPost, get(), imagePicker, get())
    }
    factory { (initialData: EditProfileOverlayData, imagePicker: PlatformImagePicker) ->
        EditProfileStateHolder(
            initialData = initialData,
            repository = get(),
            imagePicker = imagePicker,
            imageFileReader = get(),
            uploadsRepository = get(),
        )
    }
}
