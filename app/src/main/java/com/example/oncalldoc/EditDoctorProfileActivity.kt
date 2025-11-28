package com.example.oncalldoc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditDoctorProfileActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var currentPasswordInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmNewPasswordInput: EditText
    private lateinit var saveChangesBtn: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_doctor_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        nameInput = findViewById(R.id.edit_doctor_name)
        phoneInput = findViewById(R.id.edit_doctor_phone)
        currentPasswordInput = findViewById(R.id.edit_current_password)
        newPasswordInput = findViewById(R.id.edit_new_password)
        confirmNewPasswordInput = findViewById(R.id.edit_confirm_new_password)
        saveChangesBtn = findViewById(R.id.save_changes_btn)

        loadDoctorProfile()

        saveChangesBtn.setOnClickListener {
            saveChanges()
        }
    }

    private fun loadDoctorProfile() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        nameInput.setText(document.getString("name"))
                        phoneInput.setText(document.getString("phone"))
                    }
                }
                .addOnFailureListener { 
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveChanges() {
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val currentPassword = currentPasswordInput.text.toString()
        val newPassword = newPasswordInput.text.toString()
        val confirmNewPassword = confirmNewPasswordInput.text.toString()

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Name and phone cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // To change any data, we must re-authenticate the user for security.
        if (currentPassword.isEmpty()) {
            Toast.makeText(this, "Please enter your current password to save changes", Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser
        val credential = EmailAuthProvider.getCredential(user?.email!!, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                // --- User re-authenticated, now we can save the changes ---
                val userId = user.uid
                val profileUpdates = mapOf(
                    "name" to name,
                    "phone" to phone
                )

                // Update Firestore profile
                firestore.collection("users").document(userId).update(profileUpdates)
                    .addOnSuccessListener { 
                        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { 
                        Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                    }

                // Check if user wants to update password
                if (newPassword.isNotEmpty()) {
                    if (newPassword == confirmNewPassword) {
                        user.updatePassword(newPassword)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { 
                                Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                    }
                }

                finish() // Go back to the home screen
            }
            .addOnFailureListener { 
                Toast.makeText(this, "Authentication failed. Please check your current password.", Toast.LENGTH_LONG).show()
            }
    }
}
