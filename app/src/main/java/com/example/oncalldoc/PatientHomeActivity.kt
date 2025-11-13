package com.example.oncalldoc

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class PatientHomeActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var doctorsRecyclerView: RecyclerView
    private lateinit var doctorAdapter: DoctorAdapter
    private val doctors = mutableListOf<Doctor>()

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

        doctorsRecyclerView = findViewById(R.id.doctors_recycler_view)
        doctorsRecyclerView.layoutManager = LinearLayoutManager(this)
        doctorAdapter = DoctorAdapter(doctors)
        doctorsRecyclerView.adapter = doctorAdapter

        checkPermissionAndFindDoctors()
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

                        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
                        val tasks = mutableListOf<com.google.android.gms.tasks.Task<QuerySnapshot>>()

                        for (b in bounds) {
                            val query = firestore.collection("users")
                                .whereEqualTo("role", "doctor")
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
                                    doctors.clear()
                                    doctors.addAll(matchingDocs)
                                    doctorAdapter.notifyDataSetChanged()
                                }

                            }
                    } else {
                        Toast.makeText(this, "Could not get location.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
