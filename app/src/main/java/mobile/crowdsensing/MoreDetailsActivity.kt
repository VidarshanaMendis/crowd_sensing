package mobile.crowdsensing

import android.app.ProgressDialog
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import com.google.firebase.database.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.atomic.AtomicLong
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import android.graphics.DashPathEffect
import android.util.Log
import android.view.View
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import kotlinx.android.synthetic.main.activity_more_details.*
import java.util.*
import android.content.Intent
import android.net.Uri
import android.widget.Toast


class MoreDetailsActivity : AppCompatActivity() {

    private val sampleSize = 10
    private var dataSnapshots: MutableList<DataSnapshot> = mutableListOf<DataSnapshot>()
    private var placeRef: DatabaseReference? = null
    private var eventListener: ChildEventListener? = null
    private var childrenCount = AtomicLong()
    private var progressDialog: ProgressDialog? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more_details)

        progressDialog = ProgressDialog(this)
        progressDialog?.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog?.setCancelable(true)
        progressDialog?.setMessage("Loading...")
        progressDialog?.show()

        chartLight.setTouchEnabled(true)
        chartLight.setPinchZoom(true)

        chartNoise.setTouchEnabled(true)
        chartNoise.setPinchZoom(true)


        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val placeId = intent.getStringExtra("PLACE_ID")
        var placeName = intent.getStringExtra("PLACE_NAME")
        var placeAddress = intent.getStringExtra("PLACE_ADDRESS")

        placeNameTxt.text = "Place: " + placeName
        placeAddresssTxt.text = "Address: " + placeAddress

        val database = FirebaseDatabase.getInstance()
        placeRef = database.getReference("$placeId")

        eventListener = placeRef
            ?.limitToLast(sampleSize)
//            ?.orderByChild("likelihood")
            ?.addChildEventListener(object : ChildEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onChildMoved(p0: DataSnapshot, p1: String?) {
                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                }

                override fun onChildRemoved(p0: DataSnapshot) {
                }

                override fun onChildAdded(dataSnapshot: DataSnapshot, string: String?) {
                    dataSnapshots.add(dataSnapshot)
                    processData()
                }
            })

        placeRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }


            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // The number of children will always be equal to 'count' since the value of
                // the dataSnapshot here will include every child_added event triggered before this point.
                childrenCount.addAndGet(dataSnapshot.childrenCount)
                processData()
            }
        })
    }

    private fun processData() {
        if(Math.min(childrenCount.get(),sampleSize.toLong()) != dataSnapshots.size.toLong()){
            return
        }
        placeRef?.removeEventListener(eventListener!!)
        val values = ArrayList<Entry>()
        var noiseValues = ArrayList<Entry>()

        var mean_lux = 0.0f
        var meanNoise = 0.0f
        var likelihood = 0.0f

        if (dataSnapshots.isNotEmpty()) {
            for (dataSnapshot in dataSnapshots) {
                var likelihoodSnapshot = dataSnapshot.child("likelihood").value.toString().toFloat()
                mean_lux =
                    mean_lux + dataSnapshot.child("light").value.toString().toFloat() * likelihoodSnapshot
                likelihood =
                    likelihood + likelihoodSnapshot
                values.add(
                    Entry(
                        dataSnapshot.key.toString().toFloat(),
                        dataSnapshot.child("light").value.toString().toFloat()
                    )
                )
                Log.i("MapsActivity", dataSnapshot.child("noise").value.toString())

                var noiseSnapshot = dataSnapshot.child("noise").value
                if (noiseSnapshot != null) {
                    meanNoise = meanNoise + noiseSnapshot.toString().toFloat() * likelihoodSnapshot
                    noiseValues.add(Entry(
                        dataSnapshot.key.toString().toFloat(),
                        noiseSnapshot.toString().toFloat()
                    ))
                }
            }
            var mean_lux_final = ((mean_lux) / (likelihood)).toInt()
            Log.i("MapsActivity", "mean lux total  " + mean_lux)
            Log.i("MapsActivity", "likelihood lux total  " + likelihood)
            Log.i("MapsActivity", "likelihood lux total  " + mean_lux_final)
            var status = getlightstatus(mean_lux_final)
            tv_status.text = status
            tv_value.text = "Average lux value is :" + mean_lux_final.toString()

            var meanNoiseFinal = ((meanNoise) / (likelihood)).toInt()
            noiseTxt.text = "Average max noise: " + meanNoiseFinal.toString() + " dB (SPL)"

            drawchart(values, chartLight, "Lux levels")
            drawchart(noiseValues, chartNoise, "Max noise in dB (SPL)")
            progressDialog?.dismiss()
        } else {
            chartLight.visibility = View.GONE
            chartNoise.visibility = View.GONE
            tv_status.visibility = View.GONE
            tv_value.visibility = View.GONE
            progressDialog?.dismiss()
            val text = "There are no captured data for this place"
            val toast = Toast.makeText(applicationContext, text, Toast.LENGTH_LONG)
            toast.show()
        }
    }

    private fun drawchart(values:ArrayList<Entry>, mChart: LineChart, label: String){
        val set1: LineDataSet
        if (mChart.getData() != null && mChart.getData().dataSetCount > 0) {
            set1 = mChart.getData().getDataSetByIndex(0) as LineDataSet
            set1.values = values
            mChart.getData().notifyDataChanged()
            mChart.notifyDataSetChanged()
        } else {
            set1 = LineDataSet(values, label)
            set1.setDrawIcons(false)
            set1.enableDashedLine(10f, 5f, 0f)
            set1.enableDashedHighlightLine(10f, 5f, 0f)
            set1.color = Color.DKGRAY
            set1.setCircleColor(Color.DKGRAY)
            set1.lineWidth = 1f
            set1.circleRadius = 3f
            set1.setDrawCircleHole(false)
            set1.valueTextSize = 9f
            set1.setDrawFilled(true)
            set1.formLineWidth = 1f
            set1.formLineDashEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
            set1.formSize = 15f
            set1.fillColor = Color.DKGRAY
            val dataSets = ArrayList<ILineDataSet>()
            dataSets.add(set1)
            val data = LineData(dataSets)
            val xAxisFormatter = DayAxisValueFormatter(mChart)
            val xAxis = mChart.getXAxis()
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setGranularity(1f);
            xAxis.setLabelRotationAngle(-45F);
            xAxis.setValueFormatter(xAxisFormatter);

            mChart.setData(data)
            mChart.invalidate()
        }



    }
    private fun getlightstatus(luxvalue:Int): String{

        if(luxvalue > 20 && luxvalue < 50){
            return "Area with dark surroundings"

        }else if(luxvalue >50 && luxvalue < 100){
            return "Simple orientation for short visits"

        }else if(luxvalue > 100 && luxvalue < 150){
            return "visual tasks are only occasionally performed work place"

        }else if(luxvalue > 150 && luxvalue < 250){
            return "Normal Office Work"

        }else if (luxvalue > 250 && luxvalue < 500){
            return "Normal Office Work"

        }else if(luxvalue > 500 && luxvalue < 750){
            return "Like Office Landscapes"

        }else if(luxvalue > 750 && luxvalue < 1000){
            return "Normal Drawing Work place"

        }else if(luxvalue > 1000 && luxvalue < 1500){
            return "Overcast Day"

        }else if (luxvalue > 10000 && luxvalue < 11000){
            return "Fully open to Daylight"

        }else {
            return ""
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun openInGoogleMap(view: View) {
        val placeName = intent.getStringExtra("PLACE_NAME")
        val placeLat = intent.getDoubleExtra("PLACE_LAT", 0.toDouble())
        val placeLng = intent.getDoubleExtra("PLACE_LNG", 0.toDouble())
        val placeLatLng = placeLat.toString() + "," + placeLng.toString()
        val uriString = "geo:" + placeLatLng + "?q=" + placeLatLng + "(" + placeName + ")"
        val gmmIntentUri = Uri.parse(uriString)
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        }
    }
}
