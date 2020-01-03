package mobile.crowdsensing

import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.formatter.ValueFormatter
//import jdk.nashorn.internal.objects.NativeDate.getTime
import java.security.Timestamp
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.*


class DayAxisValueFormatter(private val chart: LineChart) : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        val dateFormat = SimpleDateFormat("MM/dd HH:mm")
        val d = Date(value.toLong())

        return "${dateFormat.format(d).toString()}"
    }
}