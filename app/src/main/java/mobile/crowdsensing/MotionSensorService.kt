package mobile.crowdsensing

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
import android.media.MediaRecorder
import android.os.IBinder
import android.support.v4.content.ContextCompat
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.database.FirebaseDatabase
import java.io.IOException
import java.lang.Exception
import java.util.*


class MotionSensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometerSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var temperatureSensor: Sensor? = null
    private var humiditySensor: Sensor? = null
    private var lightSensor: Sensor? = null

    private var acceleration: Float = 0.toFloat()
    private var accelerationCurrent: Float = 0.toFloat()
    private var accelerationLast: Float = 0.toFloat()

    private var processing: Boolean = false

    private var temperatureReadings: MutableList<Float> = mutableListOf<Float>()
    private var humidityReadings: MutableList<Float> = mutableListOf<Float>()
    private var lightReadings: MutableList<Float> = mutableListOf<Float>()
    private var splReading: Int? = null // Sound pressure level

    private lateinit var placesClient: PlacesClient
    private var restaurantPlaces: MutableList<PlaceLikelihood> = mutableListOf<PlaceLikelihood>()

    private val SAMPLE_SIZE = 10

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor!!.type === Sensor.TYPE_ACCELEROMETER) {
            val x = event!!.values[0]
            val y = event!!.values[1]
            val z = event!!.values[2]
            accelerationLast = accelerationCurrent
            accelerationCurrent = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = accelerationCurrent - accelerationLast
            acceleration = acceleration * 0.9f + delta
            if (acceleration > 3) {
                sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else if (event?.sensor!!.type === Sensor.TYPE_PROXIMITY) {
            val distance = event!!.values[0]
            if (!distance.equals(0.toFloat())) {
                checkPlace()
            }
            sensorManager.unregisterListener(this, proximitySensor)
            Log.i("MotionSensorService", "TYPE_PROXIMITY: $distance")

        } else if (event?.sensor!!.type === Sensor.TYPE_AMBIENT_TEMPERATURE) {
            val x = event!!.values[0]
            if (temperatureReadings.size < SAMPLE_SIZE) {
                temperatureReadings.add(x)
                Log.i("MotionSensorService", "TYPE_AMBIENT_TEMPERATURE: $x")
            } else {
                sensorManager.unregisterListener(this, temperatureSensor)
                getReadings()
            }
        } else if (event?.sensor!!.type === Sensor.TYPE_RELATIVE_HUMIDITY) {
            val x = event!!.values[0]
            if (humidityReadings.size < SAMPLE_SIZE) {
                humidityReadings.add(x)
                Log.i("MotionSensorService", "TYPE_RELATIVE_HUMIDITY: $x")
            } else {
                sensorManager.unregisterListener(this, humiditySensor)
                getReadings()
            }
        } else if (event?.sensor!!.type === Sensor.TYPE_LIGHT) {
            val x = event!!.values[0]
            if (lightReadings.size < SAMPLE_SIZE) {
                lightReadings.add(x)
                Log.i("MotionSensorService", "TYPE_LIGHT: $x")
            } else {
                sensorManager.unregisterListener(this, lightSensor)
                getReadings()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("MotionSensorService", "onCreate()")

//        Log.i("MotionSensorService",android.os.Process.myPid().toString())
//        Log.i("MotionSensorService",android.os.Process.myUid().toString())
//        Log.i("MotionSensorService",android.os.Process.myTid().toString())

        // Initialize Places instance
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // Detect motion without using `TYPE_SIGNIFICANT_MOTION`
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // To check whether device in pocket
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        // Crowd sensing sensors
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        acceleration = 0.00f
        accelerationCurrent = SensorManager.GRAVITY_EARTH
        accelerationLast = SensorManager.GRAVITY_EARTH
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i("MotionSensorService", "onStartCommand()")

        Log.i("MotionSensorService",android.os.Process.myPid().toString())
        Log.i("MotionSensorService",android.os.Process.myUid().toString())
        Log.i("MotionSensorService",android.os.Process.myTid().toString())
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL)

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun checkPlace() {
        if (processing) {
            return
        }
        processing = true

        val placeFields = Arrays.asList(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.TYPES
        )

        val request = FindCurrentPlaceRequest.builder(placeFields).build()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            placesClient.findCurrentPlace(request).addOnSuccessListener { response ->

                for (placeLikelihood in response.placeLikelihoods) {
                    Log.i(
                        "MotionSensorService", String.format(
                            "Place '%s' has likelihood: %f, latLng: %s,add: %s, type: %s\n\n",
                            placeLikelihood.place.name,
                            placeLikelihood.likelihood,
                            placeLikelihood.place.latLng.toString(),
                            placeLikelihood.place.address.toString(),
                            placeLikelihood.place.types.toString()
                        )
                    )
                    for (type in placeLikelihood.place.types!!) {
                        if (AppConstant.TYPES.contains(type.toString())) {
                            restaurantPlaces.add(placeLikelihood)
                            break
                        }
                    }
                }

                if (restaurantPlaces.size > 0) {
                    registerSensorListeners()
                } else {
                    processing = false
                }


            }.addOnFailureListener { exception ->
                processing = false
                if (exception is ApiException) {
                    Log.e("MotionSensorService", "Place not found: " + exception.statusCode)
                }
            }
        }
    }

    private fun getReadings() {
        if (temperatureSensor != null && temperatureReadings.size != SAMPLE_SIZE) {
            Log.i("MotionSensorService", "getReadings() return 1")
            return
        }
        if (humiditySensor != null && humidityReadings.size != SAMPLE_SIZE) {
            Log.i("MotionSensorService", "getReadings() return 2")
            return
        }
        if (lightSensor != null && lightReadings.size != SAMPLE_SIZE) {
            Log.i("MotionSensorService", "getReadings() return 3")
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getNoiseReadings()
        } else {
            pushReadings()
        }
    }

    private fun getNoiseReadings() {
        var recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile("/dev/null")
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setMaxDuration(2000)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e("MotionSensorService", "prepare() failed")
            }

            start()
            getMaxAmplitude()
        }
        recorder.setOnInfoListener { mr, what, extra ->
            splReading = mr.maxAmplitude
            mr.stop()
            mr.release()
            pushReadings()
        }
    }

    private fun pushReadings() {
        try {
            val database = FirebaseDatabase.getInstance()
            for (place in restaurantPlaces) {
                val placeId = place.place.id
                Log.i("MotionSensorService", placeId)
                val placeLikelihood = place.likelihood
                val date = Date()
                val timestamp = date.time
                val placeRef = database.getReference("$placeId/$timestamp")
                placeRef.child("likelihood").setValue(placeLikelihood)

                if (temperatureSensor != null) {
                    placeRef.child("temperature").setValue(temperatureReadings.average())

                }
                if (humiditySensor != null) {
                    placeRef.child("humidity").setValue(humidityReadings.average())

                }
                if (lightSensor != null) {
                    placeRef.child("light").setValue(lightReadings.average())

                }
                if (splReading != null) {
                    val maxNoise = 20 * Math.log10((Math.abs(splReading!!)).toDouble())
                    placeRef.child("noise").setValue(maxNoise)
                }
            }
        } catch (e: Exception) {

        } finally {
            // Clearing
            restaurantPlaces.clear()
            temperatureReadings.clear()
            humidityReadings.clear()
            lightReadings.clear()
            splReading = null

            // Finish reading
            processing = false
        }
    }

    private fun registerSensorListeners() {
        if (temperatureSensor != null) {
            sensorManager.registerListener(this, temperatureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (humiditySensor != null) {
            sensorManager.registerListener(this, humiditySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onDestroy() {
        Log.i("MotionSensorService", "onDestroy()")
        val broadcastIntent = Intent("mobile.crowdsensing.RestartService")
        sendBroadcast(broadcastIntent)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        Log.i("MotionSensorService", "onTaskRemoved()")
        val broadcastIntent = Intent("mobile.crowdsensing.RestartService")
        sendBroadcast(broadcastIntent)
        super.onTaskRemoved(rootIntent)

    }
}
