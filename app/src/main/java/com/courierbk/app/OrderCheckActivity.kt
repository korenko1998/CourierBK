package com.courierbk.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderCheckActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "courier_bk_orders"
        private const val ORDERS_INDEX = "orders_index"
    }

    private lateinit var orderNumberEditText: EditText
    private lateinit var customerNameEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var addressEditText: EditText
    private lateinit var apartmentEditText: EditText
    private lateinit var commentEditText: EditText
    private lateinit var totalEditText: EditText
    private lateinit var paymentEditText: EditText
    private lateinit var cashGivenEditText: EditText
    private lateinit var changeEditText: EditText
    private lateinit var itemsEditText: EditText
    private lateinit var drinksEditText: EditText

    private lateinit var warningText: TextView
    private lateinit var confirmButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_order_check)

        orderNumberEditText = findViewById(R.id.orderNumberEditText)
        customerNameEditText = findViewById(R.id.customerNameEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        addressEditText = findViewById(R.id.addressEditText)
        apartmentEditText = findViewById(R.id.apartmentEditText)
        commentEditText = findViewById(R.id.commentEditText)
        totalEditText = findViewById(R.id.totalEditText)
        paymentEditText = findViewById(R.id.paymentEditText)
        cashGivenEditText = findViewById(R.id.cashGivenEditText)
        changeEditText = findViewById(R.id.changeEditText)
        itemsEditText = findViewById(R.id.itemsEditText)
        drinksEditText = findViewById(R.id.drinksEditText)

        warningText = findViewById(R.id.warningText)
        confirmButton = findViewById(R.id.confirmOrderButton)

        fillFromIntent()
        updateWarnings()

        confirmButton.setOnClickListener {
            saveOrder()
        }

        LogUtil.info(
            "ORDER_CHECK",
            "Открыта проверка заказа №${orderNumberEditText.text}"
        )
    }

    private fun fillFromIntent() {
        orderNumberEditText.setText(
            intent.getStringExtra("order_number") ?: ""
        )

        customerNameEditText.setText(
            intent.getStringExtra("customer_name") ?: ""
        )

        phoneEditText.setText(
            intent.getStringExtra("phone") ?: ""
        )

        addressEditText.setText(
            intent.getStringExtra("address") ?: ""
        )

        apartmentEditText.setText(
            intent.getStringExtra("apartment") ?: ""
        )

        commentEditText.setText(
            intent.getStringExtra("comment") ?: ""
        )

        totalEditText.setText(
            intent.getStringExtra("total") ?: ""
        )

        paymentEditText.setText(
            intent.getStringExtra("payment") ?: ""
        )

        cashGivenEditText.setText(
            intent.getStringExtra("cash_given") ?: ""
        )

        changeEditText.setText(
            intent.getStringExtra("change") ?: ""
        )

        itemsEditText.setText(
            intent.getStringExtra("items") ?: ""
        )

        val rawDrinks =
            intent.getStringExtra("drinks") ?: ""

        drinksEditText.setText(
            if (rawDrinks.startsWith("⚠")) {
                ""
            } else {
                rawDrinks
            }
        )
    }

    private fun updateWarnings() {
        val warnings = mutableListOf<String>()

        val address =
            addressEditText.text.toString().trim()

        val phone =
            phoneEditText.text.toString().trim()

        val total =
            totalEditText.text.toString().trim()

        val payment =
            paymentEditText.text.toString()
                .trim()
                .toLowerCase(Locale.getDefault())

        val cashGiven =
            cashGivenEditText.text.toString().trim()

        val change =
            changeEditText.text.toString().trim()

        val drinks =
            drinksEditText.text.toString().trim()

        if (address.isEmpty()) {
            warnings.add(
                "⚠ Не указан адрес — заказ нельзя передать в доставку"
            )
        }

        if (phone.isEmpty()) {
            warnings.add(
                "⚠ Не указан телефон клиента"
            )
        }

        if (total.isEmpty()) {
            warnings.add(
                "⚠ Не указана сумма заказа"
            )
        }

        if (drinks.isEmpty()) {
            warnings.add(
                "⚠ Проверьте напитки"
            )
        }

        if (
            payment.contains("нал") ||
            payment.contains("cash")
        ) {
            if (cashGiven.isEmpty()) {
                warnings.add(
                    "⚠ Не указана сумма от клиента"
                )
            }

            if (change.isEmpty()) {
                warnings.add(
                    "⚠ Сдача не определена — проверьте чек"
                )
            }
        }

        warningText.text =
            if (warnings.isEmpty()) {
                "✓ Основные данные заполнены.\nПроверьте заказ перед сохранением."
            } else {
                warnings.joinToString("\n")
            }
    }

    private fun saveOrder() {
        val number =
            orderNumberEditText.text.toString().trim()

        val customer =
            customerNameEditText.text.toString().trim()

        val phone =
            phoneEditText.text.toString().trim()

        val address =
            addressEditText.text.toString().trim()

        val apartment =
            apartmentEditText.text.toString().trim()

        val comment =
            commentEditText.text.toString().trim()

        val total =
            totalEditText.text.toString().trim()

        val payment =
            paymentEditText.text.toString().trim()

        val cashGiven =
            cashGivenEditText.text.toString().trim()

        val change =
            changeEditText.text.toString().trim()

        val items =
            itemsEditText.text.toString().trim()

        val drinks =
            drinksEditText.text.toString().trim()

        if (number.isEmpty()) {
            Toast.makeText(
                this,
                "Укажите номер заказа",
                Toast.LENGTH_LONG
            ).show()

            orderNumberEditText.requestFocus()
            return
        }

        if (address.isEmpty()) {
            Toast.makeText(
                this,
                "Сначала укажите адрес доставки",
                Toast.LENGTH_LONG
            ).show()

            addressEditText.requestFocus()
            return
        }

        val finalAddress =
            buildAddress(
                address,
                apartment
            )

        val now =
            System.currentTimeMillis()

        val deliveryAt =
            parseDate(
                intent.getStringExtra("delivery_date")
            )

        val certificateAt =
            parseDate(
                intent.getStringExtra("certificate")
            )

        val order =
            JSONObject()

        order.put(
            "number",
            number
        )

        order.put(
            "customer",
            customer
        )

        order.put(
            "phone",
            phone
        )

        order.put(
            "address",
            finalAddress
        )

        order.put(
            "apartment",
            apartment
        )

        order.put(
            "comment",
            comment
        )

        order.put(
            "total",
            total
        )

        order.put(
            "payment",
            payment
        )

        order.put(
            "cashGiven",
            cashGiven
        )

        order.put(
            "change",
            change
        )

        order.put(
            "items",
            items
        )

        order.put(
            "drinks",
            drinks
        )

        order.put(
            "status",
            "NEW"
        )

        order.put(
            "createdAt",
            now
        )

        order.put(
            "deliveryAt",
            deliveryAt
        )

        order.put(
            "certificateAt",
            certificateAt
        )

        order.put(
            "receiptImagePath",
            intent.getStringExtra(
                "receipt_image_path"
            ) ?: ""
        )

        order.put(
            "ocrText",
            intent.getStringExtra(
                "ocr_text"
            ) ?: ""
        )

        val preferences =
            getSharedPreferences(
                PREFS,
                MODE_PRIVATE
            )

        val currentIndex =
            preferences.getInt(
                ORDERS_INDEX,
                0
            )

        val nextIndex =
            currentIndex + 1

        preferences.edit()
            .putInt(
                ORDERS_INDEX,
                nextIndex
            )
            .putString(
                "order_$nextIndex",
                order.toString()
            )
            .apply()

        LogUtil.info(
            "ORDER_SAVE",
            "Заказ сохранён. " +
                    "index=$nextIndex, " +
                    "number=$number, " +
                    "status=NEW"
        )

        Toast.makeText(
            this,
            "Заказ №$number сохранён",
            Toast.LENGTH_LONG
        ).show()

        val ordersIntent =
            Intent(
                this,
                OrdersActivity::class.java
            )

        ordersIntent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        startActivity(
            ordersIntent
        )

        finish()
    }

    private fun buildAddress(
        address: String,
        apartment: String
    ): String {
        if (apartment.isEmpty()) {
            return address
        }

        return "$address, кв. $apartment"
    }

    private fun parseDate(
        value: String?
    ): Long {
        if (value.isNullOrBlank()) {
            return 0L
        }

        val formats =
            listOf(
                "dd.MM.yyyy HH:mm",
                "dd.MM.yyyy",
                "dd/MM/yyyy HH:mm",
                "dd-MM-yyyy HH:mm"
            )

        for (pattern in formats) {
            try {
                val format =
                    SimpleDateFormat(
                        pattern,
                        Locale.getDefault()
                    )

                format.isLenient = false

                val date =
                    format.parse(
                        value.trim()
                    )

                if (date != null) {
                    return date.time
                }
            } catch (_: Exception) {
            }
        }

        return 0L
    }
}