package mobile.crowdsensing

import android.Manifest
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private var place: Place? = null
    private var restaurantPlaces: MutableList<PlaceLikelihood> = mutableListOf<PlaceLikelihood>()
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        progressDialog = ProgressDialog(this)
        progressDialog?.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog?.setCancelable(false)
        progressDialog?.setMessage("Loading...")
        progressDialog?.show()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            Places.initialize(applicationContext, getString(R.string.google_maps_key))
            val placesClient = Places.createClient(this)

            val placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.TYPES
            )

            val isCurrentPlace = intent.getStringExtra("CURRENT_PLACE")
            if (isCurrentPlace.equals(false.toString())) {
                val placeId = intent.getStringExtra("PLACE_ID")

                var request = FetchPlaceRequest.builder(placeId, placeFields).build()

                placesClient.fetchPlace(request).addOnSuccessListener { response ->

                    place = response.place

                    mMap.addMarker(MarkerOptions().position(response.place.latLng!!).title(response.place.name))
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(response.place.latLng!!))

                    progressDialog?.dismiss()

                }.addOnFailureListener { exception ->
                    if (exception is ApiException) {
                        Log.e("MapsActivity", "Place not found: " + exception.statusCode)
                    }

                    progressDialog?.dismiss()
                    showErrorAlert(exception.message)
                }
            } else {
                val request = FindCurrentPlaceRequest.builder(placeFields).build()

                placesClient.findCurrentPlace(request).addOnSuccessListener { response ->
                    for (placeLikelihood in response.placeLikelihoods) {
                        Log.i(
                            "MapsActivity", String.format(
                                "Place '%s' has likelihood: %f, %s",
                                placeLikelihood.place.name,
                                placeLikelihood.likelihood,
                                placeLikelihood.place.types.toString()
                            )
                        )

                        for (type in placeLikelihood.place.types!!) {
                            if (AppConstant.TYPES.contains(type.toString())) {
                                restaurantPlaces.add(placeLikelihood)
                                mMap.addMarker(
                                    MarkerOptions()
                                        .position(placeLikelihood.place.latLng!!)
                                        .title(placeLikelihood.place.name)
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                                )
                                mMap.moveCamera(CameraUpdateFactory.newLatLng(placeLikelihood.place.latLng!!))
                                break
                            }
                        }
                    }

                    if (restaurantPlaces.isEmpty()) {
                        val text = "We are sorry, there are no restaurants around you."
                        val toast = Toast.makeText(applicationContext, text, Toast.LENGTH_LONG)
                        toast.show()

                        goMainActivity()
                    } else {
                        progressDialog?.dismiss()
                    }
                }.addOnFailureListener { exception ->
                    if (exception is ApiException) {
                        Log.e("MapsActivity", "Place not found: " + exception.statusCode)
                    }

                    progressDialog?.dismiss()
                    showErrorAlert(exception.message)
                }
            }
        } else {
            goMainActivity()
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMarkerClickListener(this)
    }

    override fun onMarkerClick(marker: Marker?): Boolean {
        val isCurrentPlace = intent.getStringExtra("CURRENT_PLACE")
        var placeId: String? = null
        var placeName: String? = null
        var placeAddress: String? = null
        var placeLatLng: LatLng? = null

        if (isCurrentPlace.equals(false.toString())) {
            placeId = place!!.id
            placeName = place!!.name
            placeAddress = place!!.address
            placeLatLng = place!!.latLng

        } else {
            for (place in restaurantPlaces) {
                if (place.place.name == marker!!.title) {
                    placeId = place.place.id
                    placeName = place.place.name
                    placeAddress = place.place.address
                    placeLatLng = place.place.latLng
                    break
                }
            }
        }

        val intent = Intent(applicationContext, MoreDetailsActivity::class.java).apply {
            putExtra("PLACE_ID", placeId)
            putExtra("PLACE_NAME", placeName)
            putExtra("PLACE_ADDRESS", placeAddress)
            putExtra("PLACE_LAT", placeLatLng?.latitude)
            putExtra("PLACE_LNG", placeLatLng?.longitude)
        }
        startActivity(intent)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == android.R.id.home) {
            goMainActivity()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun goMainActivity() {
        val intent = Intent(applicationContext, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showErrorAlert(message:String?) {
        val alertDialog: AlertDialog? = this?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setPositiveButton("Ok",
                    DialogInterface.OnClickListener { dialog, id ->
                        goMainActivity()
                    })
            }

            builder.setTitle("Error").setMessage(message)
            builder.create()
        }

        alertDialog?.show()
    }
}
