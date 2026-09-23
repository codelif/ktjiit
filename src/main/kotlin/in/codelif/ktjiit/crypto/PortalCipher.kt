package `in`.codelif.ktjiit.crypto

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** aes-128-cbc with a key that rotates daily. the iv never changes, yes really. */
public object PortalCipher {
    private val IV = IvParameterSpec("dcek9wb8frty1pnm".toByteArray())

    private fun key(at: Instant) = SecretKeySpec("qa8y${dateSeq(at)}ty1pn".toByteArray(), "AES")

    private fun cipher(mode: Int, at: Instant) =
        Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(mode, key(at), IV) }

    public fun encrypt(plain: String, at: Instant): String =
        Base64.getEncoder().encodeToString(cipher(Cipher.ENCRYPT_MODE, at).doFinal(plain.toByteArray()))

    public fun decrypt(encoded: String, at: Instant): String =
        String(cipher(Cipher.DECRYPT_MODE, at).doFinal(Base64.getDecoder().decode(encoded.trim())))

    private val random = SecureRandom()
    private const val ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private fun noise(n: Int) = buildString { repeat(n) { append(ALNUM[random.nextInt(ALNUM.length)]) } }

    /** without it the server answers 200 with an empty body */
    public fun localName(at: Instant): String = encrypt(noise(4) + dateSeq(at) + noise(5), at)
}
