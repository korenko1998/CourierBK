package com.courierbk.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Locale

class OrdersActivity : AppCompatActivity() {

    companion object {
        private const val PREFS =
            "courier_bk_orders"

        private const val ORDERS_INDEX =
            "orders_index"
    }

    private lateinit var ordersContainer: LinearLayout

    private val handler =
        Handler(Looper.getMainLooper())

    private val timerRunnable =
        object : Runnable {

            override fun run() {
                updateTimers()

                handler.postDelayed(
                    this,
                    1000
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_orders
        )

        ordersContainer =
            findViewById(
                R.id.ordersContainer
            )

        val scanReceiptButton =
            findViewById<Button>(
                R.id.scanReceiptButton
            )

        val manualOrderButton =
            findViewById<Button>(
                R.id.manualOrderButton
            )

        scanReceiptButton.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    ScanReceiptActivity::class.java
                )
            )
        }

        manualOrderButton.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    OcrActivity::class.java
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()

        loadOrders()

        handler.removeCallbacks(
            timerRunnable
        )

        handler.post(
            timerRunnable
        )
    }

    override fun onPause() {
        super.onPause()

        handler.removeCallbacks(
            timerRunnable
        )
    }

    private fun loadOrders() {

        ordersContainer.removeAllViews()

        val preferences =
            getSharedPreferences(
                PREFS,
                MODE_PRIVATE
            )

        /*
         * OcrActivity сохраняет orders_index
         * как Int. Раньше здесь было getString(),
         * из-за чего экран заказов мог падать.
         */
        val index =
            preferences.getInt(
                ORDERS_INDEX,
                0
            )

        if (index <= 0) {

            val emptyText =
                TextView(this)

            emptyText.text =
                "Заказов пока нет"

            emptyText.setTextColor(
                Color.rgb(
                    128,
                    109,
                    99
                )
            )

            emptyText.textSize =
                16f

            emptyText.gravity =
                17

            emptyText.setPadding(
                0,
                50,
                0,
                50
            )

            ordersContainer.addView(
                emptyText
            )

            return
        }

        for (number in 1..index) {

            val jsonString =
                preferences.getString(
                    "order_$number",
                    null
                )

            if (
                jsonString.isNullOrEmpty()
            ) {
                continue
            }

            try {

                val order =
                    JSONObject(jsonString)

                addOrderCard(order)

            } catch (_: Exception) {
            }
        }

        if (
            ordersContainer.childCount == 0
        ) {

            val emptyText =
                TextView(this)

            emptyText.text =
                "Заказов пока нет"

            emptyText.setTextColor(
                Color.rgb(
                    128,
                    109,
                    99
                )
            )

            emptyText.textSize =
                16f

            emptyText.gravity =
                17

            emptyText.setPadding(
                0,
                50,
                0,
                50
            )

            ordersContainer.addView(
                emptyText
            )
        }
    }

    private fun addOrderCard(
        order: JSONObject
    ) {

        val card =
            LinearLayout(this)

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            18,
            18,
            18,
            18
        )

        card.setBackgroundColor(
            Color.WHITE
        )

        val params =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            0,
            0,
            16
        )

        card.layoutParams =
            params

        val number =
            order.optString(
                "number",
                "—"
            )

        val customer =
            order.optString(
                "customer",
                ""
            )

        val address =
            order.optString(
                "address",
                ""
            )

        val total =
            order.optString(
                "total",
                ""
            )

        card.addView(
            createText(
                "ЗАКАЗ №$number",
                22f,
                Color.rgb(
                    80,
                    35,
                    20
                ),
                true
            )
        )

        if (customer.isNotEmpty()) {

            card.addView(
                createText(
                    "Клиент: $customer",
                    16f,
                    Color.rgb(
                        80,
                        35,
                        20
                    ),
                    false
                )
            )
        }

        if (address.isNotEmpty()) {

            card.addView(
                createText(
                    "Адрес: $address",
                    15f,
                    Color.rgb(
                        110,
                        85,
                        75
                    ),
                    false
                )
            )
        }

        if (total.isNotEmpty()) {

            card.addView(
                createText(
                    "Сумма: $total",
                    16f,
                    Color.rgb(
                        80,
                        35,
                        20
                    ),
                    true
                )
            )
        }

        val deliveryTimer =
            createText(
                "",
                17f,
                Color.rgb(
                    198,
                    45,
                    0
                ),
                true
            )

        deliveryTimer.tag =
            "delivery_$number"

        deliveryTimer.setPadding(
            0,
            16,
            0,
            4
        )

        card.addView(
            deliveryTimer
        )

        val certificateTimer =
            createText(
                "",
                17f,
                Color.rgb(
                    138,
                    90,
                    0
                ),
                true
            )

        certificateTimer.tag =
            "certificate_$number"

        certificateTimer.setPadding(
            0,
            4,
            0,
            8
        )

        card.addView(
            certificateTimer
        )

        ordersContainer.addView(
            card
        )

        updateOrderTimers(
            order,
            deliveryTimer,
            certificateTimer
        )
    }

    private fun createText(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {

        val view =
            TextView(this)

        view.text =
            text

        view.textSize =
            size

        view.setTextColor(
            color
        )

        if (bold) {
            view.setTypeface(
                null,
                android.graphics.Typeface.BOLD
            )
        }

        view.setPadding(
            0,
            5,
            0,
            5
        )

        return view
    }

    private fun updateTimers() {

        val preferences =
            getSharedPreferences(
                PREFS,
                MODE_PRIVATE
            )

        for (
            i in 0 until ordersContainer.childCount
        ) {

            val card =
                ordersContainer
                    .getChildAt(i)
                    as? LinearLayout
                    ?: continue

            val orderNumber =
                findOrderNumber(card)
                    ?: continue

            val jsonString =
                preferences.getString(
                    "order_$orderNumber",
                    null
                )
                    ?: continue

            try {

                val order =
                    JSONObject(jsonString)

                var deliveryView:
                    TextView? = null

                var certificateView:
                    TextView? = null

                for (
                    j in 0 until card.childCount
                ) {

                    val child =
                        card.getChildAt(j)

                    if (child is TextView) {

                        if (
                            child.tag ==
                            "delivery_$orderNumber"
                        ) {
                            deliveryView =
                                child
                        }

                        if (
                            child.tag ==
                            "certificate_$orderNumber"
                        ) {
                            certificateView =
                                child
                        }
                    }
                }

                if (
                    deliveryView != null &&
                    certificateView != null
                ) {

                    updateOrderTimers(
                        order,
                        deliveryView,
                        certificateView
                    )
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun findOrderNumber(
        card: LinearLayout
    ): String? {

        if (
            card.childCount == 0
        ) {
            return null
        }

        val first =
            card.getChildAt(0)
                as? TextView
                ?: return null

        val text =
            first.text.toString()

        val prefix =
            "ЗАКАЗ №"

        if (
            !text.startsWith(prefix)
        ) {
            return null
        }

        return text
            .substring(prefix.length)
            .trim()
    }

    private fun updateOrderTimers(
        order: JSONObject,
        deliveryView: TextView,
        certificateView: TextView
    ) {

        val now =
            System.currentTimeMillis()

        val deliveryAt =
            order.optLong(
                "deliveryAt",
                0L
            )

        val certificateAt =
            order.optLong(
                "certificateAt",
                0L
            )

        if (deliveryAt > 0L) {

            deliveryView.text =
                formatTimer(
                    "До доставки",
                    deliveryAt,
                    now
                )

        } else {

            deliveryView.text =
                "До доставки: дата не указана"
        }

        if (certificateAt > 0L) {

            certificateView.text =
                formatTimer(
                    "До сертификата",
                    certificateAt,
                    now
                )

        } else {

            certificateView.text =
                "До сертификата: дата не указана"
        }
    }

    private fun formatTimer(
        title: String,
        target: Long,
        now: Long
    ): String {

        val difference =
            target - now

        if (difference <= 0L) {

            val elapsed =
                -difference

            return "$title: ПРОСРОЧЕНО на ${
                formatDuration(elapsed)
            }"
        }

        return "$title: ${
            formatDuration(difference)
        }"
    }

    private fun formatDuration(
        millis: Long
    ): String {

        val totalSeconds =
            millis / 1000L

        val days =
            totalSeconds / 86400L

        val hours =
            (totalSeconds % 86400L) / 3600L

        val minutes =
            (totalSeconds % 3600L) / 60L

        val seconds =
            totalSeconds % 60L

        return if (days > 0) {

            String.format(
                Locale.getDefault(),
                "%dд %02d:%02d:%02d",
                days,
                hours,
                minutes,
                seconds
            )

        } else {

            String.format(
                Locale.getDefault(),
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )
        }
    }
}