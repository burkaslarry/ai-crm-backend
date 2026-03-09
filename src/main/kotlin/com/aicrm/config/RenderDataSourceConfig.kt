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
 * and provide a DataSource. Falls back to default H2 if DATABASE_URL is not set.
 */
@Configuration
@Profile("render")
class RenderDataSourceConfig {

    @Value("\${DATABASE_URL:}")
    private lateinit var databaseUrl: String

    @Bean
    fun dataSource(): DataSource {
        if (databaseUrl.isBlank()) {
            throw IllegalStateException("Render profile active but DATABASE_URL is not set. Set it in Render dashboard.")
        }
        val (url, username, password) = parsePostgresUrl(databaseUrl)
        val ds = HikariDataSource().apply {
            jdbcUrl = url
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
        val host = hostPort.substringBeforeLast(':')
        val port = hostPort.substringAfterLast(':').takeIf { it != hostPort }?.toIntOrNull() ?: 5432
        val jdbcUrl = "jdbc:postgresql://$host:$port/$path"
        return Triple(jdbcUrl, user, password)
    }
}
