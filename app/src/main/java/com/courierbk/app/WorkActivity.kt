package com.courierbk.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkActivity : AppCompatActivity() {

    private lateinit var shiftTimeText: TextView

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var shiftStartTime =
        0L

    private val timeUpdater =
        object : Runnable {

            override fun run() {

                val elapsed =
                    System.currentTimeMillis() -
                        shiftStartTime

                val totalSeconds =
                    elapsed / 1000

                val hours =
                    totalSeconds / 3600

                val minutes =
                    (
                        totalSeconds % 3600
                    ) / 60

                val seconds =
                    totalSeconds % 60

                val startTime =
                    SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault()
                    ).format(
                        Date(
                            shiftStartTime
                        )
                    )

                shiftTimeText.text =
                    String.format(
                        Locale.getDefault(),
                        "Начало: %s\nВремя смены: %02d:%02d:%02d",
                        startTime,
                        hours,
                        minutes,
                        seconds
                    )

                handler.postDelayed(
                    this,
                    1000
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        LogUtil.info(
            "SHIFT",
            "Открыт экран рабочей смены"
        )

        setContentView(
            R.layout.activity_work
        )

        shiftTimeText =
            findViewById(
                R.id.shiftTimeText
            )

        val ordersButton =
            findViewById<View>(
                R.id.ordersButton
            )

        val logButton =
            findViewById<View>(
                R.id.logButton
            )

        shiftStartTime =
            System.currentTimeMillis()

        handler.post(
            timeUpdater
        )

        ordersButton.setOnClickListener {

            LogUtil.info(
                "ORDERS",
                "Открыт экран заказов"
            )

            val intent =
                Intent(
                    this,
                    OrdersActivity::class.java
                )

            startActivity(
                intent
            )
        }

        logButton.setOnClickListener {

            LogUtil.info(
                "UI",
                "Открыт журнал работы из рабочей смены"
            )

            startActivity(
                Intent(
                    this,
                    LogActivity::class.java
                )
            )
        }
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            timeUpdater
        )

        super.onDestroy()
    }
}