package com.example.oncalldoc

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
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
    private lateinit var backButton: ImageButton
    private lateinit var activeLocationButton: Button
    private var pendingLocationAction: (() -> Unit)? = null

    private val doctors = mutableListOf<Doctor>()
    private val specialities = arrayOf("Cardiologist", "Dermatologist", "Pediatrician", "Neurologist", "General Physician")

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                pendingLocationAction?.let { executeWithLocationSettingsCheck(it) }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val locationSettingsLauncher = 
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                pendingLocationAction?.invoke()
            } else {
                Toast.makeText(this, "Location must be enabled for this feature.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_home)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        backButton = findViewById(R.id.backFromPatientHome)
        backButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        doctorsRecyclerView = findViewById(R.id.doctors_recycler_view)
        doctorsRecyclerView.layoutManager = LinearLayoutManager(this)

        orderNowCard = findViewById(R.id.order_now_card)
        doctorsListHeader = findViewById(R.id.doctors_list_header)
        specialitySpinner = findViewById(R.id.speciality_spinner)
        findDoctorBtn = findViewById(R.id.find_doctor_btn)
        activeLocationButton = findViewById(R.id.active_location_button)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, specialities)
        specialitySpinner.adapter = adapter

        findDoctorBtn.setOnClickListener {
            checkLocationPermissionAndSettings { findNearbyDoctors() }
        }

        activeLocationButton.setOnClickListener {
            checkLocationPermissionAndSettings { fetchAndShowCurrentUserLocation() }
        }
    }

    private fun checkLocationPermissionAndSettings(action: () -> Unit) {
        pendingLocationAction = action
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                executeWithLocationSettingsCheck(action)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun executeWithLocationSettingsCheck(action: () -> Unit) {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { 
            action.invoke()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun fetchAndShowCurrentUserLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val lat = location.latitude
                    val long = location.longitude
                    Toast.makeText(this, "Current Location: Lat: $lat, Lon: $long", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Could not get location. Make sure location is enabled.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun findNearbyDoctors() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
           return
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
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
