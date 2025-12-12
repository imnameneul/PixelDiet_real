package com.example.pixeldiet.ui.common

import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pixeldiet.model.CalendarDecoratorData
import com.example.pixeldiet.model.DayStatus
import com.example.pixeldiet.viewmodel.SharedViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
//import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.spans.DotSpan

// ----------------------
// MaterialCalendarView 래퍼
// ----------------------
// ----------------------
// 캘린더 데코레이터 데이터 클래스
// ----------------------
private class StatusDecorator(
    private val dates: Set<CalendarDay>,
    private val color: Int
) : DayViewDecorator {
    override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)
    override fun decorate(view: DayViewFacade) {
        view.addSpan(DotSpan(10f, color))
    }
}

// ----------------------
// MaterialCalendarView 래퍼
// ----------------------
@Composable
fun WrappedMaterialCalendar(
    modifier: Modifier = Modifier,
    decoratorData: List<CalendarDecoratorData>,
    onMonthChanged: (year: Int, month: Int) -> Unit = { _, _ -> },
    onDateSelected: (CalendarDay) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MaterialCalendarView(context).apply {
                topbarVisible = true
                selectionMode = MaterialCalendarView.SELECTION_MODE_SINGLE
                setCurrentDate(CalendarDay.today())

                setOnMonthChangedListener { _, date ->
                    onMonthChanged(date.year, date.month)
                }

                setOnDateChangedListener { _, date, selected ->
                    if (selected) {
                        onDateSelected(date)
                    }
                }
            }
        },
        update = { view ->
            view.removeDecorators()
            val successDays = decoratorData.filter { it.status == DayStatus.SUCCESS }.map { it.date }.toSet()
            val warningDays = decoratorData.filter { it.status == DayStatus.WARNING }.map { it.date }.toSet()
            val failDays = decoratorData.filter { it.status == DayStatus.FAIL }.map { it.date }.toSet()

            if (successDays.isNotEmpty()) view.addDecorator(StatusDecorator(successDays, Color.GREEN))
            if (warningDays.isNotEmpty()) view.addDecorator(StatusDecorator(warningDays, Color.parseColor("#FFC107")))
            if (failDays.isNotEmpty()) view.addDecorator(StatusDecorator(failDays, Color.RED))
        }
    )
}

// ----------------------
// BarChart 래퍼
// ----------------------
@Composable
fun WrappedBarChart(
    modifier: Modifier = Modifier,
    chartData: List<Entry>,
    goalLine: Float? = null
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                axisLeft.setDrawGridLines(true)
                legend.isEnabled = false
            }
        },
        update = { barChart ->
            val entries = chartData.map { BarEntry(it.x, it.y) }
            val dataSet = BarDataSet(entries, "사용 시간(분)").apply { valueTextSize = 10f }
            barChart.data = BarData(dataSet).apply { barWidth = 0.6f }

            val leftAxis = barChart.axisLeft
            leftAxis.removeAllLimitLines()

            goalLine?.let {
                leftAxis.addLimitLine(LimitLine(it, "목표").apply {
                    lineWidth = 2f
                    enableDashedLine(10f, 10f, 0f)
                    textSize = 10f
                })
            }

            val maxUsage = entries.maxOfOrNull { it.y } ?: 0f
            val maxValue = maxOf(maxUsage, goalLine ?: 0f)
            leftAxis.axisMaximum = (maxValue * 1.1f).coerceAtLeast(10f)

            barChart.invalidate()
        }
    )
}
