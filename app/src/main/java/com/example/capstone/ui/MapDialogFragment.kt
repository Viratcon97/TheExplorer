package com.example.capstone.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.capstone.R
import com.example.capstone.databinding.FragmentMapDialogBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class MapDialogFragment : DialogFragment() {

    private lateinit var fragmentMapDialogBinding: FragmentMapDialogBinding

    private var mLocationRequest: LocationRequest? = null

    private val UPDATE_INTERVAL = (10 * 1000).toLong()  /* 10 secs */
    private val FASTEST_INTERVAL: Long = 2000 /* 2 sec */

    //Get location
    private val permissionId = 42
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var latitude = 0.0
    private var longitude = 0.0

    private lateinit var latLng : LatLng
    private lateinit var mGoogleMap: GoogleMap

    lateinit var geocoder: Geocoder
    var addresses: List<Address>? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        fragmentMapDialogBinding = FragmentMapDialogBinding.inflate(inflater, container, false)
        return fragmentMapDialogBinding.root
    }

    private fun getAddress(latLng: LatLng): String {
        addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

        val address = addresses?.get(0)?.getAddressLine(0)

        return address!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.location) as SupportMapFragment?
        fragmentMapDialogBinding.ivCloseMap.setOnClickListener {
            dismiss()
        }

        //Declaring Geo coder
        geocoder = Geocoder(requireActivity(), Locale.getDefault())


        //Declaring fusedLocation
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        //Map Callback
        mapFragment?.getMapAsync {mMap ->
            mGoogleMap = mMap

            //Get Last location
            getLastLocation()

            mGoogleMap.setOnMapLongClickListener {
                mGoogleMap.clear()
                val address = getAddress(it)
                latLng = it
                mGoogleMap.addMarker(MarkerOptions().position(it).title(address).icon(BitmapDescriptorFactory
                    .defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)))?.showInfoWindow()


            }
        }
        fragmentMapDialogBinding.btnAddLocation.setOnClickListener {
            parentFragmentManager.setFragmentResult("requestKey", bundleOf("latitude" to latLng.latitude,"longitude" to latLng.longitude))
            dismiss()
        }


    }


    override fun onDestroy() {
        super.onDestroy()
        if (isAdded && !isDetached) dismiss()
    }


    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {

                fusedLocationClient.lastLocation.addOnCompleteListener(requireActivity()) { task ->
                    val location: Location? = task.result
                    if (location != null) {
                        latLng = LatLng(location.latitude,location.longitude)
                        latitude = location.latitude
                        longitude = location.longitude
                        mGoogleMap.addMarker(
                            MarkerOptions().position(LatLng(latitude, longitude)).title("Current Location")
                        )?.showInfoWindow()
                        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude),17f))
                    }
                }
            } else {
                Toast.makeText(requireActivity(), "Turn on location", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermissions()
        }
    }


/*    private val callback = OnMapReadyCallback { mMap ->
        mGoogleMap = mMap
        mGoogleMap.addMarker(
            MarkerOptions().position(LatLng(latitude, longitude)).title("Current Location")
        )
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)))
    }*/

    private fun checkPermissions(): Boolean {
        if (
            ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission
                (
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            permissionId
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }
}