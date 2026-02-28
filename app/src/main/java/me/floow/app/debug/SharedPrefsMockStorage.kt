package me.floow.app.debug

import android.content.Context
import me.floow.mock.interfaces.MockStorage

class SharedPrefsMockStorage(context: Context) : MockStorage {
    private val prefs = context.getSharedPreferences("mock_data_storage", Context.MODE_PRIVATE)

    override fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    override fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getBoolean(key: String): Boolean {
        return prefs.getBoolean(key, false)
    }
    
    override fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    override fun removeString(key: String) {
        prefs.edit().remove(key).apply()
    }
}
