package com.example.features.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.example.core.FileSystemProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AppAuditItem(
    val appName: String,
    val packageName: String,
    val grantedDangerousPermissions: List<String>,
    val riskScore: Int, // Number of dangerous permissions
    val riskLevel: String // "HIGH", "MEDIUM", "LOW"
)

object SecurityVaultHelper {
    private const val VAULT_FOLDER_NAME = ".kryon_vault"
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    fun getVaultDirectory(context: Context): File {
        val storageDir = File(FileSystemProvider.getPrimaryStoragePath())
        val vaultDir = File(storageDir, VAULT_FOLDER_NAME)
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        return vaultDir
    }

    // Generate a 256-bit AES key from a passphrase
    private fun generateKey(passcode: String): SecretKeySpec {
        val paddedPass = passcode.padEnd(32, 'K').take(32)
        return SecretKeySpec(paddedPass.toByteArray(Charsets.UTF_8), "AES")
    }

    // Encrypts file from source to vault directory
    fun encryptFile(sourceFile: File, destFile: File, passcode: String): Boolean {
        return try {
            val key = generateKey(passcode)
            val cipher = Cipher.getInstance(ALGORITHM)
            
            // Generate a random IV
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
            
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    // Write IV first
                    output.write(iv)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encrypted = cipher.update(buffer, 0, bytesRead)
                        if (encrypted != null) {
                            output.write(encrypted)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        output.write(finalBytes)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Decrypts file from vault to destination path
    fun decryptFile(vaultFile: File, destFile: File, passcode: String): Boolean {
        return try {
            val key = generateKey(passcode)
            val cipher = Cipher.getInstance(ALGORITHM)
            
            FileInputStream(vaultFile).use { input ->
                // Read the 16-byte IV from the beginning of the file
                val iv = ByteArray(16)
                if (input.read(iv) != 16) return false
                val ivSpec = IvParameterSpec(iv)
                
                cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
                
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        if (decrypted != null) {
                            output.write(decrypted)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        output.write(finalBytes)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Runs a background permission audit on all installed applications
    fun performPermissionAudit(context: Context): List<AppAuditItem> {
        val auditList = mutableListOf<AppAuditItem>()
        val pm = context.packageManager
        
        try {
            val packages: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in packages) {
                // Ignore system apps for standard audits to focus on downloaded potential risks
                val appInfo = pkg.applicationInfo
                val isSystemApp = (appInfo != null && 
                    (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                
                val dangerousPerms = mutableListOf<String>()
                val requestedPermissions = pkg.requestedPermissions
                val requestedPermissionsFlags = pkg.requestedPermissionsFlags
                
                if (requestedPermissions != null && requestedPermissionsFlags != null) {
                    for (i in requestedPermissions.indices) {
                        if (i < requestedPermissionsFlags.size) {
                            val perm = requestedPermissions[i]
                            // Standard dangerous permission indicators
                            val isGranted = (requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                            if (isGranted && (
                                perm.contains("CAMERA") || 
                                perm.contains("RECORD_AUDIO") || 
                                perm.contains("LOCATION") || 
                                perm.contains("READ_EXTERNAL_STORAGE") || 
                                perm.contains("WRITE_EXTERNAL_STORAGE") || 
                                perm.contains("CONTACTS") || 
                                perm.contains("SMS") || 
                                perm.contains("READ_PHONE_STATE")
                            )) {
                                dangerousPerms.add(perm.substringAfterLast("."))
                            }
                        }
                    }
                }

                if (dangerousPerms.isNotEmpty() || !isSystemApp) {
                    val appName = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                    val riskScore = dangerousPerms.size
                    val riskLevel = when {
                        riskScore >= 4 -> "HIGH"
                        riskScore >= 2 -> "MEDIUM"
                        else -> "LOW"
                    }
                    
                    auditList.add(
                        AppAuditItem(
                            appName = appName,
                            packageName = pkg.packageName,
                            grantedDangerousPermissions = dangerousPerms,
                            riskScore = riskScore,
                            riskLevel = riskLevel
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return auditList.sortedByDescending { it.riskScore }
    }
}
