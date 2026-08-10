package com.inktone.data.backup

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement E2EE à mot de passe du fichier de sauvegarde local
 * (Lot 11, tâche 11.1). Le mot de passe n'est jamais stocké : seule une
 * clé dérivée (PBKDF2) sert au chiffrement AES/GCM, à la volée.
 *
 * Format de l'enveloppe (binaire) : `MAGIC (5o) | salt (16o) | iv (12o) |
 * ciphertext+tag GCM`. Un fichier qui ne commence pas par `MAGIC` — en
 * particulier un JSON en clair commençant par `{` — est un export
 * antérieur à ce lot (compatibilité ascendante, voir [BackupManager]).
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('I'.code.toByte(), 'N'.code.toByte(), 'K'.code.toByte(), 'B'.code.toByte(), 1)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    class WrongPasswordException : Exception("Mot de passe incorrect")

    fun isEncryptedEnvelope(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plainBytes: ByteArray, password: String): ByteArray {
        require(password.isNotBlank()) { "password ne peut pas être vide" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plainBytes)
        return MAGIC + salt + iv + ciphertext
    }

    /** @throws WrongPasswordException si le mot de passe ne correspond pas ou l'enveloppe est corrompue. */
    fun decrypt(envelope: ByteArray, password: String): ByteArray {
        require(isEncryptedEnvelope(envelope)) { "envelope n'est pas au format chiffré InkTone" }
        val salt = envelope.copyOfRange(MAGIC.size, MAGIC.size + SALT_LENGTH)
        val iv = envelope.copyOfRange(MAGIC.size + SALT_LENGTH, MAGIC.size + SALT_LENGTH + IV_LENGTH)
        val ciphertext = envelope.copyOfRange(MAGIC.size + SALT_LENGTH + IV_LENGTH, envelope.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: IllegalArgumentException) {
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
