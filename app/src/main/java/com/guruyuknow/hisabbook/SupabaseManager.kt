package com.guruyuknow.hisabbook

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.guruyuknow.hisabbook.Staff.Attendance
import com.guruyuknow.hisabbook.Staff.AttendanceStatus
import com.guruyuknow.hisabbook.Staff.Salary
import com.guruyuknow.hisabbook.Staff.SalaryType
import com.guruyuknow.hisabbook.Staff.Staff
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        install(Storage)
    }

    suspend fun signInWithGoogle(idToken: String): Result<User?> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Attempting Google sign-in with idToken: $idToken")
                client.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    provider = Google
                }

                val supabaseUser = client.auth.currentUserOrNull()
                Log.d("SupabaseManager", "Supabase user after sign-in: $supabaseUser")

                if (supabaseUser != null) {
                    val userData = User(
                        id = supabaseUser.id,
                        email = supabaseUser.email,
                        fullName = supabaseUser.userMetadata?.get("full_name")?.toString(),
                        avatarUrl = supabaseUser.userMetadata?.get("avatar_url")?.toString()
                    )
                    Log.d("SupabaseManager", "User data created: $userData")

                    val savedUser = upsertUser(userData)
                    Log.d("SupabaseManager", "Upserted user: $savedUser")
                    Result.success(savedUser)
                } else {
                    Log.e("SupabaseManager", "Authentication failed: No user found")
                    Result.failure(Exception("Authentication failed"))
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error signing in with Google: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: User, imageUri: Uri? = null, context: Context? = null): Result<User?> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Updating user: $user, imageUri: $imageUri")
                var avatarUrl = user.avatarUrl

                if (imageUri != null && context != null) {
                    Log.d("SupabaseManager", "Uploading profile image for user: ${user.id}")
                    val uploadResult = uploadProfileImage(imageUri, user.id ?: "", context)
                    if (uploadResult.isSuccess) {
                        avatarUrl = uploadResult.getOrNull()
                        Log.d("SupabaseManager", "Image uploaded successfully: $avatarUrl")
                    } else {
                        Log.e("SupabaseManager", "Image upload failed: ${uploadResult.exceptionOrNull()?.message}")
                        return@withContext Result.failure(
                            uploadResult.exceptionOrNull() ?: Exception("Image upload failed")
                        )
                    }
                }

                val updatedUser = user.copy(avatarUrl = avatarUrl)
                Log.d("SupabaseManager", "Updated user data: $updatedUser")

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
                    }
                    .decodeSingle<User>()
                Log.d("SupabaseManager", "User update result: $result")
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error updating user: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun uploadProfileImage(imageUri: Uri, userId: String, context: Context): Result<String?> {
        return try {
            Log.d("SupabaseManager", "Uploading profile image for userId: $userId")
            val fileExtension = getFileExtension(imageUri, context)
            val fileName = "profile_${userId}_${UUID.randomUUID()}.$fileExtension"
            val imageBytes = uriToByteArray(imageUri, context)

            val bucket = client.storage["profile-images"]
            bucket.upload(fileName, imageBytes, upsert = true)
            val publicUrl = bucket.publicUrl(fileName)
            Log.d("SupabaseManager", "Profile image uploaded: $publicUrl")
            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error uploading profile image: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun uriToByteArray(uri: Uri, context: Context): ByteArray {
        return withContext(Dispatchers.IO) {
            Log.d("SupabaseManager", "Converting URI to ByteArray: $uri")
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
            Log.d("SupabaseManager", "URI converted to ByteArray, size: ${byteBuffer.size()}")
            byteBuffer.toByteArray()
        }
    }

    private fun getFileExtension(uri: Uri, context: Context): String {
        val mimeType = context.contentResolver.getType(uri)
        Log.d("SupabaseManager", "Determining file extension for URI: $uri, mimeType: $mimeType")
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> {
                Log.w("SupabaseManager", "Unknown mimeType: $mimeType, defaulting to jpg")
                "jpg"
            }
        }
    }

    suspend fun deleteProfileImage(imageUrl: String): Result<Unit> {
        return try {
            Log.d("SupabaseManager", "Deleting profile image: $imageUrl")
            val fileName = imageUrl.substringAfterLast("/")
            val bucket = client.storage["profile-images"]
            bucket.delete(fileName)
            Log.d("SupabaseManager", "Profile image deleted: $fileName")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error deleting profile image: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun upsertUser(user: User): User? {
        return try {
            Log.d("SupabaseManager", "Upserting user: $user")
            val existingUser = client.from("user_profiles")
                .select {
                    filter {
                        eq("id", user.id ?: "")
                    }
                }
                .decodeSingleOrNull<User>()
            Log.d("SupabaseManager", "Existing user: $existingUser")

            if (existingUser != null) {
                val result = client.from("user_profiles")
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
                Log.d("SupabaseManager", "User updated: $result")
                result
            } else {
                val result = client.from("user_profiles")
                    .insert(user)
                    .decodeSingle<User>()
                Log.d("SupabaseManager", "User inserted: $result")
                result
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error upserting user: ${e.message}", e)
            null
        }
    }

    suspend fun getCurrentUser(): User? {
        return try {
            Log.d("SupabaseManager", "Fetching current user")
            val supabaseUser = client.auth.currentUserOrNull()
            Log.d("SupabaseManager", "Supabase auth user: $supabaseUser")

            if (supabaseUser != null) {
                val dbUser = client.from("user_profiles")
                    .select {
                        filter {
                            eq("id", supabaseUser.id)
                        }
                    }
                    .decodeSingle<User>()
                Log.d("SupabaseManager", "Database user: $dbUser")
                dbUser
            } else {
                Log.e("SupabaseManager", "No authenticated user found")
                null
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error getting current user: ${e.message}", e)
            null
        }
    }

    suspend fun signOut() {
        try {
            Log.d("SupabaseManager", "Signing out")
            client.auth.signOut()
            Log.d("SupabaseManager", "Sign out successful")
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error signing out: ${e.message}", e)
        }
    }

    suspend fun addStaff(staff: Staff): Result<Staff> {
        return try {
            Log.d("SupabaseManager", "Adding staff: $staff")
            val response = client.from("staff")
                .insert(staff)
                .decodeSingle<Staff>()
            Log.d("SupabaseManager", "Staff added successfully: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error adding staff: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getStaffByBusinessOwner(businessOwnerId: String): Result<List<Staff>> {
        return try {
            Log.d("SupabaseManager", "Fetching staff for businessOwnerId: $businessOwnerId")
            val response = client.from("staff")
                .select {
                    filter {
                        eq("business_owner_id", businessOwnerId)
                        eq("is_active", true)
                    }
                }
                .decodeList<Staff>()
            Log.d("SupabaseManager", "Staff fetched successfully: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching staff: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateStaff(staff: Staff): Result<Staff> {
        return try {
            Log.d("SupabaseManager", "Updating staff: $staff")
            val response = client.from("staff")
                .update(staff) {
                    filter {
                        eq("id", staff.id)
                    }
                }
                .decodeSingle<Staff>()
            Log.d("SupabaseManager", "Staff updated successfully: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error updating staff: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteStaff(staffId: String): Result<Boolean> {
        return try {
            Log.d("SupabaseManager", "Deleting staff with ID: $staffId")
            client.from("staff")
                .update(mapOf("isActive" to false)) {
                    filter {
                        eq("id", staffId)
                    }
                }
            Log.d("SupabaseManager", "Staff deleted successfully: $staffId")
            Result.success(true)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error deleting staff: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun markAttendance(attendance: Attendance): Result<Attendance> {
        return try {
            Log.d("SupabaseManager", "Attempting to mark attendance: $attendance")
            // Check for existing attendance
            val existingAttendance = client.from("attendance")
                .select {
                    filter {
                        eq("staff_id", attendance.staffId)
                        eq("date", attendance.date)
                    }
                }
                .decodeSingleOrNull<Attendance>()
            Log.d("SupabaseManager", "Existing attendance check: $existingAttendance")

            if (existingAttendance != null) {
                Log.d("SupabaseManager", "Attendance already exists for staff ${attendance.staffId} on ${attendance.date}, updating record")
                val response = client.from("attendance")
                    .update(attendance) {
                        filter {
                            eq("id", existingAttendance.id)
                        }
                    }
                    .decodeSingle<Attendance>()
                Log.d("SupabaseManager", "Attendance updated successfully: $response")
                Result.success(response)
            } else {
                val response = client.from("attendance")
                    .insert(attendance)
                    .decodeSingle<Attendance>()
                Log.d("SupabaseManager", "Attendance marked successfully: $response")
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error marking attendance: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceByStaffAndMonth(staffId: String, month: String): Result<List<Attendance>> {
        return try {
            Log.d("SupabaseManager", "Fetching attendance for staffId: $staffId, month: $month")
            val response = client.from("attendance")
                .select {
                    filter {
                        eq("staff_id", staffId)
                        like("date", "$month%")
                    }
                }
                .decodeList<Attendance>()
            Log.d("SupabaseManager", "Attendance fetched successfully: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching attendance: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getTodayAttendance(businessOwnerId: String): Result<List<Attendance>> {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            Log.d("SupabaseManager", "Fetching today's attendance for businessOwnerId: $businessOwnerId, date: $today")
            val response = client.from("attendance")
                .select {
                    filter {
                        eq("business_owner_id", businessOwnerId)
                        eq("date", today)
                    }
                }
                .decodeList<Attendance>()
            Log.d("SupabaseManager", "Today's attendance fetched successfully: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching today's attendance: ${e.message}", e)
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun calculateAndSaveSalary(staffId: String, month: String): Result<Salary> {
        return try {
            Log.d("SupabaseManager", "Calculating and saving salary for staffId: $staffId, month: $month")
            val staff = client.from("staff")
                .select {
                    filter {
                        eq("id", staffId)
                    }
                }
                .decodeSingle<Staff>()
            Log.d("SupabaseManager", "Staff fetched for salary calculation: $staff")

            val attendance = getAttendanceByStaffAndMonth(staffId, month).getOrThrow()
            Log.d("SupabaseManager", "Attendance for salary calculation: $attendance")

            val presentDays = attendance.count { it.status == AttendanceStatus.PRESENT }
            val absentDays = attendance.count { it.status == AttendanceStatus.ABSENT }
            val halfDays = attendance.count { it.status == AttendanceStatus.HALF_DAY }
            val lateDays = attendance.count { it.status == AttendanceStatus.LATE }

            val calculatedSalary = when (staff.salaryType) {
                SalaryType.MONTHLY -> staff.salaryAmount
                SalaryType.DAILY -> staff.salaryAmount * (presentDays + (halfDays * 0.5))
            }
            Log.d("SupabaseManager", "Calculated salary: $calculatedSalary, presentDays: $presentDays, halfDays: $halfDays")

            val salary = Salary(
                staffId = staffId,
                businessOwnerId = staff.businessOwnerId,
                month = month,
                basicSalary = staff.salaryAmount,
                totalDays = attendance.size,
                presentDays = presentDays,
                absentDays = absentDays,
                halfDays = halfDays,
                lateDays = lateDays,
                calculatedSalary = calculatedSalary,
                finalSalary = calculatedSalary
            )
            Log.d("SupabaseManager", "Salary object created: $salary")

            val response = client.from("salary")
                .insert(salary)
                .decodeSingle<Salary>()
            Log.d("SupabaseManager", "Salary saved successfully: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error calculating and saving salary: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getStaffById(staffId: String): Result<Staff?> {
        return try {
            Log.d("SupabaseManager", "Fetching staff by ID: $staffId")
            val response = client.from("staff")
                .select {
                    filter {
                        eq("id", staffId)
                    }
                }
                .decodeSingleOrNull<Staff>()
            Log.d("SupabaseManager", "Staff fetched: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching staff by ID: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateAttendance(attendance: Attendance): Result<Attendance> {
        return try {
            Log.d("SupabaseManager", "Updating attendance: $attendance")
            val response = client.from("attendance")
                .update(attendance) {
                    filter {
                        eq("id", attendance.id)
                    }
                }
                .decodeSingle<Attendance>()
            Log.d("SupabaseManager", "Attendance updated successfully: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error updating attendance: ${e.message}", e)
            Result.failure(e)
        }
    }
}