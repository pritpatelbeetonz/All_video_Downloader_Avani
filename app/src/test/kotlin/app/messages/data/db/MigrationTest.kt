package app.messages.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runtime test for [MIGRATION_2_3] on a real SQLite (sqlite-jdbc), on the JVM —
 * no device, emulator, or instrumented `room-testing` artifact required.
 *
 * We seed an in-memory database with the v2 `thread_flags` schema, then drive the
 * migration's **actual** `execSQL` calls into it via a thin [SupportSQLiteDatabase]
 * stub and assert that:
 *   - existing rows and their pin/archive/mute flags survive the upgrade, and
 *   - the new `starred` column is `NOT NULL DEFAULT 0`.
 *
 * (Full Room schema-equality validation — migrated DB == fresh v3 — is what the
 * instrumented `MigrationTestHelper` adds; wire that into CI where the artifact
 * downloads. The `@ColumnInfo(defaultValue = "0")` on the entity keeps the two
 * schemas identical, which this test's DEFAULT-0 assertions exercise.)
 */
class MigrationTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        // v2 schema (schemas/2.json): thread_flags before the `starred` column.
        conn.createStatement().use { st ->
            st.executeUpdate(
                "CREATE TABLE thread_flags (" +
                    "threadId TEXT NOT NULL PRIMARY KEY, " +
                    "pinned INTEGER NOT NULL, " +
                    "archived INTEGER NOT NULL, " +
                    "muted INTEGER NOT NULL)",
            )
            st.executeUpdate("INSERT INTO thread_flags VALUES ('t1', 1, 0, 1)")
        }
    }

    @After
    fun tearDown() {
        conn.close()
    }

    @Test
    fun migrate2to3_addsStarredColumn_andPreservesExistingFlags() {
        // A SupportSQLiteDatabase whose execSQL runs against the real SQLite above.
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers {
            conn.createStatement().use { it.execute(firstArg<String>()) }
        }

        MIGRATION_2_3.migrate(db)

        // (a) the existing row + its flags survive; (b) new column defaulted to 0.
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT pinned, archived, muted, starred FROM thread_flags WHERE threadId = 't1'",
            ).use { rs ->
                assertTrue("the existing row must survive the migration", rs.next())
                assertEquals(1, rs.getInt("pinned"))
                assertEquals(0, rs.getInt("archived"))
                assertEquals(1, rs.getInt("muted"))
                assertEquals("new `starred` column must default to 0", 0, rs.getInt("starred"))
            }
        }

        // The column is NOT NULL DEFAULT 0: an insert that omits it must succeed
        // and read back 0 (proves the default matches the entity's @ColumnInfo).
        conn.createStatement().use {
            it.execute("INSERT INTO thread_flags(threadId, pinned, archived, muted) VALUES ('t2', 0, 0, 0)")
        }
        conn.createStatement().use { st ->
            st.executeQuery("SELECT starred FROM thread_flags WHERE threadId = 't2'").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt("starred"))
            }
        }
    }
}
