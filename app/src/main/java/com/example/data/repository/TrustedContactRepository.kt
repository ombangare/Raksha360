package com.example.data.repository

import com.example.data.local.dao.TrustedContactDao
import com.example.data.local.entity.TrustedContactEntity
import kotlinx.coroutines.flow.Flow

class TrustedContactRepository(private val dao: TrustedContactDao) {

    val allContacts: Flow<List<TrustedContactEntity>> = dao.getAllContacts()

    fun getActiveContacts(): Flow<List<TrustedContactEntity>> = dao.getActiveContacts()

    suspend fun getActiveContactsList(): List<TrustedContactEntity> = dao.getActiveContactsList()

    suspend fun addContact(name: String, relationship: String, phone: String, email: String?): Long {
        if (dao.getActiveContactCount() >= 2) {
            return -1L
        }
        val contact = TrustedContactEntity(
            name = name.trim(),
            relationship = relationship.trim(),
            phoneNumber = phone.trim(),
            email = email?.trim()?.ifBlank { null },
            notifyBySms = true,
            notifyByEmail = !email.isNullOrBlank(),
            isEmailVerified = false,
            canShareLocation = true,
            isActive = true
        )
        return dao.insertContact(contact)
    }

    suspend fun verifyContactEmail(id: Long) {
        val contact = dao.getContactById(id) ?: return
        dao.updateContact(contact.copy(isEmailVerified = true))
    }

    suspend fun updateLocationConsent(id: Long, canShare: Boolean) {
        val contact = dao.getContactById(id) ?: return
        dao.updateContact(contact.copy(canShareLocation = canShare))
    }

    suspend fun updateContact(contact: TrustedContactEntity) {
        dao.updateContact(contact)
    }

    suspend fun deleteContact(id: Long) {
        dao.deleteContactById(id)
    }

    /**
     * Seeds demo contacts ONLY if explicit Demo Mode is enabled.
     * Respects the maximum limit of 2 emergency contacts.
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
                        isEmailVerified = true,
                        canShareLocation = true,
                        isActive = true
                    ),
                    TrustedContactEntity(
                        name = "Demo Contact: Father",
                        relationship = "Father",
                        phoneNumber = "+91 98000 00002",
                        email = "demo.father@naarikavach.local",
                        notifyBySms = true,
                        notifyByEmail = true,
                        isEmailVerified = true,
                        canShareLocation = true,
                        isActive = true
                    )
                )
            )
        }
    }
}
