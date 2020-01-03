package mobile.crowdsensing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.android.synthetic.main.activity_main.*
import android.app.ActivityManager
import android.content.Context


class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback,
    AdapterView.OnItemClickListener {

    private lateinit var placesClient: PlacesClient
    private var listItems: MutableList<String> = mutableListOf<String>()
    private var listAutocompletePredictions: MutableList<AutocompletePrediction> =
        mutableListOf<AutocompletePrediction>()
    private var listItemsAll: MutableList<String> = mutableListOf<String>()
    private var listAutocompletePredictionsAll: MutableList<AutocompletePrediction> =
        mutableListOf<AutocompletePrediction>()
    private var isShowAllResults: Boolean = false

    var PERMISSION_ACCESS_FINE_LOCATION = 0
    var PERMISSION_RECORD_AUDIO = 1

    private val TRIGGER_SERACH = 1
    private val SEARCH_TRIGGER_DELAY_IN_MS: Long = 1000

    private val handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what === TRIGGER_SERACH) {
                if (editText.text.isNotEmpty()) {
                    sendAutoCompleteQuery()
                } else {
                    clearLists()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar.visibility = View.GONE
        searchBtn.visibility = View.GONE
        textView.visibility = View.GONE
        showAllBtn.visibility = View.GONE

        Log.i("MotionSensorService-",android.os.Process.myPid().toString())
        Log.i("MotionSensorService-",android.os.Process.myUid().toString())
        Log.i("MotionSensorService-",android.os.Process.myTid().toString())

//        if (!isMyServiceRunning(MotionSensorService::class.java)) {
            val intent = Intent(applicationContext, MotionSensorService::class.java)
            startService(intent)
//        }

        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)

        listView.setOnItemClickListener(this)
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                Log.i("MainActivity", "afterTextChanged")
                handler.removeMessages(TRIGGER_SERACH)
                handler.sendEmptyMessageDelayed(TRIGGER_SERACH, SEARCH_TRIGGER_DELAY_IN_MS)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        Log.i(
            "MainActivity", String.format(
                "onItemClick: '%d'",
                position
            )
        )
        var placeId = if (isShowAllResults) {
            listAutocompletePredictionsAll[position].placeId
        } else {
            listAutocompletePredictions[position].placeId
        }

        val intent = Intent(applicationContext, MapsActivity::class.java).apply {
            putExtra("PLACE_ID", placeId)
            putExtra("CURRENT_PLACE", false.toString())
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        getLocationPermission()
        getRecordAudioPermission()
    }

    fun nearbySearch(view: View) {
        val intent = Intent(applicationContext, MapsActivity::class.java).apply {
            putExtra("CURRENT_PLACE", true.toString())
        }
        startActivity(intent)
    }

    fun search(view: View) {
        sendAutoCompleteQuery()
    }

    fun showAllResults(view: View) {
        if (!isShowAllResults) {
            isShowAllResults = true
            showAllBtn.text = "Hide all results"
            listView.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listItemsAll)
        } else {
            isShowAllResults = false
            showAllBtn.text = "Show all results"
            listView.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listItems)
        }
    }

    private fun sendAutoCompleteQuery() {
        searchBtn.visibility = View.GONE
        textView.visibility = View.GONE
        showAllBtn.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(editText.text.toString())
            .build()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
                clearLists()
                for (autocompletePrediction in response.autocompletePredictions) {
                    Log.i(
                        "MainActivity", String.format(
                            "Place '%s'",
                            autocompletePrediction.a()
                        )
                    )

                    for (type in autocompletePrediction.placeTypes) {
                        if (AppConstant.TYPES.contains(type.toString())) {
                            listAutocompletePredictions.add(autocompletePrediction)
                            listItems.add(autocompletePrediction.a())
                            break
                        }
                    }
                    listAutocompletePredictionsAll.add(autocompletePrediction)
                    listItemsAll.add(autocompletePrediction.a())
                }
                if (listItems.isEmpty()) {
                    textView.text = "No results found which are related to restaurant."
                    textView.visibility = View.VISIBLE
                    if (listItemsAll.isNotEmpty()) {
                        showAllBtn.visibility = View.VISIBLE
                    }
                }

                listView.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listItems)
                progressBar.visibility = View.GONE

            }.addOnFailureListener { exception ->
                if (exception is ApiException) {
                    Log.e("MainActivity", "Place not found: " + exception.statusCode)
                }
                textView.text = "Error occurred. Please search again."
                textView.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                searchBtn.visibility = View.VISIBLE
            }
        } else {
            progressBar.visibility = View.GONE
        }
    }

    private fun clearLists() {
        listItems.clear()
        listItemsAll.clear()
        listAutocompletePredictions.clear()
        listAutocompletePredictionsAll.clear()
        listView.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listItems)
    }

    private fun getLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                val text = "Location permission is required for application functionality"
                val toast = Toast.makeText(applicationContext, text, Toast.LENGTH_LONG)
                toast.show()

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_ACCESS_FINE_LOCATION
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_ACCESS_FINE_LOCATION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_ACCESS_FINE_LOCATION) {
            if (!(grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                textView.text = "This application not working without location permission."
                textView.visibility = View.VISIBLE
            }
        }
    }

    private fun getRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PERMISSION_RECORD_AUDIO
                )
            }
        }
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
