package com.example.oncalldoc

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginBtn: Button
    private lateinit var signupRedirect: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_page)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Bind UI
        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        loginBtn = findViewById(R.id.login_btn)
        signupRedirect = findViewById(R.id.signup_btn)
        loadingSpinner = findViewById(R.id.loading_spinner)

        // Login Button
        loginBtn.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.please_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loadingSpinner.visibility = View.VISIBLE
            loginBtn.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: ""

                        // Fetch role from Firestore
                        firestore.collection("users").document(uid).get()
                            .addOnSuccessListener { doc ->
                                val role = doc.getString("role") ?: "patient"
                                val name = doc.getString("name")

                                Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                                loadingSpinner.visibility = View.GONE
                                loginBtn.isEnabled = true

                                if (name == null) {
                                    if (role == "patient") {
                                        startActivity(Intent(this, CreatePatientProfileActivity::class.java))
                                    } else {
                                        startActivity(Intent(this, CreateDoctorProfileActivity::class.java))
                                    }
                                } else {
                                    if (role == "patient") {
                                        startActivity(Intent(this, PatientHomeActivity::class.java))
                                    } else {
                                        startActivity(Intent(this, DoctorHomeActivity::class.java))
                                    }
                                }
                                finish() // This line is now restored to fix the crash.
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, getString(R.string.error_fetching_role, e.message), Toast.LENGTH_SHORT).show()
                                loadingSpinner.visibility = View.GONE
                                loginBtn.isEnabled = true
                            }
                    } else {
                        Toast.makeText(this, getString(R.string.login_failed, task.exception?.message), Toast.LENGTH_SHORT).show()
                        loadingSpinner.visibility = View.GONE
                        loginBtn.isEnabled = true
                    }
                }
        }

        // Redirect to Signup
        signupRedirect.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }
    }
}
