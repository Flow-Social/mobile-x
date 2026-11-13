package me.floow.shared.di

import kotlin.test.Test
import kotlin.test.assertNotNull
import me.floow.chatssearch.uilogic.SearchUsersStateHolder
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.UsersRepository
import me.floow.domain.models.UserPreview
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class SearchUsersKoinTest {

    private class FakeUsersRepository : UsersRepository {
        override suspend fun searchUsers(query: String): GetDataResponse<List<UserPreview>> =
            GetDataResponse.Success(emptyList())
    }

    @Test
    fun `resolves SearchUsersStateHolder from sharedCommonModule`() {
        val testModule = module {
            single<UsersRepository> { FakeUsersRepository() }
        }

        val app = koinApplication {
            modules(testModule, sharedCommonModule)
        }

        val stateHolder = app.koin.getOrNull<SearchUsersStateHolder>()
        assertNotNull(stateHolder, "SearchUsersStateHolder should be registered and resolvable from sharedCommonModule")
    }
}
