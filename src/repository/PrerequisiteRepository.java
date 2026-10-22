
package repository;

import database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PrerequisiteRepository {

    /**
     * Assigns, replaces, or removes a course prerequisite.
     * Pass null as prerequisiteCourseId to remove the prerequisite.
     */
    public void setPrerequisite(int courseId, Integer prerequisiteCourseId) {

        if (prerequisiteCourseId != null
                && courseId == prerequisiteCourseId) {
            throw new IllegalArgumentException(
                    "A course cannot be its own prerequisite.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                // Verify that the target course exists.
                if (!courseExists(connection, courseId)) {
                    throw new IllegalArgumentException(
                            "The selected course does not exist.");
                }

                if (prerequisiteCourseId != null) {
                    if (!courseExists(connection, prerequisiteCourseId)) {
                        throw new IllegalArgumentException(
                                "The prerequisite course does not exist.");
                    }

                    if (wouldCreateCycle(
                            connection, courseId, prerequisiteCourseId)) {
                        throw new IllegalArgumentException(
                                "This prerequisite would create a circular dependency.");
                    }
                }

                // Remove the previous prerequisite first.
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                     DELETE FROM course_prerequisites
                                     WHERE course_id = ?
                                     """)) {
                    statement.setInt(1, courseId);
                    statement.executeUpdate();
                }

                // Add the new prerequisite, if one was selected.
                if (prerequisiteCourseId != null) {
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                                         INSERT INTO course_prerequisites
                                             (course_id, prerequisite_course_id)
                                         VALUES (?, ?)
                                         """)) {
                        statement.setInt(1, courseId);
                        statement.setInt(2, prerequisiteCourseId);
                        statement.executeUpdate();
                    }
                }

                connection.commit();

           } catch (SQLException | RuntimeException e) {
          connection.rollback();
              throw e;
          } finally {
             connection.setAutoCommit(true);
    }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not save course prerequisite.", e);
        }
    }

    /**
     * Returns the prerequisite course ID, or null if none exists.
     */
    public Integer findPrerequisiteId(int courseId) {
        String sql = """
                SELECT prerequisite_course_id
                FROM course_prerequisites
                WHERE course_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, courseId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("prerequisite_course_id");
                }
                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not retrieve course prerequisite.", e);
        }
    }

    /**
     * Returns the prerequisite course name, or null if none exists.
     */
    public String findPrerequisiteName(int courseId) {
        String sql = """
                SELECT c.course_name
                FROM course_prerequisites cp
                JOIN courses c
                    ON c.course_id = cp.prerequisite_course_id
                WHERE cp.course_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, courseId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? result.getString("course_name")
                        : null;
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not retrieve prerequisite name.", e);
        }
    }

    /**
     * Lists every course except the course currently being edited.
     * Includes unavailable courses.
     */
    public List<CourseOption> findOtherCourses(int courseId) {
        List<CourseOption> courses = new ArrayList<>();

        String sql = """
                SELECT course_id, course_code, course_name
                FROM courses
                WHERE course_id <> ?
                ORDER BY course_code
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, courseId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    courses.add(new CourseOption(
                            result.getInt("course_id"),
                            result.getString("course_code"),
                            result.getString("course_name")));
                }
            }

            return courses;

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not load prerequisite courses.", e);
        }
    }

    /**
     * Checks whether assigning prerequisiteCourseId to courseId
     * would create a direct or indirect cycle.
     *
     * If A requires B, then B cannot require A or any course
     * that eventually depends on B.
     */
    private boolean wouldCreateCycle(
            Connection connection,
            int courseId,
            int prerequisiteCourseId) throws SQLException {

        int currentId = prerequisiteCourseId;
        List<Integer> visited = new ArrayList<>();

        while (true) {
            if (currentId == courseId) {
                return true;
            }

            if (visited.contains(currentId)) {
                // Existing cycle encountered; reject further changes
                // along this dependency chain.
                return true;
            }

            visited.add(currentId);

            try (PreparedStatement statement =
                         connection.prepareStatement("""
                                 SELECT prerequisite_course_id
                                 FROM course_prerequisites
                                 WHERE course_id = ?
                                 """)) {

                statement.setInt(1, currentId);

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return false;
                    }

                    currentId = result.getInt("prerequisite_course_id");
                }
            }
        }
    }

    private boolean courseExists(
            Connection connection, int courseId) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement("""
                             SELECT 1 FROM courses WHERE course_id = ?
                             """)) {

            statement.setInt(1, courseId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /**
     * Simple item for the prerequisite list selector.
     */
    public static class CourseOption {
        private final int courseId;
        private final String courseCode;
        private final String courseName;

        public CourseOption(
                int courseId, String courseCode, String courseName) {
            this.courseId = courseId;
            this.courseCode = courseCode;
            this.courseName = courseName;
        }

        public int getCourseId() {
            return courseId;
        }

        public String getCourseCode() {
            return courseCode;
        }

        public String getCourseName() {
            return courseName;
        }

        @Override
        public String toString() {
            return courseCode + " - " + courseName;
        }
    }
    
/**
 * Saves a prerequisite using a caller-managed connection.
 * The caller is responsible for committing or rolling back.
 */
public void setPrerequisite(
        Connection connection,
        int courseId,
        Integer prerequisiteCourseId) throws SQLException {

    if (!courseExists(connection, courseId)) {
        throw new IllegalArgumentException(
                "The selected course does not exist.");
    }

    if (prerequisiteCourseId != null) {
        if (courseId == prerequisiteCourseId) {
            throw new IllegalArgumentException(
                    "A course cannot be its own prerequisite.");
        }

        if (!courseExists(connection, prerequisiteCourseId)) {
            throw new IllegalArgumentException(
                    "The prerequisite course does not exist.");
        }

        if (wouldCreateCycle(
                connection, courseId, prerequisiteCourseId)) {
            throw new IllegalArgumentException(
                    "This prerequisite would create a circular dependency.");
        }
    }

    try (PreparedStatement statement = connection.prepareStatement("""
            DELETE FROM course_prerequisites
            WHERE course_id = ?
            """)) {
        statement.setInt(1, courseId);
        statement.executeUpdate();
    }

    if (prerequisiteCourseId != null) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO course_prerequisites
                    (course_id, prerequisite_course_id)
                VALUES (?, ?)
                """)) {
            statement.setInt(1, courseId);
            statement.setInt(2, prerequisiteCourseId);
            statement.executeUpdate();
        }
    }
}

}
