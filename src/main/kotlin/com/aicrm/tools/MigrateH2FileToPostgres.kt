package com.aicrm.tools

import com.aicrm.config.DbTableNames
import java.io.File
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Types

/**
 * Copy all AI CRM rows from an H2 file database (default profile / run-local.sh) into PostgreSQL.
 * Prerequisites: target DB exists and [schema-postgresql.sql] has been applied (use the shell wrapper).
 *
 * Env:
 *   H2_PATH — absolute or relative path to H2 DB **without** .mv.db (e.g. .../ai-crm-backend/data/crm)
 *   TARGET_JDBC_URL — e.g. jdbc:postgresql://localhost:5432/ai_crm_demo
 *   POSTGRES_USER, POSTGRES_PASSWORD — Postgres credentials (password may be empty for local trust auth)
 */
object MigrateH2FileToPostgres {

    @JvmStatic
    fun main(args: Array<String>) {
        val rawH2 = System.getenv("H2_PATH")?.trim()?.takeIf { it.isNotEmpty() }
            ?: File(System.getProperty("user.dir"), "data/crm").absolutePath
        val baseFile = File(rawH2.removeSuffix(".mv.db"))
        val mvFile = File(baseFile.absolutePath + ".mv.db")
        if (!mvFile.exists()) {
            error("H2 database file not found: ${mvFile.absolutePath} (run ./run-local.sh at least once to create data)")
        }

        val pgUrl = System.getenv("TARGET_JDBC_URL")?.trim()
            ?: "jdbc:postgresql://localhost:5432/ai_crm_demo"
        val pgUser = System.getenv("POSTGRES_USER") ?: System.getProperty("user.name")
        val pgPassword = System.getenv("POSTGRES_PASSWORD") ?: ""
        val tablePrefix = System.getenv("APP_DB_TABLE_PREFIX") ?: System.getenv("TABLE_PREFIX") ?: ""

        Class.forName("org.h2.Driver")
        Class.forName("org.postgresql.Driver")

        // AUTO_SERVER=TRUE matches application.yml so a second process can open the file DB while bootRun is up.
        val h2Jdbc = "jdbc:h2:file:${baseFile.absolutePath};AUTO_SERVER=TRUE"
        DriverManager.getConnection(h2Jdbc, "sa", "").use { h2 ->
            DriverManager.getConnection(pgUrl, pgUser, pgPassword).use { pg ->
                pg.autoCommit = false
                truncateTarget(pg, tablePrefix)
                for (table in DbTableNames.TABLES) {
                    val count = copyTable(h2, pg, tablePrefix, table)
                    println("Copied $count row(s) -> $table")
                }
                pg.commit()
            }
        }
        println("Migration finished.")
    }

    private fun truncateTarget(pg: java.sql.Connection, tablePrefix: String) {
        val targetTables =
            DbTableNames.TABLES.joinToString(", ") { DbTableNames.qualify(tablePrefix, it) }
        val sql = "TRUNCATE TABLE $targetTables CASCADE;"
        pg.createStatement().use { st ->
            st.execute(sql)
        }
        println("Target tables truncated (CRM only).")
    }

    private fun copyTable(h2: java.sql.Connection, pg: java.sql.Connection, tablePrefix: String, table: String): Int {
        h2.prepareStatement("SELECT * FROM $table").use { sel ->
            sel.executeQuery().use { rs ->
                val meta = rs.metaData
                val colCount = meta.columnCount
                if (colCount == 0) return 0
                val columns = (1..colCount).map { meta.getColumnName(it).lowercase() }
                val quoted = columns.joinToString(", ") { "\"$it\"" }
                val placeholders = columns.joinToString(", ") { "?" }
                val insertSql =
                    "INSERT INTO ${DbTableNames.qualify(tablePrefix, table)} ($quoted) VALUES ($placeholders)"
                pg.prepareStatement(insertSql).use { ins ->
                    var n = 0
                    while (rs.next()) {
                        bindRow(rs, meta, ins, colCount)
                        ins.addBatch()
                        n++
                    }
                    if (n > 0) {
                        ins.executeBatch()
                    }
                    return n
                }
            }
        }
    }

    private fun bindRow(rs: ResultSet, meta: ResultSetMetaData, ps: PreparedStatement, colCount: Int) {
        for (i in 1..colCount) {
            val sqlType = meta.getColumnType(i)
            when (sqlType) {
                Types.CLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.VARCHAR, Types.NVARCHAR, Types.CHAR, Types.NCHAR -> {
                    val s = rs.getString(i)
                    if (rs.wasNull()) ps.setNull(i, Types.VARCHAR) else ps.setString(i, s)
                }
                Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
                    val v = rs.getInt(i)
                    if (rs.wasNull()) ps.setNull(i, sqlType) else ps.setInt(i, v)
                }
                Types.BIGINT -> {
                    val v = rs.getLong(i)
                    if (rs.wasNull()) ps.setNull(i, sqlType) else ps.setLong(i, v)
                }
                Types.DOUBLE, Types.FLOAT, Types.REAL -> {
                    val v = rs.getDouble(i)
                    if (rs.wasNull()) ps.setNull(i, sqlType) else ps.setDouble(i, v)
                }
                Types.TIMESTAMP, Types.TIME -> {
                    val v = rs.getTimestamp(i)
                    if (rs.wasNull()) ps.setNull(i, Types.TIMESTAMP) else ps.setTimestamp(i, v)
                }
                Types.DATE -> {
                    val v = rs.getDate(i)
                    if (rs.wasNull()) ps.setNull(i, Types.DATE) else ps.setDate(i, v)
                }
                Types.BOOLEAN, Types.BIT -> {
                    val v = rs.getBoolean(i)
                    if (rs.wasNull()) ps.setNull(i, sqlType) else ps.setBoolean(i, v)
                }
                else -> {
                    val o = rs.getObject(i)
                    if (rs.wasNull()) ps.setNull(i, sqlType) else ps.setObject(i, o)
                }
            }
        }
    }
}
