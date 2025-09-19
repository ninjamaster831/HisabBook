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
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

object SupabaseManager {
    private var isClientInitialized = false
    private const val SUPABASE_URL = "https://vqhmuwjizefxahczixxx.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZxaG11d2ppemVmeGFoY3ppeHh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDk2NzIzODYsImV4cCI6MjA2NTI0ODM4Nn0.JJKfWjHfhl4OWeOqsyJzjL0Hk5iFbjNl6YOI4BFcHoE"

    var client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest){
            serializer = KotlinXSerializer()
        }
        install(Storage)
    }.also {
        isClientInitialized = true
        Log.d("SupabaseManager", "Supabase client initialized")
    }

    // ==================== STAFF MANAGEMENT ====================

    suspend fun addStaff(staff: Staff): Result<Staff> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Adding staff: ${staff.name}")

                // Optional: prevent duplicates by phone within same business owner
                val existingStaff = client.from("staff")
                    .select {
                        filter {
                            eq("phone_number", staff.phoneNumber)
                            eq("business_owner_id", staff.businessOwnerId)
                            eq("is_active", true)
                        }
                    }
                    .decodeSingleOrNull<Staff>()

                if (existingStaff != null) {
                    return@withContext Result.failure(Exception("Staff with this phone number already exists"))
                }

                val created = client.from("staff")
                    .insert(staff) {
                        // Make PostgREST return representation
                        select()
                    }
                    .decodeSingle<Staff>()

                Log.d("SupabaseManager", "Staff added successfully: ${created.id}")
                Result.success(created)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error adding staff: ${e.message}", e)
            Result.failure(e)
        }
    }


    suspend fun getStaffByBusinessOwner(businessOwnerId: String): Result<List<Staff>> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Fetching staff for business owner: $businessOwnerId")

                val response = client.from("staff")
                    .select {
                        filter {
                            eq("business_owner_id", businessOwnerId)
                            eq("is_active", true)
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Staff>()

                Log.d("SupabaseManager", "Found ${response.size} staff members")
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching staff: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getStaffById(staffId: String): Result<Staff?> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Fetching staff by ID: $staffId")

                val response = client.from("staff")
                    .select {
                        filter {
                            eq("id", staffId)
                            eq("is_active", true)
                        }
                    }
                    .decodeSingleOrNull<Staff>()

                Log.d("SupabaseManager", "Staff found: ${response?.name ?: "null"}")
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching staff by ID: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateStaff(staff: Staff): Result<Staff> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Updating staff: ${staff.id}")

                val updated = client.from("staff")
                    .update(staff) {
                        filter { eq("id", staff.id) }
                        select() // return updated row
                    }
                    .decodeSingle<Staff>()

                Log.d("SupabaseManager", "Staff updated successfully: ${updated.id}")
                Result.success(updated)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error updating staff: ${e.message}", e)
            Result.failure(e)
        }
    }


    suspend fun deleteStaff(staffId: String): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Soft deleting staff: $staffId")

                client.from("staff")
                    .update(mapOf("is_active" to false)) {
                        filter {
                            eq("id", staffId)
                        }
                    }

                Log.d("SupabaseManager", "Staff soft deleted successfully")
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error deleting staff: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== ATTENDANCE MANAGEMENT ====================

    suspend fun markAttendance(attendance: Attendance): Result<Attendance> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Marking attendance for staff=${attendance.staffId}, date=${attendance.date}")

                suspend fun fetchExisting(): Attendance? {
                    return client.from("attendance")
                        .select {
                            filter {
                                eq("staff_id", attendance.staffId)
                                eq("date", attendance.date)
                            }
                            // order("created_at", Order.DESCENDING) // optional
                            limit(1)
                        }
                        .decodeSingleOrNull<Attendance>()
                }

                // Try once; on cancellation, small delay and retry
                val existing = try {
                    fetchExisting()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException || e.message?.contains("Job was cancelled") == true) {
                        Log.w("SupabaseManager", "existing-check cancelled; retrying once...")
                        kotlinx.coroutines.delay(250)
                        fetchExisting()
                    } else {
                        throw e
                    }
                }

                val saved = if (existing != null) {
                    client.from("attendance")
                        .update(attendance) {
                            filter { eq("id", existing.id) }
                            select()
                        }
                        .decodeSingle<Attendance>()
                } else {
                    client.from("attendance")
                        .insert(attendance) {
                            select()
                        }
                        .decodeSingle<Attendance>()
                }

                Log.d("SupabaseManager", "Attendance saved, status=${saved.status}")
                Result.success(saved)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error marking attendance: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceSummary(staffId: String, month: String): Result<AttendanceSummary> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Fetching attendance summary for staff: $staffId, month: $month")

                // Get attendance data for the month
                val attendanceResult = getAttendanceByStaffAndMonth(staffId, month)
                val attendance = attendanceResult.getOrNull() ?: emptyList()

                // Count attendance statuses
                val presentCount = attendance.count { it.status == AttendanceStatus.PRESENT }
                val absentCount = attendance.count { it.status == AttendanceStatus.ABSENT }
                val halfDayCount = attendance.count { it.status == AttendanceStatus.HALF_DAY }

                // Create a summary object
                val summary = AttendanceSummary(
                    present = presentCount,
                    absent = absentCount,
                    halfDay = halfDayCount
                )

                Result.success(summary)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching attendance summary: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Data class to hold attendance summary
    data class AttendanceSummary(
        val present: Int,
        val absent: Int,
        val halfDay: Int
    )


    suspend fun getAttendanceByStaff(staffId: String, limit: Int = 30): Result<List<Attendance>> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Fetching attendance history for staff: $staffId")

                val response = client.from("attendance")
                    .select {
                        filter {
                            eq("staff_id", staffId)
                        }
                        order("date", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<Attendance>()

                Log.d("SupabaseManager", "Found ${response.size} attendance records")
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching attendance: ${e.message}", e)
            Result.failure(e)
        }
    }
    suspend fun calculateSalary(staffId: String, month: String): Result<Double> {
        return try {
            withContext(Dispatchers.IO) {
                val staffResult = getStaffById(staffId)
                val staff = staffResult.getOrNull() ?: return@withContext Result.failure(Exception("Staff not found"))

                val attendanceResult = getAttendanceByStaffAndMonth(staffId, month)
                val attendance = attendanceResult.getOrNull() ?: emptyList()

                val presentDays = attendance.count { it.status == AttendanceStatus.PRESENT }
                val lateDays = attendance.count { it.status == AttendanceStatus.LATE }
                val halfDays = attendance.count { it.status == AttendanceStatus.HALF_DAY }

                val totalSalary = when (staff.salaryType) {
                    SalaryType.MONTHLY -> staff.salaryAmount // For monthly salary
                    SalaryType.DAILY -> staff.salaryAmount * (presentDays + lateDays + halfDays * 0.5) // For daily salary
                }

                Result.success(totalSalary)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error calculating salary: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceByStaffAndMonth(staffId: String, month: String): Result<List<Attendance>> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Fetching monthly attendance for staff: $staffId, month: $month")

                // Parse month (YYYY-MM format)
                val parts = month.split("-")
                if (parts.size < 2) {
                    return@withContext Result.failure(Exception("Invalid month format. Expected YYYY-MM"))
                }

                val year = parts[0].toIntOrNull() ?: return@withContext Result.failure(Exception("Invalid year"))
                val monthNum = parts[1].toIntOrNull() ?: return@withContext Result.failure(Exception("Invalid month"))

                val startDate = String.format("%04d-%02d-01", year, monthNum)
                val nextYear = if (monthNum == 12) year + 1 else year
                val nextMonth = if (monthNum == 12) 1 else monthNum + 1
                val endDate = String.format("%04d-%02d-01", nextYear, nextMonth)

                val response = client.from("attendance")
                    .select {
                        filter {
                            eq("staff_id", staffId)
                            gte("date", startDate)
                            lt("date", endDate)
                        }
                        order("date", Order.ASCENDING)
                    }
                    .decodeList<Attendance>()

                Log.d("SupabaseManager", "Found ${response.size} monthly attendance records")
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching monthly attendance: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getTodayAttendance(businessOwnerId: String): Result<List<Attendance>> {
        return try {
            withContext(Dispatchers.IO) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                Log.d("SupabaseManager", "Fetching today's attendance for business: $businessOwnerId")

                val response = client.from("attendance")
                    .select {
                        filter {
                            eq("business_owner_id", businessOwnerId)
                            eq("date", today)
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Attendance>()

                Log.d("SupabaseManager", "Found ${response.size} today's attendance records")
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching today's attendance: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== SALARY MANAGEMENT ====================

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun calculateMonthlySalary(staffId: String, month: String): Result<Salary?> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Calculating salary for staff: $staffId, month: $month")

                // Get staff details
                val staffResult = getStaffById(staffId)
                val staff = staffResult.getOrNull()
                    ?: return@withContext Result.failure(Exception("Staff not found"))

                // Get monthly attendance
                val attendanceResult = getAttendanceByStaffAndMonth(staffId, month)
                val attendanceList = attendanceResult.getOrNull() ?: emptyList()

                // Calculate attendance metrics
                val presentDays = attendanceList.count {
                    it.status == AttendanceStatus.PRESENT
                }
                val lateDays = attendanceList.count {
                    it.status == AttendanceStatus.LATE
                }
                val halfDays = attendanceList.count {
                    it.status == AttendanceStatus.HALF_DAY
                }
                val absentDays = attendanceList.count {
                    it.status == AttendanceStatus.ABSENT
                }

                // Calculate salary based on type
                val calculatedSalary = when (staff.salaryType) {
                    SalaryType.MONTHLY -> {
                        // For monthly salary, calculate proportional amount based on attendance
                        val totalWorkingDays = presentDays + lateDays + halfDays + absentDays
                        val effectiveWorkingDays = presentDays + lateDays + (halfDays * 0.5)

                        if (totalWorkingDays > 0) {
                            staff.salaryAmount * (effectiveWorkingDays / totalWorkingDays)
                        } else {
                            staff.salaryAmount
                        }
                    }
                    SalaryType.DAILY -> {
                        val workingDays = presentDays + lateDays + (halfDays * 0.5)
                        staff.salaryAmount * workingDays
                    }
                }

                val salary = Salary(
                    staffId = staffId,
                    businessOwnerId = staff.businessOwnerId,
                    month = month,
                    basicSalary = staff.salaryAmount,
                    totalDays = attendanceList.size,
                    presentDays = presentDays + lateDays, // Late counted as present for salary
                    absentDays = absentDays,
                    halfDays = halfDays,
                    lateDays = lateDays,
                    calculatedSalary = calculatedSalary,
                    finalSalary = calculatedSalary,
                    bonus = 0.0,
                    deductions = 0.0
                )

                Log.d("SupabaseManager", "Salary calculated: ₹${salary.finalSalary}")
                Result.success(salary)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error calculating salary: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun saveSalary(salary: Salary): Result<Salary> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Saving salary for staff=${salary.staffId}, month=${salary.month}")

                val existing = client.from("salary")
                    .select {
                        filter {
                            eq("staff_id", salary.staffId)
                            eq("month", salary.month)
                        }
                    }
                    .decodeSingleOrNull<Salary>()

                val saved = if (existing != null) {
                    Log.d("SupabaseManager", "Updating existing salary id=${existing.id}")
                    client.from("salary")
                        .update(salary) {
                            filter { eq("id", existing.id) }
                            select() // return updated row
                        }
                        .decodeSingle<Salary>()
                } else {
                    Log.d("SupabaseManager", "Creating new salary record")
                    client.from("salary")
                        .insert(salary) {
                            select() // return inserted row
                        }
                        .decodeSingle<Salary>()
                }

                Log.d("SupabaseManager", "Salary saved, final=${saved.finalSalary}")
                Result.success(saved)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error saving salary: ${e.message}", e)
            Result.failure(e)
        }
    }


    suspend fun getSalaryByStaffAndMonth(staffId: String, month: String): Result<Salary?> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Fetching salary for staff: $staffId, month: $month")

                val response = client.from("salary")
                    .select {
                        filter {
                            eq("staff_id", staffId)
                            eq("month", month)
                        }
                    }
                    .decodeSingleOrNull<Salary>()

                Log.d("SupabaseManager", "Salary found: ${response?.finalSalary ?: "null"}")
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching salary: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getSalaryHistoryByStaff(staffId: String, limit: Int = 12): Result<List<Salary>> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Fetching salary history for staff: $staffId")

                val response = client.from("salary")
                    .select {
                        filter {
                            eq("staff_id", staffId)
                        }
                        order("month", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<Salary>()

                Log.d("SupabaseManager", "Found ${response.size} salary records")
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching salary history: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== ANALYTICS & REPORTING ====================

    suspend fun getMonthlyStaffSummary(businessOwnerId: String, month: String): Result<StaffMonthlySummary> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Generating monthly staff summary for: $month")

                // Get all active staff
                val staffResult = getStaffByBusinessOwner(businessOwnerId)
                val staffList = staffResult.getOrNull() ?: emptyList()

                var totalSalaryDue = 0.0
                var totalPresent = 0
                var totalAbsent = 0
                var totalHalfDay = 0
                var totalLate = 0

                // Calculate totals across all staff
                for (staff in staffList) {
                    val attendanceResult = getAttendanceByStaffAndMonth(staff.id, month)
                    val attendance = attendanceResult.getOrNull() ?: emptyList()

                    totalPresent += attendance.count { it.status == AttendanceStatus.PRESENT }
                    totalAbsent += attendance.count { it.status == AttendanceStatus.ABSENT }
                    totalHalfDay += attendance.count { it.status == AttendanceStatus.HALF_DAY }
                    totalLate += attendance.count { it.status == AttendanceStatus.LATE }

                    // Calculate salary for this staff member
                    when (staff.salaryType) {
                        SalaryType.MONTHLY -> totalSalaryDue += staff.salaryAmount
                        SalaryType.DAILY -> {
                            val workingDays = attendance.count { it.status == AttendanceStatus.PRESENT } +
                                    attendance.count { it.status == AttendanceStatus.LATE } +
                                    (attendance.count { it.status == AttendanceStatus.HALF_DAY } * 0.5)
                            totalSalaryDue += staff.salaryAmount * workingDays
                        }
                    }
                }

                val summary = StaffMonthlySummary(
                    month = month,
                    totalStaff = staffList.size,
                    totalSalaryDue = totalSalaryDue,
                    totalPresentDays = totalPresent,
                    totalAbsentDays = totalAbsent,
                    totalHalfDays = totalHalfDay,
                    totalLateDays = totalLate
                )

                Log.d("SupabaseManager", "Monthly summary generated: ${staffList.size} staff, ₹$totalSalaryDue due")
                Result.success(summary)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error generating monthly summary: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== USER MANAGEMENT (Existing) ====================

    suspend fun getCurrentUser(): User? {
        return try {
            withContext(Dispatchers.IO) {
                if (!isClientInitialized) {
                    Log.e("SupabaseManager", "Supabase client not initialized")
                    return@withContext null
                }

                val supabaseUser = client.auth.currentUserOrNull()
                if (supabaseUser == null) {
                    Log.d("SupabaseManager", "No authenticated user found")
                    return@withContext null
                }

                try {
                    val dbUser = withTimeout(10000) {
                        client.from("user_profiles")
                            .select {
                                filter { eq("id", supabaseUser.id) }
                            }
                            .decodeSingleOrNull<User>()
                    }
                    dbUser
                } catch (ex: TimeoutCancellationException) {
                    Log.e("SupabaseManager", "Timeout fetching user from database")
                    null
                } catch (ex: Exception) {
                    Log.e("SupabaseManager", "Error fetching DB user: ${ex.message}", ex)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error getting current user: ${e.message}", e)
            null
        }
    }
    suspend fun updateUser(user: User, imageUri: Uri? = null, context: Context? = null): Result<User?> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseManager", "Updating user: ${user.id}, imageUri: $imageUri")
                var avatarUrl = user.avatarUrl

                if (imageUri != null && context != null) {
                    val uploadResult = uploadProfileImage(imageUri, user.id ?: "", context)
                    if (uploadResult.isSuccess) {
                        avatarUrl = uploadResult.getOrNull()
                    } else {
                        return@withContext Result.failure(uploadResult.exceptionOrNull() ?: Exception("Image upload failed"))
                    }
                }

                val result = client.from("user_profiles")
                    .update(
                        mapOf(
                            "full_name" to (user.fullName ?: ""),
                            "avatar_url" to avatarUrl
                        )
                    ) {
                        filter { eq("id", user.id ?: "") }
                        select() // return updated row
                    }
                    .decodeSingle<User>()

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
            Log.e("SupabaseManager", "Error signing in with Google: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun upsertUser(user: User): User? {
        return try {
            val existing = client.from("user_profiles")
                .select {
                    filter { eq("id", user.id ?: "") }
                }
                .decodeSingleOrNull<User>()

            if (existing != null) {
                client.from("user_profiles")
                    .update(
                        mapOf(
                            "updated_at" to "now()",
                            "email" to user.email,
                            "full_name" to user.fullName,
                            "avatar_url" to user.avatarUrl
                        )
                    ) {
                        filter { eq("id", user.id ?: "") }
                        select() // return updated row
                    }
                    .decodeSingle<User>()
            } else {
                client.from("user_profiles")
                    .insert(user) {
                        select() // return inserted row
                    }
                    .decodeSingle<User>()
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error upserting user: ${e.message}", e)
            null
        }
    }


    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error signing out: ${e.message}", e)
        }
    }
}

// Data class for monthly summary
data class StaffMonthlySummary(
    val month: String,
    val totalStaff: Int,
    val totalSalaryDue: Double,
    val totalPresentDays: Int,
    val totalAbsentDays: Int,
    val totalHalfDays: Int,
    val totalLateDays: Int
)