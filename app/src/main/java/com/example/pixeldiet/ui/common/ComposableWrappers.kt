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
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter



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
    chartData: List<Entry>,          // usage bars
    goalSeries: List<Entry> = emptyList() // ✅ 날짜별 목표 line
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CombinedChart(context).apply {
                description.isEnabled = false
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                axisLeft.setDrawGridLines(true)
                legend.isEnabled = true     // ✅ 범례 켜기
                axisLeft.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val hours = value / 60f
                        return String.format("%.1f시간", hours) // 1.5시간 같은 형태
                    }
                }
            }
        },
        update = { chart ->
            // 1) Bars (usage)
            val barEntries = chartData.map { BarEntry(it.x, it.y) }
            val barDataSet = BarDataSet(barEntries, "사용 시간").apply {
                valueTextSize = 10f
            }
            barDataSet.valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry): String {
                    val hours = barEntry.y / 60f
                    return String.format("%.1f", hours) // 막대 위에 1.5 이런 식
                }
            }
            val barData = BarData(barDataSet).apply { barWidth = 0.6f }

            // 2) Line (goal series)
            val lineEntries = goalSeries
                .sortedBy { it.x }
                .map { Entry(it.x, it.y) }

            val lineData = if (lineEntries.isNotEmpty()) {
                val lineDataSet = LineDataSet(lineEntries, "목표 시간").apply {
                    lineWidth = 2f
                    setDrawValues(false)
                    setDrawCircles(true)
                    circleRadius = 3f

                    // ✅ 목표시간 그래프 색상: 빨간색
                    color = android.graphics.Color.RED
                    setCircleColor(android.graphics.Color.RED)
                    enableDashedLine(8f, 4f, 0f) // 점선
                }
                LineData(lineDataSet)
            } else {
                LineData()
            }

            // 3) Combine
            val combined = CombinedData().apply {
                setData(barData)
                setData(lineData)
            }
            chart.data = combined

            // 4) Axis max: bars와 goals 중 큰 값 기준
            val maxUsage = barEntries.maxOfOrNull { it.y } ?: 0f
            val maxGoal = lineEntries.maxOfOrNull { it.y } ?: 0f
            val maxValue = maxOf(maxUsage, maxGoal)

            chart.axisLeft.axisMaximum = (maxValue * 1.1f).coerceAtLeast(10f)

            chart.invalidate()
        }

    )
}
