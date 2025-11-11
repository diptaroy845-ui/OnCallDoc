package com.example.oncalldoc

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var signupBtn: Button
    private lateinit var loginRedirect: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var roleGroup: RadioGroup
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup_page)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Bind UI
        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        confirmPasswordInput = findViewById(R.id.confirm_password_input)
        signupBtn = findViewById(R.id.signup_btn)
        loginRedirect = findViewById(R.id.login_redirect)
        loadingSpinner = findViewById(R.id.loading_spinner)
        roleGroup = findViewById(R.id.role_group)

        // Signup Button
        signupBtn.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()
            val selectedRoleId = roleGroup.checkedRadioButtonId

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedRoleId == -1) {
                Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val role = if (selectedRoleId == R.id.radio_patient) "patient" else "doctor"

            // Show loading
            loadingSpinner.visibility = View.VISIBLE
            signupBtn.isEnabled = false

            // Firebase Signup
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: ""
                        val userMap = hashMapOf(
                            "email" to email,
                            "role" to role
                        )

                        // Save user role in Firestore
                        firestore.collection("users").document(uid)
                            .set(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Signup successful!", Toast.LENGTH_SHORT).show()
                                loadingSpinner.visibility = View.GONE
                                signupBtn.isEnabled = true

                                // Redirect based on role
                                if (role == "patient") {
                                    startActivity(Intent(this, PatientHomeActivity::class.java))
                                } else {
                                    startActivity(Intent(this, DoctorHomeActivity::class.java))
                                }
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error saving user: ${e.message}", Toast.LENGTH_SHORT).show()
                                loadingSpinner.visibility = View.GONE
                                signupBtn.isEnabled = true
                            }
                    } else {
                        Toast.makeText(this, "Signup failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        loadingSpinner.visibility = View.GONE
                        signupBtn.isEnabled = true
                    }
                }
        }

        // Redirect to Login
        loginRedirect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
