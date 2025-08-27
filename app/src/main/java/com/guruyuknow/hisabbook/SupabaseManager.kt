package com.guruyuknow.hisabbook

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

object SupabaseManager {

    private const val SUPABASE_URL = "https://vqhmuwjizefxahczixxx.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZxaG11d2ppemVmeGFoY3ppeHh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDk2NzIzODYsImV4cCI6MjA2NTI0ODM4Nn0.JJKfWjHfhl4OWeOqsyJzjL0Hk5iFbjNl6YOI4BFcHoE"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage) // Add Storage plugin
    }

    suspend fun signInWithGoogle(idToken: String): Result<User?> {
        return try {
            withContext(Dispatchers.IO) {
                client.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    provider = Google
                }

                val supabaseUser = client.auth.currentUserOrNull()

                if (supabaseUser != null) {
                    val userData = User(
                        id = supabaseUser.id,
                        email = supabaseUser.email,
                        fullName = supabaseUser.userMetadata?.get("full_name")?.toString(),
                        avatarUrl = supabaseUser.userMetadata?.get("avatar_url")?.toString()
                    )

                    val savedUser = upsertUser(userData)
                    Result.success(savedUser)
                } else {
                    Result.failure(Exception("Authentication failed"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Updated updateUser method with image upload
    suspend fun updateUser(user: User, imageUri: Uri? = null, context: Context? = null): Result<User?> {
        return try {
            withContext(Dispatchers.IO) {
                println("=== UPDATE USER DEBUG ===")
                println("Input user: $user")
                println("Image URI: $imageUri")

                var avatarUrl = user.avatarUrl

                // Upload image if provided
                if (imageUri != null && context != null) {
                    println("Uploading image...")
                    val uploadResult = uploadProfileImage(imageUri, user.id ?: "", context)
                    if (uploadResult.isSuccess) {
                        avatarUrl = uploadResult.getOrNull()
                        println("Image uploaded successfully: $avatarUrl")
                    } else {
                        println("Image upload failed: ${uploadResult.exceptionOrNull()?.message}")
                        return@withContext Result.failure(
                            uploadResult.exceptionOrNull() ?: Exception("Image upload failed")
                        )
                    }
                }

                val updatedUser = user.copy(avatarUrl = avatarUrl)
                println("Updated user data: $updatedUser")

                // Use select() to return the updated data
                val result = client.from("user_profiles")
                    .update(
                        mapOf(
                            "full_name" to updatedUser.fullName,
                            "avatar_url" to avatarUrl
                        )
                    ) {
                        filter {
                            eq("id", user.id ?: "")
                        }
                    } // Add this to return the updated row
                    .decodeSingle<User>()

                println("Update result: $result")
                Result.success(result)
            }
        } catch (e: Exception) {
            println("Update user error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Upload profile image to Supabase Storage
    private suspend fun uploadProfileImage(imageUri: Uri, userId: String, context: Context): Result<String?> {
        return try {
            // Generate unique filename
            val fileExtension = getFileExtension(imageUri, context)
            val fileName = "profile_${userId}_${UUID.randomUUID()}.$fileExtension"

            // Convert Uri to ByteArray
            val imageBytes = uriToByteArray(imageUri, context)

            // Upload to Supabase Storage
            val bucket = client.storage["profile-images"] // Create this bucket in Supabase Dashboard

            bucket.upload(fileName, imageBytes, upsert = true)

            // Get public URL
            val publicUrl = bucket.publicUrl(fileName)

            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper function to convert Uri to ByteArray
    private suspend fun uriToByteArray(uri: Uri, context: Context): ByteArray {
        return withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val byteBuffer = ByteArrayOutputStream()

            inputStream?.use { input ->
                val bufferSize = 1024
                val buffer = ByteArray(bufferSize)
                var len: Int
                while (input.read(buffer).also { len = it } != -1) {
                    byteBuffer.write(buffer, 0, len)
                }
            }

            byteBuffer.toByteArray()
        }
    }

    // Helper function to get file extension
    private fun getFileExtension(uri: Uri, context: Context): String {
        return when (val mimeType = context.contentResolver.getType(uri)) {
            "image/jpeg" -> "jpg"
            "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "jpg" // default
        }
    }

    // Delete old profile image
    suspend fun deleteProfileImage(imageUrl: String): Result<Unit> {
        return try {
            // Extract filename from URL
            val fileName = imageUrl.substringAfterLast("/")
            val bucket = client.storage["profile-images"]
            bucket.delete(fileName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun upsertUser(user: User): User? {
        return try {
            val existingUser = client.from("user_profiles") // Changed from "users"
                .select {
                    filter {
                        eq("id", user.id ?: "")
                    }
                }
                .decodeSingleOrNull<User>()

            if (existingUser != null) {
                client.from("user_profiles")
                    .update(
                        mapOf(
                            "updated_at" to "now()",
                            "email" to user.email,
                            "full_name" to user.fullName,
                            "avatar_url" to user.avatarUrl
                        )
                    ) {
                        filter {
                            eq("id", user.id ?: "")
                        }
                    }
                    .decodeSingle<User>()
            } else {
                client.from("user_profiles") // Changed from "users"
                    .insert(user)
                    .decodeSingle<User>()
            }
        } catch (e: Exception) {
            println("Error upserting user: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun getCurrentUser(): User? {
        return try {
            println("=== GET CURRENT USER DEBUG ===")
            val supabaseUser = client.auth.currentUserOrNull()
            println("Supabase auth user: $supabaseUser")
            println("Supabase user ID: ${supabaseUser?.id}")

            if (supabaseUser != null) {
                val dbUser = client.from("user_profiles")
                    .select {
                        filter {
                            eq("id", supabaseUser.id)
                        }
                    }
                    .decodeSingle<User>()
                println("Database user: $dbUser")
                dbUser
            } else {
                println("No authenticated user found")
                null
            }
        } catch (e: Exception) {
            println("Error getting current user: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (e: Exception) {
            println("Error signing out: ${e.message}")
        }
    }
}