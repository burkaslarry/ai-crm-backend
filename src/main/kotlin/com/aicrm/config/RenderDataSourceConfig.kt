package com.aicrm.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import javax.sql.DataSource

@Configuration
@Profile("render")
class RenderDataSourceConfig {

    @Value("\${DATABASE_URL:}")
    private var databaseUrl: String = ""

    @Value("\${INTERNAL_DATABASE_URL:}")
    private var internalDatabaseUrl: String = ""

    @Value("\${RENDER_REGION:singapore}")
    private var renderRegion: String = "singapore"

    @Value("\${RENDER_PG_HOST_SUFFIX:}")
    private var renderPgHostSuffix: String = ""

    @Bean
    fun dataSource(): DataSource {
        val url = databaseUrl.takeIf { it.isNotBlank() } ?: internalDatabaseUrl.takeIf { it.isNotBlank() }
        if (url.isNullOrBlank()) {
            throw IllegalStateException("Render profile active but DATABASE_URL/INTERNAL_DATABASE_URL is empty")
        }

        val (jdbcUrl, username, password) = parsePostgresUrl(url)
        val ds = HikariDataSource().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            connectionTimeout = 10000
        }

        // Render profile uses Postgres-only schema file.
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
        var query = hostPortPath.substringAfter('?', "")
        if (host.contains("render.com") && !query.contains("sslmode=", ignoreCase = true)) {
            query = if (query.isBlank()) "sslmode=require" else "$query&sslmode=require"
        }
        val jdbcUrl = buildString {
            append("jdbc:postgresql://")
            append(host)
            append(":")
            append(port)
            append("/")
            append(path)
            if (query.isNotBlank()) {
                append("?")
                append(query)
            }
        }
        return Triple(jdbcUrl, user, password)
    }

    private fun normalizeRenderHost(host: String): String {
        if (host.contains('.')) return host
        if (!host.startsWith("dpg-")) return host
        if (renderPgHostSuffix.isNotBlank()) return "$host.${renderPgHostSuffix.trim()}"
        val normalizedRegion = renderRegion.lowercase().ifBlank { "singapore" }
        val regionAlias = when (normalizedRegion) {
            "sg" -> "singapore"
            "us-west", "oregon" -> "oregon"
            "us-east", "ohio" -> "ohio"
            "eu-central", "frankfurt" -> "frankfurt"
            else -> normalizedRegion
        }
        return "$host.$regionAlias-postgres.render.com"
    }
}
