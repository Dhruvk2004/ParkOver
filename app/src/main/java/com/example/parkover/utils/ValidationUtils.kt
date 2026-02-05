package com.example.parkover.utils

object ValidationUtils {
    
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult.Error("Email is required")
            !email.isValidEmail() -> ValidationResult.Error("Please enter a valid email")
            else -> ValidationResult.Success
        }
    }
    
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Error("Password is required")
            password.length < Constants.MIN_PASSWORD_LENGTH -> 
                ValidationResult.Error("Password must be at least ${Constants.MIN_PASSWORD_LENGTH} characters")
            else -> ValidationResult.Success
        }
    }
    
    fun validateConfirmPassword(password: String, confirmPassword: String): ValidationResult {
        return when {
            confirmPassword.isBlank() -> ValidationResult.Error("Please confirm your password")
            password != confirmPassword -> ValidationResult.Error("Passwords do not match")
            else -> ValidationResult.Success
        }
    }
    
    fun validateName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.Error("Name is required")
            name.length < 2 -> ValidationResult.Error("Name is too short")
            else -> ValidationResult.Success
        }
    }
    
    fun validatePhone(phone: String): ValidationResult {
        val cleanPhone = phone.replace(" ", "").replace("-", "")
        return when {
            cleanPhone.isBlank() -> ValidationResult.Error("Phone number is required")
            cleanPhone.length != 10 -> ValidationResult.Error("Please enter a valid 10-digit phone number")
            !cleanPhone.all { it.isDigit() } -> ValidationResult.Error("Phone number should contain only digits")
            else -> ValidationResult.Success
        }
    }
    
    fun validateVehicleNumber(number: String): ValidationResult {
        return when {
            number.isBlank() -> ValidationResult.Error("Vehicle number is required")
            !number.isValidVehicleNumber() -> 
                ValidationResult.Error("Please enter a valid vehicle number (e.g., DL01AB1234)")
            else -> ValidationResult.Success
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    
    fun isValid(): Boolean = this is Success
    
    fun getErrorOrNull(): String? = (this as? Error)?.message
}
