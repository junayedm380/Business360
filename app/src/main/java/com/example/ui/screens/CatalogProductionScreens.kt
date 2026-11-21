package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.BomItemEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductModelEntity
import com.example.data.local.ProductionBatchEntity
import com.example.data.local.RawMaterialEntity
import com.example.data.local.WarehouseEntity
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.Formatters
import com.example.domain.SessionUser
import com.example.ui.components.EmptyStateNotice
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogProductionScreen(
    session: SessionUser,
    strings: AppStrings,
    products: List<ProductEntity>,
    models: List<ProductModelEntity>,
    bomItems: List<BomItemEntity>,
    rawMaterials: List<RawMaterialEntity>,
    warehouses: List<WarehouseEntity>,
    batches: List<ProductionBatchEntity>,
    onSaveProduct: (String?, String, String, String, String, String) -> Unit,
    onSaveModel: (String?, String, String, String, String, Double, Double, Double, Double, String) -> Unit,
    onSaveBom: (String, String, Double) -> Unit,
    onDeleteBom: (String) -> Unit,
    onSaveRawMaterial: (String, String, String, String, Double, String, Double, Double) -> Unit,
    onCreateBatch: (String, String, Double, Double, Double, Double, Double, Double, Double, Double, String) -> Unit,
    onAdvanceBatch: (String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showNewProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showModelDialog by remember { mutableStateOf<ProductModelEntity?>(null) }
    var showNewModelDialog by remember { mutableStateOf(false) }
    var showBomDialogForModel by remember { mutableStateOf<ProductModelEntity?>(null) }
    var showRawDialog by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.productionBatches) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(strings.productsAndModels) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(strings.rawMaterials) })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (session.has(AppPermission.CREATE_PRODUCTION)) {
                                Button(
                                    onClick = { showBatchDialog = true },
                                    enabled = models.isNotEmpty() && warehouses.isNotEmpty(),
                                    modifier = Modifier.testTag("btn_new_batch")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("New Batch")
                                }
                            }
                        }
                    }
                    if (batches.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(batches, key = { it.id }) { batch ->
                            val modelName = models.firstOrNull { it.id == batch.modelId }?.modelName ?: batch.modelId
                            SectionCard(title = "${batch.batchNumber} • $modelName") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(Formatters.formatDate(batch.productionDate), style = MaterialTheme.typography.bodySmall)
                                    StatusChip(batch.status)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Planned: ${Formatters.formatQty(batch.plannedQty)} | Actual: ${Formatters.formatQty(batch.actualQty)} | Good: ${Formatters.formatQty(batch.goodQty)} | Rejected: ${Formatters.formatQty(batch.rejectedQty)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (session.canViewFinancials) {
                                    Text(
                                        "Total Cost: ${Formatters.formatTaka(batch.totalCost)} (Mat: ${Formatters.formatTaka(batch.materialCost)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (batch.status != "COMPLETED") {
                                    Spacer(Modifier.height(10.dp))
                                    val nextStep = when (batch.status) {
                                        "DRAFT" -> "SUBMITTED"
                                        "SUBMITTED" -> "REVIEWED"
                                        "REVIEWED" -> "APPROVED"
                                        "APPROVED" -> "COMPLETED"
                                        else -> null
                                    }
                                    if (nextStep != null && (nextStep == "REVIEWED" || session.has(AppPermission.APPROVE_PRODUCTION))) {
                                        FilledTonalButton(onClick = { onAdvanceBatch(batch.id, nextStep) }) {
                                            Text("Advance to $nextStep")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session.has(AppPermission.MANAGE_PRODUCTS)) {
                                Button(onClick = { showNewProductDialog = true }, modifier = Modifier.weight(1f).testTag("btn_add_product")) {
                                    Text("+ Product")
                                }
                                FilledTonalButton(
                                    onClick = { showNewModelDialog = true },
                                    enabled = products.isNotEmpty(),
                                    modifier = Modifier.weight(1f).testTag("btn_add_model")
                                ) {
                                    Text("+ Model")
                                }
                            }
                        }
                    }
                    if (products.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(products, key = { it.id }) { prod ->
                            val prodModels = models.filter { it.productId == prod.id }
                            SectionCard(
                                title = "${prod.name} (${prod.category})",
                                actionLabel = if (session.has(AppPermission.MANAGE_PRODUCTS)) "Edit" else null,
                                onAction = if (session.has(AppPermission.MANAGE_PRODUCTS)) {
                                    { editingProduct = prod }
                                } else null
                            ) {
                                if (prod.description.isNotBlank()) {
                                    Text(prod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                }
                                if (prodModels.isEmpty()) {
                                    Text("No models added under this product yet.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    prodModels.forEach { m ->
                                        val bomCount = bomItems.count { it.modelId == m.id }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (m.imageUri.isNotBlank()) {
                                                AsyncImage(
                                                    model = Uri.parse(m.imageUri),
                                                    contentDescription = m.modelName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${m.modelName} [${m.modelCode}]", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                                if (session.has(AppPermission.VIEW_UNIT_PRICES) || session.canViewFinancials) {
                                                    Text(
                                                        "Cost: ${Formatters.formatTaka(m.productionCost)} | Wholesale: ${Formatters.formatTaka(m.wholesalePrice)} | Retail: ${Formatters.formatTaka(m.retailPrice)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                                Text("BOM items: $bomCount • Min stock: ${Formatters.formatQty(m.minimumStock)}", style = MaterialTheme.typography.labelSmall)
                                            }
                                            if (session.has(AppPermission.MANAGE_PRODUCTS)) {
                                                IconButton(onClick = { showBomDialogForModel = m }) {
                                                    Icon(Icons.Filled.Build, contentDescription = "Configure BOM")
                                                }
                                                TextButton(onClick = { showModelDialog = m }) {
                                                    Text("Edit")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (session.has(AppPermission.MANAGE_RAW_MATERIALS)) {
                                Button(onClick = { showRawDialog = true }, modifier = Modifier.testTag("btn_add_raw_material")) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Raw Material")
                                }
                            }
                        }
                    }
                    if (rawMaterials.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(rawMaterials, key = { it.id }) { mat ->
                            SectionCard(title = "${mat.name} (${mat.materialCode})") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Category: ${mat.category}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${Formatters.formatQty(mat.availableQuantity, mat.unit)} (Min ${Formatters.formatQty(mat.minimumStock)})",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                if (session.canViewFinancials) {
                                    Text("Last Price: ${Formatters.formatTaka(mat.purchasePrice)} / ${mat.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewProductDialog || editingProduct != null) {
        ProductFormDialog(
            existing = editingProduct,
            onDismiss = {
                showNewProductDialog = false
                editingProduct = null
            },
            onSave = { exId, name, cat, desc, unit, img ->
                onSaveProduct(exId, name, cat, desc, unit, img)
                showNewProductDialog = false
                editingProduct = null
            }
        )
    }

    if (showNewModelDialog || showModelDialog != null) {
        ModelFormDialog(
            existing = showModelDialog,
            products = products,
            onDismiss = { showNewModelDialog = false; showModelDialog = null },
            onSave = { exId, prodId, mName, mCode, barcode, cost, wholesale, retail, minStk, img ->
                onSaveModel(exId, prodId, mName, mCode, barcode, cost, wholesale, retail, minStk, img)
                showNewModelDialog = false
                showModelDialog = null
            }
        )
    }

    showBomDialogForModel?.let { targetModel ->
        BomConfigDialog(
            model = targetModel,
            bomItems = bomItems.filter { it.modelId == targetModel.id },
            rawMaterials = rawMaterials,
            onAddBom = { matId, qty -> onSaveBom(targetModel.id, matId, qty) },
            onDeleteBom = onDeleteBom,
            onDismiss = { showBomDialogForModel = null }
        )
    }

    if (showRawDialog) {
        RawMaterialFormDialog(warehouses = warehouses, onDismiss = { showRawDialog = false }) { name, code, cat, unit, price, whId, initQty, minStk ->
            onSaveRawMaterial(name, code, cat, unit, price, whId, initQty, minStk)
            showRawDialog = false
        }
    }

    if (showBatchDialog) {
        ProductionBatchFormDialog(
            models = models,
            warehouses = warehouses,
            onDismiss = { showBatchDialog = false },
            onSubmit = { mId, whId, planned, actual, rej, wast, labor, machine, elec, other, notes ->
                onCreateBatch(mId, whId, planned, actual, rej, wast, labor, machine, elec, other, notes)
                showBatchDialog = false
            }
        )
    }
}

@Composable
private fun ProductFormDialog(
    existing: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, String) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "Apparel") }
    var description by remember(existing?.id) { mutableStateOf(existing?.description ?: "") }
    var unit by remember(existing?.id) { mutableStateOf(existing?.unit ?: "pcs") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Product" else "Edit Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (pcs, box, kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(existing?.id, name, category, description, unit, existing?.imageUri ?: "") },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelFormDialog(
    existing: ProductModelEntity?,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, Double, Double, Double, Double, String) -> Unit
) {
    var selectedProduct by remember { mutableStateOf(products.firstOrNull { it.id == existing?.productId } ?: products.first()) }
    var expanded by remember { mutableStateOf(false) }
    var modelName by remember { mutableStateOf(existing?.modelName ?: "") }
    var modelCode by remember { mutableStateOf(existing?.modelCode ?: "") }
    var barcode by remember { mutableStateOf(existing?.barcode ?: "") }
    var cost by remember { mutableStateOf(existing?.productionCost?.toString() ?: "0") }
    var wholesale by remember { mutableStateOf(existing?.wholesalePrice?.toString() ?: "0") }
    var retail by remember { mutableStateOf(existing?.retailPrice?.toString() ?: "0") }
    var minStock by remember { mutableStateOf(existing?.minimumStock?.toString() ?: "5") }
    var imageUri by remember { mutableStateOf(existing?.imageUri ?: "") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Product Model" else "Edit Model") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedProduct.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Product") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        products.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { selectedProduct = p; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text("Model Name") }, singleLine = true)
                OutlinedTextField(value = modelCode, onValueChange = { modelCode = it }, label = { Text("Model Code") }, singleLine = true)
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode (optional)") }, singleLine = true)
                OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Production Cost (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(value = wholesale, onValueChange = { wholesale = it }, label = { Text("Wholesale Price (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(value = retail, onValueChange = { retail = it }, label = { Text("Retail Price (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(value = minStock, onValueChange = { minStock = it }, label = { Text("Minimum Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (imageUri.isBlank()) "Select Model Image" else "Image Selected")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    existing?.id, selectedProduct.id, modelName, modelCode, barcode,
                    cost.toDoubleOrNull() ?: 0.0, wholesale.toDoubleOrNull() ?: 0.0,
                    retail.toDoubleOrNull() ?: 0.0, minStock.toDoubleOrNull() ?: 5.0, imageUri
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BomConfigDialog(
    model: ProductModelEntity,
    bomItems: List<BomItemEntity>,
    rawMaterials: List<RawMaterialEntity>,
    onAddBom: (String, Double) -> Unit,
    onDeleteBom: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMat by remember { mutableStateOf(rawMaterials.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var qtyPerUnit by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BOM: ${model.modelName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bomItems.forEach { b ->
                    val mat = rawMaterials.firstOrNull { it.id == b.rawMaterialId }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${mat?.name ?: b.rawMaterialId}: ${b.quantityPerUnit} ${mat?.unit ?: ""}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteBom(b.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete BOM item") }
                    }
                }
                if (rawMaterials.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedMat?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Raw Material") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            rawMaterials.forEach { m ->
                                DropdownMenuItem(text = { Text("${m.name} (${m.unit})") }, onClick = { selectedMat = m; expanded = false })
                            }
                        }
                    }
                    OutlinedTextField(value = qtyPerUnit, onValueChange = { qtyPerUnit = it }, label = { Text("Qty per 1 unit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    FilledTonalButton(onClick = {
                        selectedMat?.let { m -> onAddBom(m.id, qtyPerUnit.toDoubleOrNull() ?: 1.0) }
                    }) { Text("Add to BOM") }
                } else {
                    Text("Add raw materials first to define BOM.")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun RawMaterialFormDialog(
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Double, String, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Fabric") }
    var unit by remember { mutableStateOf("kg") }
    var price by remember { mutableStateOf("0") }
    var qty by remember { mutableStateOf("0") }
    var minStock by remember { mutableStateOf("10") }
    val whId = warehouses.firstOrNull()?.id ?: "wh_main"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Raw Material") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Material Name") }, singleLine = true)
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Material Code") }, singleLine = true)
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (kg, meter, pcs)") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Purchase Price (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Initial Available Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = minStock, onValueChange = { minStock = it }, label = { Text("Minimum Stock Alert") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(name, code, cat, unit, price.toDoubleOrNull() ?: 0.0, whId, qty.toDoubleOrNull() ?: 0.0, minStock.toDoubleOrNull() ?: 10.0)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductionBatchFormDialog(
    models: List<ProductModelEntity>,
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Double, Double, Double, Double, Double, Double, Double, Double, String) -> Unit
) {
    var model by remember { mutableStateOf(models.first()) }
    var wh by remember { mutableStateOf(warehouses.first()) }
    var mExp by remember { mutableStateOf(false) }
    var wExp by remember { mutableStateOf(false) }
    var planned by remember { mutableStateOf("100") }
    var actual by remember { mutableStateOf("100") }
    var rejected by remember { mutableStateOf("0") }
    var wastage by remember { mutableStateOf("0") }
    var labor by remember { mutableStateOf("0") }
    var machine by remember { mutableStateOf("0") }
    var electricity by remember { mutableStateOf("0") }
    var other by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Production Batch") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = mExp, onExpandedChange = { mExp = !mExp }) {
                    OutlinedTextField(value = "${model.modelName} (${model.modelCode})", onValueChange = {}, readOnly = true, label = { Text("Model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = mExp, onDismissRequest = { mExp = false }) {
                        models.forEach { m -> DropdownMenuItem(text = { Text("${m.modelName} (${m.modelCode})") }, onClick = { model = m; mExp = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = wExp, onExpandedChange = { wExp = !wExp }) {
                    OutlinedTextField(value = wh.name, onValueChange = {}, readOnly = true, label = { Text("Warehouse") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(wExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = wExp, onDismissRequest = { wExp = false }) {
                        warehouses.forEach { w -> DropdownMenuItem(text = { Text(w.name) }, onClick = { wh = w; wExp = false }) }
                    }
                }
                OutlinedTextField(value = planned, onValueChange = { planned = it }, label = { Text("Planned Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = actual, onValueChange = { actual = it }, label = { Text("Actual Produced Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = rejected, onValueChange = { rejected = it }, label = { Text("Rejected Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = wastage, onValueChange = { wastage = it }, label = { Text("Material Wastage Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = labor, onValueChange = { labor = it }, label = { Text("Labor Cost (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = machine, onValueChange = { machine = it }, label = { Text("Machine Cost (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = electricity, onValueChange = { electricity = it }, label = { Text("Electricity Cost (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = other, onValueChange = { other = it }, label = { Text("Other Cost (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(
                    model.id, wh.id, planned.toDoubleOrNull() ?: 0.0, actual.toDoubleOrNull() ?: 0.0,
                    rejected.toDoubleOrNull() ?: 0.0, wastage.toDoubleOrNull() ?: 0.0,
                    labor.toDoubleOrNull() ?: 0.0, machine.toDoubleOrNull() ?: 0.0,
                    electricity.toDoubleOrNull() ?: 0.0, other.toDoubleOrNull() ?: 0.0, notes
                )
            }) { Text("Submit Batch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
