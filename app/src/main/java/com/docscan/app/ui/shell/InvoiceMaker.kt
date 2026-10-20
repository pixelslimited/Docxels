package com.docscan.app.ui.shell

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.docscan.app.core.word.round2
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.docscan.app.core.word.BillKind
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
    var kind by remember { mutableStateOf(BillKind.INVOICE) }
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
        paymentDetails = payment.trim(), notes = notes.trim(), kind = kind,
    )
    val preview = data()

    val canCreate = business.isNotBlank() && customer.isNotBlank() && preview.items.isNotEmpty()
    fun create() {
        prefs.edit().putString("business", business).putString("contact", contact).putString("payment", payment)
            .putInt("next", (number.filter { it.isDigit() }.toIntOrNull() ?: prefs.getInt("next", 1)) + 1).apply()
        onCreate(preview, style)
    }
    val ink = Color(0xFF1B1B1F)
    val faint = Color(0xFF8A9099)
    val sym = currency.symbol

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    Text("New ${kind.label.lowercase()}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(enabled = canCreate, onClick = { create() }) { Text("Create") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BillKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text(k.label) }) }
                    }
                    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InvoiceStyle.entries.forEach { st -> FilterChip(style == st, { style = st }, label = { Text(st.label) }) }
                    }
                    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InvoiceCurrency.all.forEach { c -> FilterChip(currency == c, { currency = c }, label = { Text("${c.symbol} ${c.major}") }) }
                    }

                    // The page itself: type straight onto it; amounts, totals and the words are worked out as you go.
                    Column(
                        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(6.dp)).background(Color.White, RoundedCornerShape(6.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Plain(business, { business = it }, "Your business name", ink, faint, size = 18, bold = true)
                                Plain(contact, { contact = it }, "Address, phone, email", ink, faint, size = 12, multi = true)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(kind.title.uppercase(), color = Color(0xFF1F4E79), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("BILL TO", color = faint, fontSize = 10.sp)
                                Plain(customer, { customer = it }, "Customer name", ink, faint, size = 14, bold = true)
                                Plain(address, { address = it }, "Customer address", ink, faint, size = 12, multi = true)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${kind.title.uppercase()} NO.", color = faint, fontSize = 10.sp)
                                Plain(number, { number = it }, "No.", ink, faint, size = 13)
                                Text("DATE", color = faint, fontSize = 10.sp)
                                Plain(date, { date = it }, "Date", ink, faint, size = 13)
                                Text(kind.dueLabel.uppercase(), color = faint, fontSize = 10.sp)
                                Plain(due, { due = it }, kind.dueLabel, ink, faint, size = 13)
                            }
                        }
                        // Items table
                        Row(Modifier.fillMaxWidth().background(Color(0xFFE8EEF6)).padding(vertical = 6.dp, horizontal = 4.dp)) {
                            Text("Description", Modifier.weight(1f), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Qty", Modifier.width(40.dp), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Price", Modifier.width(72.dp), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Amount", Modifier.width(78.dp), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        items.forEachIndexed { i, row ->
                            val amount = round2((row.qty.toDoubleOrNull() ?: 1.0) * (row.price.toDoubleOrNull() ?: 0.0))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Plain(row.desc, { v -> row.desc = v }, "Item ${i + 1}", ink, faint, size = 13, multi = true, modifier = Modifier.weight(1f))
                                Plain(row.qty, { v -> row.qty = v }, "1", ink, faint, size = 13, number = true, modifier = Modifier.width(40.dp))
                                Spacer(Modifier.width(4.dp))
                                Plain(row.price, { v -> row.price = v }, "0.00", ink, faint, size = 13, number = true, modifier = Modifier.width(68.dp))
                                Text(
                                    if (amount == 0.0) "" else com.docscan.app.core.word.Invoices.money(amount),
                                    Modifier.width(78.dp), color = ink, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                )
                            }
                            if (items.size > 1) Text("Remove item ${i + 1}", color = faint, fontSize = 11.sp, modifier = Modifier.clickable { items.removeAt(i) }.padding(vertical = 2.dp))
                        }
                        Text("+ Add item", color = Color(0xFF1F4E79), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { items += ItemDraft() }.padding(vertical = 6.dp))

                        // Totals, worked out live.
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TotalLine("Subtotal", "$sym${com.docscan.app.core.word.Invoices.money(preview.subtotal)}", ink)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Discount", color = ink, fontSize = 13.sp)
                                Plain(discount, { discount = it }, "0", ink, faint, size = 13, number = true, modifier = Modifier.width(44.dp).padding(horizontal = 4.dp))
                                Text("%", color = ink, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                Text(if (preview.discount > 0) "−$sym${com.docscan.app.core.word.Invoices.money(preview.discount)}" else "", color = ink, fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("VAT / tax", color = ink, fontSize = 13.sp)
                                Plain(tax, { tax = it }, "0", ink, faint, size = 13, number = true, modifier = Modifier.width(44.dp).padding(horizontal = 4.dp))
                                Text("%", color = ink, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                Text(if (preview.tax > 0) "$sym${com.docscan.app.core.word.Invoices.money(preview.tax)}" else "", color = ink, fontSize = 13.sp)
                            }
                            androidx.compose.material3.HorizontalDivider(color = Color(0xFFD0D4DA))
                            TotalLine("Total", "$sym${com.docscan.app.core.word.Invoices.money(preview.total)}", ink, big = true)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("AMOUNT IN WORDS", color = faint, fontSize = 10.sp)
                            Text(
                                if (preview.total > 0.0) com.docscan.app.core.word.NumberWords.money(preview.total, currency) + " only" else "—",
                                color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PAYMENT DETAILS", color = faint, fontSize = 10.sp)
                            Plain(payment, { payment = it }, "Bank name, account name, account number", ink, faint, size = 12, multi = true)
                            Text("NOTES", color = faint, fontSize = 10.sp)
                            Plain(notes, { notes = it }, "Notes or terms", ink, faint, size = 12, multi = true)
                        }
                    }
                    if (!canCreate) Text(
                        "Add your business name, a customer and at least one item to create it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, ink: Color, big: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = ink, fontSize = if (big) 15.sp else 13.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        Text(value, color = ink, fontSize = if (big) 16.sp else 13.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
    }
}

/** A line of text typed straight onto the page: no box, just a thin rule underneath. */
@Composable
private fun Plain(
    value: String, onChange: (String) -> Unit, hint: String, ink: Color, faint: Color,
    size: Int = 13, bold: Boolean = false, multi: Boolean = false, number: Boolean = false, modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange,
        textStyle = androidx.compose.ui.text.TextStyle(color = ink, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1F4E79)),
        singleLine = !multi,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 3.dp)) {
                    if (value.isEmpty()) Text(hint, color = faint, fontSize = size.sp)
                    inner()
                }
                androidx.compose.material3.HorizontalDivider(color = Color(0xFFD0D4DA))
            }
        },
    )
}
