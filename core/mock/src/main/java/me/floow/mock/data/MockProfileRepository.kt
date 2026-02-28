package me.floow.mock.data
 
 import me.floow.domain.api.models.EditProfileData
 import me.floow.domain.data.GetDataResponse
 import me.floow.domain.data.UpdateDataResponse
 import me.floow.domain.data.repos.ProfileRepository
 import me.floow.domain.models.SelfProfile
 import me.floow.domain.values.ProfileDescription
 import me.floow.domain.values.ProfileName
 import me.floow.domain.values.ProfileUsername
 import me.floow.domain.values.util.RawValueObjectCreate
 import me.floow.mock.interfaces.MockStorage
 
 import me.floow.domain.models.PublicProfile

class MockProfileRepository(
	private val mockStorage: MockStorage
) : ProfileRepository {
	@OptIn(RawValueObjectCreate::class)
	private var userProfile: SelfProfile?
		get() {
			val name = mockStorage.getString("profile_name") ?: return null
			val username = mockStorage.getString("profile_username") ?: "demndevel"
			val description = mockStorage.getString("profile_description")
				?: "Hi. My name is demn. I like coding: Kotlin, F#, Jetpack Compose, SwiftUI and etc. Welcome to my profile screen!"
			val avatarUrl = mockStorage.getString("profile_avatar")
				?: "https://http.cat/images/200.jpg"
			val backgroundUrl = mockStorage.getString("profile_background")

			return SelfProfile(
				name = ProfileName.createRaw(name),
				avatarUrl = avatarUrl,
				backgroundUrl = backgroundUrl,
				backgroundUpdatedAt = null,
				username = ProfileUsername.createRaw(username),
				description = ProfileDescription.createRaw(description)
			)
		}
		set(value) {
			value?.name?.value?.let { mockStorage.saveString("profile_name", it) }
			value?.username?.value?.let { mockStorage.saveString("profile_username", it) }
			value?.description?.value?.let { mockStorage.saveString("profile_description", it) }
			value?.avatarUrl?.let { mockStorage.saveString("profile_avatar", it) }
			value?.backgroundUrl?.let { mockStorage.saveString("profile_background", it) }
		}

	override suspend fun getSelfData(): GetDataResponse<SelfProfile> {
       val profile = userProfile
		return if (profile != null) {
           GetDataResponse.Success(data = profile)
       } else {
           GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)
       }
	}

	@OptIn(RawValueObjectCreate::class)
	override suspend fun getUserProfile(userId: String): GetDataResponse<PublicProfile> {
		// Mock logic: treat "me" or "self" as current user
		if (userId == "me" || userId == "self") {
			val self = userProfile ?: return GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)
			return GetDataResponse.Success(
				PublicProfile(
					id = userId,
					name = self.name,
					username = self.username,
					avatarUrl = self.avatarUrl,
					backgroundUrl = self.backgroundUrl,
					backgroundUpdatedAt = self.backgroundUpdatedAt,
					description = self.description
				)
			)
		}

		return GetDataResponse.Success(
			PublicProfile(
				id = userId,
				name = ProfileName.createRaw("User $userId"),
				username = ProfileUsername.createRaw("user_$userId"),
				avatarUrl = "https://robohash.org/$userId",
				backgroundUrl = null,
				backgroundUpdatedAt = null,
				description = ProfileDescription.createRaw("This is the profile description for user $userId.")
			)
		)
	}

	override suspend fun edit(data: EditProfileData): UpdateDataResponse {
 		mockStorage.saveString("profile_description", data.description?.value.orEmpty())
 		mockStorage.saveString("profile_username", data.username?.value.orEmpty())
 		mockStorage.saveString("profile_name", data.name?.value.orEmpty())
 
 		return UpdateDataResponse.Success
 	}

	override suspend fun updateAvatarUrl(avatarUrl: String): UpdateDataResponse {
		mockStorage.saveString("profile_avatar", avatarUrl)
		return UpdateDataResponse.Success
	}

	override suspend fun updateBackgroundUrl(backgroundUrl: String): UpdateDataResponse {
		mockStorage.saveString("profile_background", backgroundUrl)
		return UpdateDataResponse.Success
	}

	override suspend fun checkUsername(username: String): Boolean {
		// В моках всегда считаем, что имя свободно, если это не "taken"
		return username != "taken"
	}
 }
