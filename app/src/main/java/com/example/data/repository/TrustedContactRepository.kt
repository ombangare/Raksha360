package com.example.data.repository

import com.example.data.local.dao.TrustedContactDao
import com.example.data.local.entity.TrustedContactEntity
import kotlinx.coroutines.flow.Flow

class TrustedContactRepository(private val dao: TrustedContactDao) {

    val allContacts: Flow<List<TrustedContactEntity>> = dao.getAllContacts()

    fun getActiveContacts(): Flow<List<TrustedContactEntity>> = dao.getActiveContacts()

    suspend fun getActiveContactsList(): List<TrustedContactEntity> = dao.getActiveContactsList()

    suspend fun addContact(name: String, relationship: String, phone: String, email: String?): Long {
        val contact = TrustedContactEntity(
            name = name.trim(),
            relationship = relationship.trim(),
            phoneNumber = phone.trim(),
            email = email?.trim()?.ifBlank { null },
            notifyBySms = true,
            notifyByEmail = !email.isNullOrBlank(),
            isActive = true
        )
        return dao.insertContact(contact)
    }

    suspend fun updateContact(contact: TrustedContactEntity) {
        dao.updateContact(contact)
    }

    suspend fun deleteContact(id: Long) {
        dao.deleteContactById(id)
    }

    /**
     * Seeds demo contacts ONLY if explicit Demo Mode is enabled.
     * Normal authenticated users start with their own clean contact list.
     */
    suspend fun seedDemoContactsIfEmpty(isDemoMode: Boolean) {
        if (!isDemoMode) return

        if (dao.getActiveContactCount() == 0) {
            dao.insertContacts(
                listOf(
                    TrustedContactEntity(
                        name = "Demo Contact: Sister",
                        relationship = "Sister",
                        phoneNumber = "+91 98000 00001",
                        email = "demo.sister@naarikavach.local",
                        notifyBySms = true,
                        notifyByEmail = true,
                        isActive = true
                    ),
                    TrustedContactEntity(
                        name = "Demo Contact: Father",
                        relationship = "Father",
                        phoneNumber = "+91 98000 00002",
                        email = null,
                        notifyBySms = true,
                        notifyByEmail = false,
                        isActive = true
                    ),
                    TrustedContactEntity(
                        name = "Demo Contact: Flatmate",
                        relationship = "Friend / Flatmate",
                        phoneNumber = "+91 98000 00003",
                        email = "demo.friend@naarikavach.local",
                        notifyBySms = true,
                        notifyByEmail = false,
                        isActive = true
                    )
                )
            )
        }
    }
}
