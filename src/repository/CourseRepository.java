
package repository;

import database.DBConnection;
import model.Course;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CourseRepository {

    // Shared query: enrolled is calculated from actual registrations.
    private static final String COURSE_SELECT = """
            SELECT c.course_id, c.course_code, c.course_name,
                   c.credits, c.available, c.capacity,
                   COUNT(r.registration_id) AS enrolled
            FROM courses c
            LEFT JOIN registrations r
                ON c.course_id = r.course_id
            """;

    public List<Course> findAvailableCourses() {

        String sql = COURSE_SELECT + """
                WHERE c.available = 1
                GROUP BY c.course_id
                ORDER BY c.course_code
                """;

        return executeCourseQuery(sql,
                "Could not retrieve available courses.");
    }

    public List<Course> findAll() {

        String sql = COURSE_SELECT + """
                GROUP BY c.course_id
                ORDER BY c.course_code
                """;

        return executeCourseQuery(sql,
                "Could not retrieve all courses.");
    }

    private List<Course> executeCourseQuery(

        
            String sql, String errorMessage) {

        List<Course> courses = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                courses.add(new Course(
                        result.getInt("course_id"),
                        result.getString("course_code"),
                        result.getString("course_name"),
                        result.getInt("credits"),
                        result.getInt("available") == 1,
                        result.getInt("capacity"),
                        result.getInt("enrolled")
                ));
            }

        } catch (SQLException e) {
            throw new RuntimeException(errorMessage, e);
        }

        return courses;
    }

    public void save(Course course) {

        String sql = """
                INSERT INTO courses
                    (course_code, course_name, credits,
                     available, capacity)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, course.getCourseCode());
            statement.setString(2, course.getCourseName());
            statement.setInt(3, course.getCredits());
            statement.setInt(4, course.isAvailable() ? 1 : 0);
            statement.setInt(5, course.getCapacity());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Could not save course.", e);
        }
    }

    public void update(Course course) {

        String sql = """
                UPDATE courses
                SET course_code = ?, course_name = ?,
                    credits = ?, available = ?, capacity = ?
                WHERE course_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, course.getCourseCode());
            statement.setString(2, course.getCourseName());
            statement.setInt(3, course.getCredits());
            statement.setInt(4, course.isAvailable() ? 1 : 0);
            statement.setInt(5, course.getCapacity());
            statement.setInt(6, course.getCourseId());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated == 0) {
                throw new IllegalArgumentException(
                        "Course not found: " + course.getCourseId());
            }

        } catch (SQLException e) {
            throw new RuntimeException("Could not update course.", e);
        }
    }

    public void deleteById(int courseId) {

        String sql = "DELETE FROM courses WHERE course_id = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, courseId);

            int rowsDeleted = statement.executeUpdate();

            if (rowsDeleted == 0) {
                throw new IllegalArgumentException(
                        "Course not found: " + courseId);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Could not delete course.", e);
        }
    }
    
/**
 * Inserts a course using a caller-managed connection.
 * The caller must commit or roll back the transaction.
 */
public int save(Connection connection, Course course)
        throws SQLException {

    String sql = """
            INSERT INTO courses
                (course_code, course_name, credits, available, capacity)
            VALUES (?, ?, ?, ?, ?)
            """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, course.getCourseCode());
        statement.setString(2, course.getCourseName());
        statement.setInt(3, course.getCredits());
        statement.setInt(4, course.isAvailable() ? 1 : 0);
        statement.setInt(5, course.getCapacity());

        statement.executeUpdate();
    }

    // Get the ID generated by this connection's most recent insert.
    try (PreparedStatement statement =
                 connection.prepareStatement("SELECT last_insert_rowid()");
         ResultSet result = statement.executeQuery()) {

        if (result.next()) {
            return result.getInt(1);
        }

        throw new SQLException("Could not retrieve the new course ID.");
    }
}

/**
 * Updates a course using a caller-managed connection.
 * The caller must commit or roll back the transaction.
 */
public void update(Connection connection, Course course)
        throws SQLException {

    String sql = """
            UPDATE courses
            SET course_code = ?, course_name = ?,
                credits = ?, available = ?, capacity = ?
            WHERE course_id = ?
            """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, course.getCourseCode());
        statement.setString(2, course.getCourseName());
        statement.setInt(3, course.getCredits());
        statement.setInt(4, course.isAvailable() ? 1 : 0);
        statement.setInt(5, course.getCapacity());
        statement.setInt(6, course.getCourseId());

        int rowsUpdated = statement.executeUpdate();

        if (rowsUpdated == 0) {
            throw new IllegalArgumentException(
                    "Course not found: " + course.getCourseId());
        }
    }
}

}
