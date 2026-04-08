package com.aicrm.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import javax.sql.DataSource

/**
 * When running with profile "render", parse DATABASE_URL (postgres://... from Render)
 * and provide a DataSource. Also supports INTERNAL_DATABASE_URL if set by Render when linking a DB.
 *
 * Startup runs [schema-postgresql.sql] only: CREATE IF NOT EXISTS for AI CRM tables — no DROP,
 * safe on a shared Postgres that already has BNI `bni_anchor_*` tables.
 */
@Configuration
@Profile("render")
class RenderDataSourceConfig {

    @Value("\${DATABASE_URL:}")
    private var databaseUrl: String = ""

    @Value("\${INTERNAL_DATABASE_URL:}")
    private var internalDatabaseUrl: String = ""

    @Value("\${RENDER_REGION:singapore}")
    private var renderRegion: String = "singapore"

    @Bean
    fun dataSource(): DataSource {
        val url = databaseUrl.takeIf { it.isNotBlank() } ?: internalDatabaseUrl.takeIf { it.isNotBlank() }
        if (url.isNullOrBlank()) {
            throw IllegalStateException(
                "Render profile active but DATABASE_URL is not set. " +
                "On Render: Dashboard → Web Services → ai-crm-backend → Environment → add DATABASE_URL with your " +
                "Postgres connection string (from your database's Connect tab, Internal or External URL). " +
                "If using Blueprint, ensure the web service is linked to the database in render.yaml."
            )
        }
        val (jdbcUrlStr, username, password) = parsePostgresUrl(url)
        val ds = HikariDataSource().apply {
            jdbcUrl = jdbcUrlStr
            this.username = username
            this.password = password
            driverClassName = "org.postgresql.Driver"
        }
        ResourceDatabasePopulator(ClassPathResource("schema-postgresql.sql")).execute(ds)
        return ds
    }

    private fun parsePostgresUrl(url: String): Triple<String, String, String> {
        val s = url.trim().removePrefix("postgres://").removePrefix("postgresql://")
        val at = s.lastIndexOf('@')
        if (at <= 0) throw IllegalArgumentException("Invalid DATABASE_URL: missing user@host")
        val userInfo = s.substring(0, at)
        val hostPortPath = s.substring(at + 1)
        val user = userInfo.substringBefore(':')
        val password = if (userInfo.length > user.length + 1) userInfo.substring(user.length + 1) else ""
        val path = hostPortPath.substringAfter('/').substringBefore('?')
        val hostPort = hostPortPath.substringBefore('/')
        val hostRaw = hostPort.substringBeforeLast(':')
        val host = normalizeRenderHost(hostRaw)
        val port = hostPort.substringAfterLast(':').takeIf { it != hostPort }?.toIntOrNull() ?: 5432
        val qMark = hostPortPath.indexOf('?')
        val query = if (qMark >= 0) hostPortPath.substring(qMark + 1) else ""
        var jdbcUrl = "jdbc:postgresql://$host:$port/$path"
        if (query.isNotEmpty()) {
            jdbcUrl += "?$query"
        }
        return Triple(jdbcUrl, user, password)
    }

    /**
     * Some setups accidentally provide Render internal host (e.g. dpg-xxxx) to services
     * that cannot resolve internal DNS. Convert to an external FQDN fallback.
     */
    private fun normalizeRenderHost(host: String): String {
        if (host.contains('.')) return host
        if (!host.startsWith("dpg-")) return host
        val region = renderRegion.ifBlank { "singapore" }
        return "$host.$region-postgres.render.com"
    }
}
