package mobile.crowdsensing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ServiceRestartBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("MotionSensorService", "Service Stops, let's restart again.")
        context?.startService(Intent(context, MotionSensorService::class.java))
    }
}