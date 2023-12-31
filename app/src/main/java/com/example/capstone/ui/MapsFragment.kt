package com.example.capstone.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.example.capstone.R
import com.example.capstone.model.Place
import com.example.capstone.model.Response
import com.example.capstone.services.GeofenceService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

class MapsFragment : Fragment() {

    //Firebase initialization
    private val database = Firebase.database
    private val myRef = database.getReference("capstone").child("place")

    private val mutableLiveData = MutableLiveData<Response>()

    lateinit var mGoogleMap: GoogleMap
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    private val tagToMarkers = mutableMapOf<String, Marker>()


    private val geofenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "GEOFENCE_EVENT") {
                val geofenceId = intent.getStringExtra("geofenceId")
                val entered = intent.getBooleanExtra("entered", true)

                fetchGeofenceDataFromFirebase(geofenceId, entered)
            }
        }
    }

    private fun fetchGeofenceDataFromFirebase(geofenceId: String?, entered: Boolean) {
        myRef.child(geofenceId ?: "").get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val dataSnapShot = task.result
                if (dataSnapShot != null && dataSnapShot.exists()) {
                    val lat = dataSnapShot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = dataSnapShot.child("longitude").getValue(Double::class.java) ?: 0.0
                    val placeName = dataSnapShot.child("placeName").getValue(String::class.java) ?: ""
                    val placePicture = dataSnapShot.child("pictures_0").getValue(String::class.java) ?: ""

                    val latlng = LatLng(lat, lng)

                    println("pictures found for : $placeName with $lat $lng")
                    if (entered) {
//                        val markerOptions = latlng?.let {
                        val customMarkerView = layoutInflater.inflate(R.layout.custom_marker, null)

                        val iconImageView: ImageView =
                            customMarkerView.findViewById(R.id.custom_marker_icon)
                        val titleTextView: TextView =
                            customMarkerView.findViewById(R.id.custom_marker_title)

                        // Set dynamic data to the views
                        titleTextView.text =
                            dataSnapShot.child("placeName").getValue(String::class.java)
                        iconImageView.setImageResource(
                            getCategoryIconRes(
                                dataSnapShot.child("category").getValue(String::class.java)
                                    .toString()
                            )
                        )

                        // Create a Bitmap from the custom marker layout
                        val customMarkerBitmap = createBitmapFromView(customMarkerView)


                        val markerOptions = latlng.let {
                            // Set the custom marker bitmap as the marker icon
                            MarkerOptions()
                                .position(it)
                                .title(dataSnapShot.child("placeName").getValue(String::class.java))
                                .icon(BitmapDescriptorFactory.fromBitmap(customMarkerBitmap))
                        }

                        // set event info data class object as marker tag
                        // event name, address, image
                        val customInfowWindow = geofenceId?.let {
                            CustomInfoWindow(
                                imagePreview = placePicture,
                                geofenceId = it
                            )
                        }
                        if (markerOptions != null) {
                            val marker = mGoogleMap.addMarker(markerOptions)
                            if (marker != null) {
                                marker.tag = customInfowWindow
                            } else println("marker is null")
                        } else println("markerOptions is null")

                    } else {
                        // Geofence entered, remove the marker
                        // TODO: can create a object of the addmarker and can delete that object to make it delete.
                        val markerToRemove =
                            tagToMarkers[dataSnapShot.child("placeName")
                                .getValue(String::class.java).toString()]
                        markerToRemove?.let {
                            tagToMarkers.remove(
                                dataSnapShot.child("placeName").getValue(String::class.java)
                                    .toString()
                            )
                            it.remove()
                        }
                    }
                }
            }
        }
    }

    private fun createBitmapFromView(view: View): Bitmap {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val bitmap =
            Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.draw(canvas)
        return bitmap

    }

    private fun getCategoryIconRes(category: String): Int {
        return when (category) {
            "Fun & Games" -> R.drawable.video_game
            "Hiking trails & Parks" -> R.drawable.hiking
            "Point of interest & Landmark" -> R.drawable.intresting_place_poi
            "Food & Drinks" -> R.drawable.food_drinnks
            "Shopping malls & Antique shops" -> R.drawable.online_shopping
            // Add more categories as needed
            else -> R.drawable.placeholder_600x400
        }
    }

    private var mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val locationList = locationResult.locations
            if (locationList.isNotEmpty()) {
                val location = locationList.last()
                // Create a LatLng object for the current location
                val currentLatLng = LatLng(location!!.latitude, location.longitude)
                // Move the camera to the current location
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                mFusedLocationClient?.removeLocationUpdates(this)

            }
        }
    }

    private val callback = OnMapReadyCallback { mMap ->

        mGoogleMap = mMap
        mGoogleMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        mGoogleMap.setMinZoomPreference(13.0f)
        // Load custom map style from resources
        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireActivity(), R.raw.map_style
                )
            )

            if (success) {
                // Custom map style applied successfully
                Toast.makeText(
                    requireContext(),
                    "Custom map style applied successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Handle map style loading failure
                Toast.makeText(
                    requireContext(),
                    "Failed to apply custom map style",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Resources.NotFoundException) {
            // Handle exception (e.g., resource not found)
            Toast.makeText(requireContext(), "Resource not found: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            // Handle other exceptions (e.g., parsing or IO errors)
            Toast.makeText(
                requireContext(),
                "Error loading custom map style: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }

        enableMyLocation()

        // Set custom InfoWindowAdapter
        mGoogleMap.setInfoWindowAdapter(CustomInfoWindowAdapter())

        mGoogleMap.setOnInfoWindowClickListener(infoWindowClickListener)

    }

    private val infoWindowClickListener = GoogleMap.OnInfoWindowClickListener { marker ->
        val customInfoWindow = marker.tag as? CustomInfoWindow
        customInfoWindow?.let {
            loadFragment(PlaceDetailsFragment(), it.geofenceId)
        }
    }

    //Custom InfoWindowAdapter class
    inner class CustomInfoWindowAdapter : GoogleMap.InfoWindowAdapter {

        private val mInflater: LayoutInflater =
            requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        @SuppressLint("MissingInflatedId")
        override fun getInfoContents(p0: Marker): View? {
            val customView = mInflater.inflate(R.layout.custom_info_window, null)

            // Get data from the marker (assuming you have a custom Marker class)
            val customInfoWindow = p0.tag as? CustomInfoWindow// Your custom marker class
            if (customInfoWindow != null) {
                val iconImageView: ImageView = customView.findViewById(R.id.imageViewIcon)
                Picasso.get().load(customInfoWindow.imagePreview).into(iconImageView)
//                iconImageView.setImageResource(customInfoWindow.imagePreview)

                val textView: TextView = customView.findViewById(R.id.textViewForTitle)
                textView.text = customInfoWindow.geofenceId

            }
            return customView
        }

        override fun getInfoWindow(p0: Marker): View? {
            return null
        }
    }

    data class CustomInfoWindow(
        val imagePreview: String,
        val geofenceId: String
    )

    private fun enableMyLocation() {
        if (checkPermissions()) {
            val locationRequest = LocationRequest.create()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(
                        requireActivity(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    mFusedLocationClient?.requestLocationUpdates(
                        locationRequest,
                        mLocationCallback,
                        Looper.myLooper()
                    )
                    mGoogleMap.isMyLocationEnabled = true
                } else {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSIONS_REQUEST_LOCATION
                    )
                }
            } else {
                mFusedLocationClient?.requestLocationUpdates(
                    locationRequest,
                    mLocationCallback,
                    Looper.myLooper()
                )
                mGoogleMap.isMyLocationEnabled = true
            }
        } else {
            // Handle permission denied
            // You can display a message or request permission again here
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        getResponseFromRealtimeDatabaseUsingLiveData()
        requireContext().startService(Intent(requireContext(), GeofenceService::class.java))
        val filter = IntentFilter("GEOFENCE_EVENT")
        requireContext().registerReceiver(geofenceReceiver, filter)

        //(activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        /*val fab = requireActivity().findViewById<FloatingActionButton>(R.id.floatingActionButton)
        fab.setOnClickListener {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_mapss, AddPlaceFragment())
                .addToBackStack(tag)

                .commit()
        }*/

        if (this::mGoogleMap.isInitialized) {
            mGoogleMap.setOnInfoWindowClickListener {
                loadFragment(PlaceDetailsFragment(), it.id)
            }

            mGoogleMap.setOnMarkerClickListener { marker ->
                marker.showInfoWindow()
                return@setOnMarkerClickListener true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().stopService(Intent(requireContext(), GeofenceService::class.java))
        requireActivity().unregisterReceiver(geofenceReceiver)
    }

    private fun loadFragment(fragment: Fragment, geofenceId: String) {
        val bundle = Bundle()
        bundle.putString("geofenceId", geofenceId)
        fragment.arguments = bundle
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun getResponseFromRealtimeDatabaseUsingLiveData(): MutableLiveData<Response> {

        myRef.child("place").get().addOnCompleteListener { task ->
            val response = Response()
            if (task.isSuccessful) {
                val result = task.result
                result?.let {
                    response.list = result.children.map { snapShot ->
                        snapShot.getValue(Place::class.java)!!
                    }
                }
            } else {
                response.exception = task.exception
            }
            mutableLiveData.value = response
            val mapFragment =
                childFragmentManager.findFragmentById(R.id.explorePlaces) as SupportMapFragment?
            mapFragment?.getMapAsync(callback)

        }
        return mutableLiveData
    }

    companion object {
        private const val PERMISSIONS_REQUEST_LOCATION = 123
    }

    private fun checkPermissions(): Boolean {
        return (ActivityCompat.checkSelfPermission(
            requireActivity(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
            requireActivity(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED)
    }
}