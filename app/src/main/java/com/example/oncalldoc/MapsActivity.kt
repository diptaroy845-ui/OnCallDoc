package com.example.oncalldoc

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.QuerySnapshot

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private var patientLocation: Location? = null
    private lateinit var selectedSpeciality: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()

        selectedSpeciality = intent.getStringExtra("SPECIALITY") ?: ""

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getCurrentPatientLocation()
    }

    private fun getCurrentPatientLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        patientLocation = location
                        val patientLatLng = LatLng(location.latitude, location.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(patientLatLng, 15f))
                        mMap.addMarker(MarkerOptions().position(patientLatLng).title("Your Location"))

                        // Add a 2km radius circle around the patient
                        mMap.addCircle(
                            CircleOptions()
                                .center(patientLatLng)
                                .radius(2000.0) // 2km in meters
                                .strokeColor(Color.BLUE)
                                .strokeWidth(2f)
                                .fillColor(Color.parseColor("#220000FF")) // Semi-transparent blue
                        )

                        findAndDisplayDoctors()
                    } else {
                        Toast.makeText(this, "Could not get your location.", Toast.LENGTH_SHORT).show()
                    }
                }
        } 
    }

    private fun findAndDisplayDoctors() {
        val center = GeoLocation(patientLocation!!.latitude, patientLocation!!.longitude)
        // We still search in a wider radius to find all doctors, but the circle on the map is 2km
        val radiusInM = 50 * 1000.0 

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
                for (task in tasks) {
                    if (task.isSuccessful) {
                        val snap = task.result as QuerySnapshot
                        for (doc in snap.documents) {
                            val lat = doc.getDouble("latitude") ?: 0.0
                            val lon = doc.getDouble("longitude") ?: 0.0
                            val doctorName = doc.getString("name") ?: "Doctor"

                            val docLocation = Location("")
                            docLocation.latitude = lat
                            docLocation.longitude = lon

                            val distanceInM = patientLocation!!.distanceTo(docLocation)

                            val doctorLatLng = LatLng(lat, lon)
                            var markerTitle = doctorName
                            if(distanceInM > 2000){
                                markerTitle += " (more than 2km away)"
                            }

                            mMap.addMarker(
                                MarkerOptions()
                                .position(doctorLatLng)
                                .title(markerTitle)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            )
                        }
                    }
                }
            }
    }
}
