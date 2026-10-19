package com.marlin.tokenrouter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marlin.tokenrouter.api.TokenRouterApi
import com.marlin.tokenrouter.model.Company
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Companies : Screen
    data class Detail(val companyId: Long) : Screen
}

@Composable
fun App() {
    MaterialTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Companies) }
        val api = remember { TokenRouterApi() }

        when (val current = screen) {
            is Screen.Companies -> CompaniesScreen(
                api = api,
                onCompanyClick = { screen = Screen.Detail(it) },
            )
            is Screen.Detail -> CompanyDetailScreen(
                api = api,
                companyId = current.companyId,
                onBack = { screen = Screen.Companies },
            )
        }
    }
}

@Composable
private fun CompaniesScreen(
    api: TokenRouterApi,
    onCompanyClick: (Long) -> Unit,
) {
    var companies by remember { mutableStateOf<List<Company>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        companies = null
        error = null
        runCatching { api.getCompanies() }
            .onSuccess { companies = it }
            .onFailure { error = it.message ?: "unknown error" }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = "Companies",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )

            when {
                error != null -> ErrorState(error!!, onRetry = {
                    scope.launch {
                        error = null
                        companies = null
                        runCatching { api.getCompanies() }
                            .onSuccess { companies = it }
                            .onFailure { error = it.message ?: "unknown error" }
                    }
                })
                companies == null -> LoadingState()
                companies!!.isEmpty() -> Text(
                    text = "No companies yet",
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(companies!!) { company ->
                        CompanyCard(company = company, onClick = { onCompanyClick(company.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyCard(company: Company, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = company.prefix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            UsageBar(company = company)
        }
    }
}

@Composable
private fun UsageBar(company: Company) {
    val fraction = if (company.tokenLimit > 0) {
        (company.tokensSpent.toFloat() / company.tokenLimit).coerceIn(0f, 1f)
    } else 0f

    Column {
        Text(
            text = "${company.tokensSpent} / ${company.tokenLimit} tokens",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompanyDetailScreen(
    api: TokenRouterApi,
    companyId: Long,
    onBack: () -> Unit,
) {
    var company by remember { mutableStateOf<Company?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(companyId) {
        company = null
        error = null
        runCatching { api.getCompany(companyId) }
            .onSuccess { company = it }
            .onFailure { error = it.message ?: "unknown error" }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
            }

            when {
                error != null -> ErrorState(error!!, onRetry = {
                    scope.launch {
                        error = null
                        company = null
                        runCatching { api.getCompany(companyId) }
                            .onSuccess { company = it }
                            .onFailure { error = it.message ?: "unknown error" }
                    }
                })
                company == null -> LoadingState()
                else -> CompanyDetail(company!!)
            }
        }
    }
}

@Composable
private fun CompanyDetail(company: Company) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "prefix: ${company.prefix}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(8.dp))
                UsageBar(company = company)
            }
        }

        item {
            SectionTitle("API keys (${company.validApiKeys.size})")
            if (company.validApiKeys.isEmpty()) {
                EmptyHint("No API keys")
            }
        }

        items(company.validApiKeys) { apiKey ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = apiKey.key,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (apiKey.active) "active · user #${apiKey.userId}" else "revoked",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (apiKey.active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }

        item {
            SectionTitle("Users (${company.validLogins.size})")
            if (company.validLogins.isEmpty()) {
                EmptyHint("No users")
            }
        }

        items(company.validLogins) { user ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "login: ${user.login}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LinearProgressIndicator(modifier = Modifier.width(120.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Failed to load data",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
