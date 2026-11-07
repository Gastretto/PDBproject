package com.marlin.tokenrouter.server.postgres

import java.math.BigDecimal
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Companies : Table("companies") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val prefix = varchar("prefix", 64).uniqueIndex()
    val creditLimit = decimal("credit_limit", 18, 6).default(BigDecimal.ZERO)
    val creditSpend = decimal("credit_spend", 18, 6).default(BigDecimal.ZERO)
    val hasCredits = bool("has_credits").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    init {
        check("companies_credit_limit_non_negative") {
            creditLimit greaterEq BigDecimal.ZERO
        }
    }
}

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val companyId = long("company_id").references(Companies.id, onDelete = ReferenceOption.RESTRICT)
    val login = varchar("login", 255)
    val name = varchar("name", 255)
    val pwdHash = text("pwd_hash")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("users_company_login_unique", companyId, login)
        uniqueIndex("users_id_company_unique", id, companyId)
        index("idx_users_company", false, companyId)
    }
}

object ApiTokens : Table("api_tokens") {
    val id = long("id").autoIncrement()
    val companyId = long("company_id").references(Companies.id, onDelete = ReferenceOption.RESTRICT)
    val userId = long("user_id")
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val tokenPrefix = varchar("token_prefix", 16)
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("api_tokens_id_company_unique", id, companyId)
        foreignKey(
            userId to Users.id,
            companyId to Users.companyId,
            onDelete = ReferenceOption.RESTRICT,
        )
        index("idx_api_tokens_company", false, companyId)
        index("idx_api_tokens_user", false, userId)
        check("api_tokens_token_hash_format") {
            tokenHash regexp "^[0-9a-f]{64}$"
        }
    }
}

object Models : Table("models") {
    val id = long("id").autoIncrement()
    val codeName = varchar("code_name", 128).uniqueIndex()
    val providerUrl = text("provider_url")
    val pricePerMInput = decimal("price_per_m_input", 18, 6)
    val pricePerMOutput = decimal("price_per_m_output", 18, 6)
    val defaultPriceQuantifier = decimal("default_price_quantifier", 9, 4)
        .default(BigDecimal.ONE)
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    init {
        check("models_price_input_non_negative") {
            pricePerMInput greaterEq BigDecimal.ZERO
        }
        check("models_price_output_non_negative") {
            pricePerMOutput greaterEq BigDecimal.ZERO
        }
        check("models_quantifier_non_negative") {
            defaultPriceQuantifier greaterEq BigDecimal.ZERO
        }
    }
}

object CompanyModelPrices : Table("company_model_prices") {
    val companyId = long("company_id").references(Companies.id, onDelete = ReferenceOption.RESTRICT)
    val modelId = long("model_id").references(Models.id, onDelete = ReferenceOption.RESTRICT)
    val pricePerMInput = decimal("price_per_m_input", 18, 6)
    val pricePerMOutput = decimal("price_per_m_output", 18, 6)
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(companyId, modelId)

    init {
        check("company_model_prices_input_non_negative") {
            pricePerMInput greaterEq BigDecimal.ZERO
        }
        check("company_model_prices_output_non_negative") {
            pricePerMOutput greaterEq BigDecimal.ZERO
        }
    }
}

object UsageRollup : Table("usage_rollup") {
    val companyId = long("company_id")
    val apiTokenId = long("api_token_id")
    val provider = varchar("provider", 32)
    val model = varchar("model", 128)
    val windowStart = timestampWithTimeZone("window_start")
    val inputTokens = long("input_tokens")
    val outputTokens = long("output_tokens")
    val totalTokens = long("total_tokens").databaseGenerated()
    val pricedInputTokens = long("priced_input_tokens").default(0)
    val pricedOutputTokens = long("priced_output_tokens").default(0)
    val needsPricing = bool("needs_pricing").databaseGenerated()
    val lastPricedAt = timestampWithTimeZone("last_priced_at").nullable()

    override val primaryKey = PrimaryKey(companyId, apiTokenId, provider, model, windowStart)

    init {
        foreignKey(companyId to Companies.id, onDelete = ReferenceOption.RESTRICT)
        foreignKey(
            apiTokenId to ApiTokens.id,
            companyId to ApiTokens.companyId,
            onDelete = ReferenceOption.RESTRICT,
        )
        index("idx_usage_rollup_company_time", false, companyId, windowStart)
        index("idx_usage_rollup_token_time", false, apiTokenId, windowStart)
        index("idx_usage_rollup_model_time", false, model, windowStart)
        index("idx_usage_rollup_needs_pricing", false, windowStart) {
            needsPricing eq true
        }
        check("usage_rollup_tokens_non_negative") {
            (inputTokens greaterEq 0L) and (outputTokens greaterEq 0L)
        }
        check("usage_rollup_provider_valid") {
            provider inList listOf("OPENAI", "ANTHROPIC", "DEEPSEEK")
        }
    }
}

object UsagePricingRuns : Table("usage_pricing_runs") {
    val id = long("id").autoIncrement()
    val companyId = long("company_id").references(Companies.id, onDelete = ReferenceOption.RESTRICT)
    val windowFrom = timestampWithTimeZone("window_from")
    val windowTo = timestampWithTimeZone("window_to")
    val inputTokens = long("input_tokens")
    val outputTokens = long("output_tokens")
    val totalTokens = long("total_tokens")
    val inputCost = decimal("input_cost", 18, 6)
    val outputCost = decimal("output_cost", 18, 6)
    val totalCost = decimal("total_cost", 18, 6)
    val status = varchar("status", 16).default("DONE")
    val errorMessage = text("error_message").nullable()
    val startedAt = timestampWithTimeZone("started_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val finishedAt = timestampWithTimeZone("finished_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        check("usage_pricing_runs_status_valid") {
            status inList listOf("RUNNING", "DONE", "FAILED")
        }
        check("usage_pricing_runs_window_valid") {
            windowTo greater windowFrom
        }
    }
}

object UsageCost : Table("usage_cost") {
    val id = long("id").autoIncrement()
    val companyId = long("company_id")
    val apiTokenId = long("api_token_id")
    val provider = varchar("provider", 32)
    val model = varchar("model", 128)
    val windowStart = timestampWithTimeZone("window_start")
    val modelId = long("model_id").references(Models.id, onDelete = ReferenceOption.RESTRICT)
    val priceSource = varchar("price_source", 8)
    val inputTokensDelta = long("input_tokens_delta")
    val outputTokensDelta = long("output_tokens_delta")
    val totalTokensDelta = long("total_tokens_delta").databaseGenerated()
    val inputPricePerM = decimal("input_price_per_m", 18, 6)
    val outputPricePerM = decimal("output_price_per_m", 18, 6)
    val inputCost = decimal("input_cost", 18, 6)
    val outputCost = decimal("output_cost", 18, 6)
    val totalCost = decimal("total_cost", 18, 6).databaseGenerated()
    val providerInputPricePerM = decimal("provider_input_price_per_m", 18, 6)
    val providerOutputPricePerM = decimal("provider_output_price_per_m", 18, 6)
    val providerInputCost = decimal("provider_input_cost", 18, 6)
    val providerOutputCost = decimal("provider_output_cost", 18, 6)
    val providerTotalCost = decimal("provider_total_cost", 18, 6).databaseGenerated()
    val pricedAt = timestampWithTimeZone("priced_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val pricingRunId = long("pricing_run_id")
        .references(UsagePricingRuns.id, onDelete = ReferenceOption.RESTRICT)

    override val primaryKey = PrimaryKey(id)

    init {
        foreignKey(companyId to Companies.id, onDelete = ReferenceOption.RESTRICT)
        foreignKey(apiTokenId to ApiTokens.id, onDelete = ReferenceOption.RESTRICT)
        foreignKey(
            companyId,
            apiTokenId,
            provider,
            model,
            windowStart,
            target = UsageRollup.primaryKey,
        )
        index("idx_usage_cost_company_time", false, companyId, windowStart)
        index("idx_usage_cost_usage_key", false, companyId, apiTokenId, provider, model, windowStart)
        check("usage_cost_deltas_non_negative") {
            (inputTokensDelta greaterEq 0L) and (outputTokensDelta greaterEq 0L)
        }
        check("usage_cost_price_source_valid") {
            priceSource inList listOf("COMPANY", "GLOBAL")
        }
    }
}

object CompanyPricingState : Table("company_pricing_state") {
    val companyId = long("company_id").references(Companies.id, onDelete = ReferenceOption.RESTRICT)
    val lastPricedWindowStart = timestampWithTimeZone("last_priced_window_start").nullable()
    val lastPricedAt = timestampWithTimeZone("last_priced_at").nullable()
    val lastPricingRunId = optReference("last_pricing_run_id", UsagePricingRuns.id)

    override val primaryKey = PrimaryKey(companyId)
}

object CreditLedger : Table("credit_ledger") {
    val id = long("id").autoIncrement()
    val companyId = long("company_id").references(Companies.id, onDelete = ReferenceOption.RESTRICT)
    val entryType = varchar("entry_type", 16)
    val amount = decimal("amount", 18, 6)
    val pricingRunId = optReference("pricing_run_id", UsagePricingRuns.id)
    val note = text("note").nullable()
    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val createdBy = optReference("created_by", Users.id)

    override val primaryKey = PrimaryKey(id)

    init {
        check("credit_ledger_entry_type_valid") {
            entryType inList listOf("TOPUP", "USAGE")
        }
        check("credit_ledger_semantics") {
            ((entryType eq "TOPUP") and
                (amount greater BigDecimal.ZERO) and
                createdBy.isNotNull() and
                pricingRunId.isNull()) or
                ((entryType eq "USAGE") and
                    (amount less BigDecimal.ZERO) and
                    pricingRunId.isNotNull() and
                    createdBy.isNull())
        }
        index("credit_ledger_usage_run_uq", true, pricingRunId) {
            entryType eq "USAGE"
        }
        index("idx_credit_ledger_company_time", false, companyId, createdAt)
    }
}

object AggregationCheckpoint : Table("aggregation_checkpoint") {
    val id = short("id").default(1.toShort())
    val lastWindowStart = timestampWithTimeZone("last_window_start").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    init {
        check("aggregation_checkpoint_singleton") {
            id eq 1.toShort()
        }
    }
}
