package com.courierbk.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        LogUtil.info(
            "APP",
            "Открыт главный экран"
        )

        setContentView(
            R.layout.activity_main
        )

        val dateText =
            findViewById<TextView>(
                R.id.dateText
            )

        val timeText =
            findViewById<TextView>(
                R.id.timeText
            )

        val startButton =
            findViewById<Button>(
                R.id.startShiftButton
            )

        val statusText =
            findViewById<TextView>(
                R.id.statusText
            )

        val settingsButton =
            findViewById<TextView>(
                R.id.settingsButton
            )

        settingsButton.setOnClickListener {

            LogUtil.info(
                "UI",
                "Открыт журнал работы"
            )

            startActivity(
                Intent(
                    this,
                    LogActivity::class.java
                )
            )
        }

        val dateFormat =
            SimpleDateFormat(
                "dd MMMM yyyy",
                Locale("ru")
            )

        val timeFormat =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            )

        dateText.text =
            dateFormat.format(
                Date()
            )

        timeText.text =
            timeFormat.format(
                Date()
            )

        val buttonBackground =
            GradientDrawable()

        buttonBackground.shape =
            GradientDrawable.OVAL

        buttonBackground.setColor(
            Color.rgb(
                214,
                35,
                0
            )
        )

        startButton.background =
            buttonBackground

        startButton.setOnTouchListener {
                view,
                event ->

            when (
                event.action
            ) {

                MotionEvent.ACTION_DOWN -> {

                    view.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(100)
                        .setInterpolator(
                            DecelerateInterpolator()
                        )
                        .start()

                    true
                }

                MotionEvent.ACTION_UP -> {

                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(
                            DecelerateInterpolator()
                        )
                        .start()

                    LogUtil.info(
                        "SHIFT",
                        "Нажата кнопка начала смены"
                    )

                    val intent =
                        Intent(
                            this,
                            WorkActivity::class.java
                        )

                    startActivity(
                        intent
                    )

                    true
                }

                MotionEvent.ACTION_CANCEL -> {

                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()

                    true
                }

                else -> true
            }
        }

        statusText.text =
            "●  Смена не начата"
    }
}