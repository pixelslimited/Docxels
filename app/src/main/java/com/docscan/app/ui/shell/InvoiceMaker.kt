package com.docscan.app.ui.shell

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docscan.app.core.word.InvoiceCurrency
import com.docscan.app.core.word.InvoiceData
import com.docscan.app.core.word.InvoiceItem
import com.docscan.app.core.word.InvoiceStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private class ItemDraft(desc: String = "", qty: String = "1", price: String = "") {
    var desc by mutableStateOf(desc)
    var qty by mutableStateOf(qty)
    var price by mutableStateOf(price)
}

private val DATE = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
private fun today(plusDays: Int = 0): String = DATE.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, plusDays) }.time)

/** Form for a new invoice. Your business details and the invoice number are remembered for next time. */
@Composable
fun InvoiceMaker(onCreate: (InvoiceData, InvoiceStyle) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("invoice", Context.MODE_PRIVATE) }
    var style by remember { mutableStateOf(InvoiceStyle.TRADITIONAL) }
    var business by remember { mutableStateOf(prefs.getString("business", "") ?: "") }
    var contact by remember { mutableStateOf(prefs.getString("contact", "") ?: "") }
    var payment by remember { mutableStateOf(prefs.getString("payment", "") ?: "") }
    var number by remember { mutableStateOf("INV-" + (prefs.getInt("next", 1)).toString().padStart(4, '0')) }
    var customer by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var due by remember { mutableStateOf(today(14)) }
    var currency by remember { mutableStateOf(InvoiceCurrency.NAIRA) }
    var discount by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf(ItemDraft()) }

    fun data() = InvoiceData(
        businessName = business.trim(), businessContact = contact.trim(), customerName = customer.trim(), customerAddress = address.trim(),
        number = number.trim(), date = date.trim(), due = due.trim(), currency = currency,
        items = items.filter { it.desc.isNotBlank() || (it.price.toDoubleOrNull() ?: 0.0) != 0.0 }
            .map { InvoiceItem(it.desc.trim(), it.qty.toDoubleOrNull() ?: 1.0, it.price.toDoubleOrNull() ?: 0.0) },
        discountPercent = discount.toDoubleOrNull() ?: 0.0,
        taxPercent = tax.toDoubleOrNull() ?: 0.0,
        paymentDetails = payment.trim(), notes = notes.trim(),
    )
    val preview = data()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("New invoice", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("Style")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InvoiceStyle.entries.forEach { st -> FilterChip(style == st, { style = st }, label = { Text(st.label) }) }
                    }
                    Text(style.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Label("From (your business)")
                    Field(business, { business = it }, "Business name")
                    Field(contact, { contact = it }, "Address, phone, email", lines = 3)

                    Label("Bill to")
                    Field(customer, { customer = it }, "Customer name")
                    Field(address, { address = it }, "Customer address", lines = 2)

                    Label("Details")
                    Field(number, { number = it }, "Invoice number")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(date, { date = it }, "Date", Modifier.weight(1f))
                        Field(due, { due = it }, "Due date", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InvoiceCurrency.all.forEach { c -> FilterChip(currency == c, { currency = c }, label = { Text("${c.symbol} ${c.major}") }) }
                    }

                    Label("Items")
                    items.forEachIndexed { i, it ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Field(it.desc, { v -> it.desc = v }, "Description", Modifier.weight(1f))
                                if (items.size > 1) IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Default.Delete, contentDescription = "Remove item") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(it.qty, { v -> it.qty = v }, "Qty", Modifier.weight(1f), number = true)
                                Field(it.price, { v -> it.price = v }, "Unit price", Modifier.weight(2f), number = true)
                            }
                        }
                    }
                    TextButton(onClick = { items += ItemDraft() }) { Icon(Icons.Default.Add, null); Text("  Add item") }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(discount, { discount = it }, "Discount %", Modifier.weight(1f), number = true)
                        Field(tax, { tax = it }, "VAT / tax %", Modifier.weight(1f), number = true)
                    }
                    Label("Payment details and notes")
                    Field(payment, { payment = it }, "Bank name, account name, account number", lines = 3)
                    Field(notes, { notes = it }, "Notes or terms", lines = 2)
                    Text(
                        "Total: ${currency.symbol}${com.docscan.app.core.word.Invoices.money(preview.total)}",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        enabled = business.isNotBlank() && customer.isNotBlank() && preview.items.isNotEmpty(),
                        onClick = {
                            prefs.edit().putString("business", business).putString("contact", contact).putString("payment", payment)
                                .putInt("next", (number.filter { it.isDigit() }.toIntOrNull() ?: prefs.getInt("next", 1)) + 1).apply()
                            onCreate(preview, style)
                        },
                    ) { Text("Create invoice") }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, lines: Int = 1, number: Boolean = false) {
    OutlinedTextField(
        value, onChange, modifier = modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = lines == 1, minLines = lines,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
    )
}
