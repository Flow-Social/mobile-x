package me.floow.mock.interfaces

interface MockStorage {
    fun saveString(key: String, value: String)
    fun getString(key: String): String?
    fun saveBoolean(key: String, value: Boolean)
    fun getBoolean(key: String): Boolean
    fun removeString(key: String)
    
    // Методы для тестирования
    fun clearAll()
}
