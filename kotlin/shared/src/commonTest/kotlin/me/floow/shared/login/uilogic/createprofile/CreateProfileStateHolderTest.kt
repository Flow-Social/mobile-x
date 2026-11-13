package me.floow.shared.login.uilogic.createprofile

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.uikit.util.state.ValidatedField
import me.floow.uikit.util.state.ValidationErrorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateProfileStateHolderTest {

    @Test
    fun `create profile emits completed on successful save`() = runTest {
        var savedData: EditProfileData? = null
        val holder = CreateProfileStateHolder(
            profileRepository = object : CreateProfileRepository {
                override suspend fun submitProfile(data: EditProfileData): CreateProfileSubmissionResult {
                    savedData = data
                    return CreateProfileSubmissionResult.Success
                }
            },
            scope = backgroundScope,
        )

        val deferredEvent = async(start = CoroutineStart.UNDISPATCHED) { holder.events.first() }
        holder.updateName("Bogdan")
        holder.updateUsername("bogdan")
        holder.updateBio("Hello")
        holder.createProfile()
        advanceUntilIdle()

        assertEquals(CreateProfileStateHolder.Event.Completed, deferredEvent.await())
        assertEquals("Bogdan", savedData?.name?.value)
        assertEquals("bogdan", savedData?.username?.value)
        assertEquals("Hello", savedData?.description?.value)
    }

    @Test
    fun `create profile keeps edit state and shows message when fields are invalid`() = runTest {
        var editCalls = 0
        val holder = CreateProfileStateHolder(
            profileRepository = object : CreateProfileRepository {
                override suspend fun submitProfile(data: EditProfileData): CreateProfileSubmissionResult {
                    editCalls += 1
                    return CreateProfileSubmissionResult.Success
                }
            },
            scope = backgroundScope,
        )

        val deferredEvents = async(start = CoroutineStart.UNDISPATCHED) { holder.events.take(2).toList() }
        holder.updateName("")
        holder.updateUsername("ok_username")
        holder.updateBio("Hello")
        holder.createProfile()
        advanceUntilIdle()

        assertEquals(
            listOf(
                CreateProfileStateHolder.Event.HapticFeedback,
                CreateProfileStateHolder.Event.ShowMessage("Проверьте поля профиля"),
            ),
            deferredEvents.await(),
        )
        assertEquals(0, editCalls)
        assertTrue(holder.state.value is CreateProfileState.Edit)
    }

    @Test
    fun `create profile applies pending registration initial data`() = runTest {
        val holder = CreateProfileStateHolder(
            profileRepository = object : CreateProfileRepository {
                override suspend fun submitProfile(data: EditProfileData): CreateProfileSubmissionResult {
                    return CreateProfileSubmissionResult.Success
                }
            },
            initialData = PendingRegistrationInitialData(
                name = "Bogdan",
                username = "bogdan",
                description = "Hello",
            ),
            scope = backgroundScope,
        )

        val state = assertIs<CreateProfileState.Edit>(holder.state.value)
        assertEquals("Bogdan", state.name.value)
        assertEquals("bogdan", state.username.value)
        assertEquals("Hello", state.bio.value)
    }

    @Test
    fun `create profile marks username taken on conflict`() = runTest {
        val holder = CreateProfileStateHolder(
            profileRepository = object : CreateProfileRepository {
                override suspend fun submitProfile(data: EditProfileData): CreateProfileSubmissionResult {
                    return CreateProfileSubmissionResult.UsernameAlreadyExists
                }
            },
            scope = backgroundScope,
        )

        holder.updateName("Bogdan")
        holder.updateUsername("bogdan")
        holder.updateBio("Hello")
        val deferredEvents = async(start = CoroutineStart.UNDISPATCHED) { holder.events.first() }
        holder.createProfile()
        advanceUntilIdle()

        assertEquals(
            CreateProfileStateHolder.Event.ShowMessage("Этот юзернейм уже занят"),
            deferredEvents.await(),
        )
        val state = assertIs<CreateProfileState.Edit>(holder.state.value)
        val username = assertIs<ValidatedField.Invalid>(state.username)
        assertEquals(ValidationErrorType.UsernameAlreadyExists, username.errorType)
    }
}
