
package database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    public static void initialize() {

        String usersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    first_name TEXT NOT NULL DEFAULT '',
                    last_name TEXT NOT NULL DEFAULT '',
                    role TEXT NOT NULL DEFAULT 'STUDENT'
                        CHECK (role IN ('STUDENT', 'ADMIN'))
                )
                """;

        String coursesTable = """
                CREATE TABLE IF NOT EXISTS courses (
                    course_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    course_code TEXT NOT NULL UNIQUE,
                    course_name TEXT NOT NULL,
                    credits INTEGER NOT NULL CHECK (credits > 0),
                    available INTEGER NOT NULL DEFAULT 1
                        CHECK (available IN (0, 1)),
                    capacity INTEGER NOT NULL DEFAULT 60
                        CHECK (capacity > 0)
                )
                """;

        String registrationsTable = """
                CREATE TABLE IF NOT EXISTS registrations (
                    registration_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id INTEGER NOT NULL
                        REFERENCES users(user_id) ON DELETE CASCADE,
                    course_id INTEGER NOT NULL
                        REFERENCES courses(course_id) ON DELETE CASCADE,
                    registration_date TEXT NOT NULL
                        DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (student_id, course_id)
                )
                """;

        // A course can have at most one prerequisite.
        String prerequisitesTable = """
                CREATE TABLE IF NOT EXISTS course_prerequisites (
                    course_id INTEGER PRIMARY KEY,
                    prerequisite_course_id INTEGER NOT NULL,

                    FOREIGN KEY (course_id)
                        REFERENCES courses(course_id)
                        ON DELETE CASCADE,

                    FOREIGN KEY (prerequisite_course_id)
                        REFERENCES courses(course_id)
                        ON DELETE CASCADE,

                    CHECK (course_id <> prerequisite_course_id)
                )
                """;

        // Records successful course completions certified by an admin.
        String completionsTable = """
                CREATE TABLE IF NOT EXISTS student_course_completions (
                    student_id INTEGER NOT NULL,
                    course_id INTEGER NOT NULL,
                    completed_at TEXT NOT NULL
                        DEFAULT CURRENT_TIMESTAMP,

                    PRIMARY KEY (student_id, course_id),

                    FOREIGN KEY (student_id)
                        REFERENCES users(user_id)
                        ON DELETE CASCADE,

                    FOREIGN KEY (course_id)
                        REFERENCES courses(course_id)
                        ON DELETE CASCADE
                )
                """;

        try (Connection connection = DBConnection.getConnection();
             Statement statement = connection.createStatement()) {

            // Preserve existing tables and records.
            statement.execute(usersTable);
            statement.execute(coursesTable);
            statement.execute(registrationsTable);

            // Add capacity to older databases if necessary.
            boolean capacityExists = false;

            try (ResultSet columns =
                         statement.executeQuery("PRAGMA table_info(courses)")) {

                while (columns.next()) {
                    if ("capacity".equalsIgnoreCase(
                            columns.getString("name"))) {
                        capacityExists = true;
                        break;
                    }
                }
            }

            if (!capacityExists) {
                statement.execute("""
                        ALTER TABLE courses
                        ADD COLUMN capacity INTEGER NOT NULL DEFAULT 30
                        CHECK (capacity > 0)
                        """);

                System.out.println("Added capacity column to courses.");
            }

            // Create the new tables after courses and users exist.
            statement.execute(prerequisitesTable);
            statement.execute(completionsTable);

            System.out.println("Database initialized successfully!");

        } catch (SQLException e) {
            System.out.println("Failed to initialize database.");
            e.printStackTrace();
        }
    }
}
