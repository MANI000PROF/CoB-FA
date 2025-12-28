package com.cobfa.app.ui.expense.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cobfa.app.domain.model.ExpenseCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerBottomSheet(
    onCategorySelected: (ExpenseCategory) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select category",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(16.dp))

            ExpenseCategory.values().forEach { category ->
                Text(
                    text = category.name.replace("_", " "),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCategorySelected(category)
                            onDismiss()
                        }
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
