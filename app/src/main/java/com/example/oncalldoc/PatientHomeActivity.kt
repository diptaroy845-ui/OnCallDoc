package com.example.oncalldoc

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class PatientHomeActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var doctorsRecyclerView: RecyclerView
    private lateinit var doctorAdapter: DoctorAdapter
    private lateinit var orderNowCard: MaterialCardView
    private lateinit var doctorsListHeader: TextView
    private lateinit var specialitySpinner: Spinner
    private lateinit var findDoctorBtn: Button
    private val doctors = mutableListOf<Doctor>()

    private val specialities = arrayOf("Cardiologist", "Dermatologist", "Pediatrician", "Neurologist", "General Physician")

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                findNearbyDoctors()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_home)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        doctorsRecyclerView = findViewById(R.id.doctors_recycler_view)
        doctorsRecyclerView.layoutManager = LinearLayoutManager(this)

        orderNowCard = findViewById(R.id.order_now_card)
        doctorsListHeader = findViewById(R.id.doctors_list_header)
        specialitySpinner = findViewById(R.id.speciality_spinner)
        findDoctorBtn = findViewById(R.id.find_doctor_btn)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, specialities)
        specialitySpinner.adapter = adapter

        findDoctorBtn.setOnClickListener {
            checkPermissionAndFindDoctors()
        }
    }

    private fun checkPermissionAndFindDoctors() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                findNearbyDoctors()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun findNearbyDoctors() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val center = GeoLocation(location.latitude, location.longitude)
                        val radiusInM = 50 * 1000.0 // 50 km
                        val selectedSpeciality = specialitySpinner.selectedItem.toString()

                        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
                        val tasks = mutableListOf<com.google.android.gms.tasks.Task<QuerySnapshot>>()

                        for (b in bounds) {
                            val query = firestore.collection("users")
                                .whereEqualTo("role", "doctor")
                                .whereEqualTo("isOnline", true)
                                .whereEqualTo("speciality", selectedSpeciality)
                                .orderBy("geohash")
                                .startAt(b.startHash)
                                .endAt(b.endHash)
                            tasks.add(query.get())
                        }

                        Tasks.whenAllComplete(tasks)
                            .addOnSuccessListener { 
                                val matchingDocs = mutableListOf<Doctor>()
                                for (task in tasks) {
                                    if (task.isSuccessful) {
                                        val snap = task.result as QuerySnapshot
                                        for (doc in snap.documents) {
                                            val lat = doc.getDouble("latitude") ?: 0.0
                                            val lon = doc.getDouble("longitude") ?: 0.0

                                            val docLocation = GeoLocation(lat, lon)
                                            val distanceInM = GeoFireUtils.getDistanceBetween(docLocation, center)

                                            if (distanceInM <= radiusInM) {
                                                val doctor = doc.toObject(Doctor::class.java)
                                                if (doctor != null) {
                                                    doctor.distance = distanceInM
                                                    matchingDocs.add(doctor)
                                                }
                                            }
                                        }
                                    }
                                }

                                matchingDocs.sortBy { it.distance }
                                runOnUiThread {
                                    doctorAdapter = DoctorAdapter(matchingDocs) { doctor ->
                                        placeOrder(doctor)
                                    }
                                    doctorsRecyclerView.adapter = doctorAdapter
                                    doctorsRecyclerView.visibility = View.VISIBLE
                                    doctorsListHeader.visibility = View.VISIBLE
                                    orderNowCard.visibility = View.GONE
                                }

                            }
                    } else {
                        Toast.makeText(this, "Could not get location.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun placeOrder(doctor: Doctor) {
        val patientId = auth.currentUser?.uid
        if (patientId != null) {
            val order = hashMapOf(
                "patientId" to patientId,
                "doctorId" to doctor.uid,
                "timestamp" to System.currentTimeMillis(),
                "status" to "pending"
            )

            firestore.collection("orders")
                .add(order)
                .addOnSuccessListener {
                    Toast.makeText(this, "Order placed successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to place order: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
