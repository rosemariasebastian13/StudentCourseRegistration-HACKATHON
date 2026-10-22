package repository;

import database.DBConnection;
import model.Course;
import model.Registration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RegistrationRepository {

    // Register a student only if the course is available,
    // has capacity, and its prerequisite has been completed.
    public void save(int studentId, int courseId) {

        String prerequisiteSql = """
                SELECT prerequisite_course_id
                FROM course_prerequisites
                WHERE course_id = ?
                """;

        String prerequisiteNameSql = """
                SELECT c.course_name
                FROM courses c
                WHERE c.course_id = ?
                """;

        String completedSql = """
                SELECT 1
                FROM student_course_completions
                WHERE student_id = ? AND course_id = ?
                """;

        String registrationSql = """
                INSERT INTO registrations (student_id, course_id)
                SELECT ?, c.course_id
                FROM courses c
                WHERE c.course_id = ?
                  AND c.available = 1
                  AND (
                      SELECT COUNT(*)
                      FROM registrations r
                      WHERE r.course_id = c.course_id
                  ) < c.capacity
                """;

        try (Connection connection = DBConnection.getConnection()) {

            // Check the prerequisite before registering.
            try (PreparedStatement prerequisiteStatement =
                         connection.prepareStatement(prerequisiteSql)) {

                prerequisiteStatement.setInt(1, courseId);

                try (ResultSet prerequisiteResult =
                             prerequisiteStatement.executeQuery()) {

                    if (prerequisiteResult.next()) {

                        int prerequisiteCourseId =
                                prerequisiteResult.getInt(
                                        "prerequisite_course_id");

                        try (PreparedStatement completedStatement =
                                     connection.prepareStatement(completedSql)) {

                            completedStatement.setInt(1, studentId);
                            completedStatement.setInt(
                                    2, prerequisiteCourseId);

                            try (ResultSet completedResult =
                                         completedStatement.executeQuery()) {

                                if (!completedResult.next()) {

                                    String prerequisiteName;

                                    try (PreparedStatement nameStatement =
                                                 connection.prepareStatement(
                                                         prerequisiteNameSql)) {

                                        nameStatement.setInt(
                                                1, prerequisiteCourseId);

                                        try (ResultSet nameResult =
                                                     nameStatement.executeQuery()) {

                                            prerequisiteName =
                                                    nameResult.next()
                                                            ? nameResult.getString(
                                                                    "course_name")
                                                            : "the required course";
                                        }
                                    }

                                    throw new IllegalArgumentException(
                                            "You must complete and receive "
                                                    + "certification for "
                                                    + prerequisiteName
                                                    + " before registering "
                                                    + "for this course.");
                                }
                            }
                        }
                    }
                }
            }

            // Keep the existing availability and capacity checks.
            try (PreparedStatement statement =
                         connection.prepareStatement(registrationSql)) {

                statement.setInt(1, studentId);
                statement.setInt(2, courseId);

                int rowsInserted = statement.executeUpdate();

                if (rowsInserted == 0) {
                    throw new IllegalArgumentException(
                            "Course is unavailable, full, or not found.");
                }
            }

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not register student. The student may already "
                            + "be registered for this course, or the student "
                            + "or course ID may be invalid.", e);
        }
    }

    // Cancel a student's registration.
    public void delete(int studentId, int courseId) {

        String sql = """
                DELETE FROM registrations
                WHERE student_id = ? AND course_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);
            statement.setInt(2, courseId);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not cancel registration.", e);
        }
    }

    // Find registration records belonging to a student.
    public List<Registration> findByStudentId(int studentId) {

        String sql = """
                SELECT registration_id, student_id, course_id,
                       registration_date
                FROM registrations
                WHERE student_id = ?
                ORDER BY registration_date DESC
                """;

        List<Registration> registrations = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    registrations.add(new Registration(
                            result.getInt("registration_id"),
                            result.getInt("student_id"),
                            result.getInt("course_id"),
                            result.getString("registration_date")
                    ));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not retrieve student registrations.", e);
        }

        return registrations;
    }

    // Check whether a student is registered for a course.
    public boolean exists(int studentId, int courseId) {

        String sql = """
                SELECT 1
                FROM registrations
                WHERE student_id = ? AND course_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);
            statement.setInt(2, courseId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not check registration.", e);
        }
    }

    // Find registered courses with capacity and enrolled count.
    public List<Course> findRegisteredCourses(int studentId) {

        String sql = """
                SELECT c.course_id,
                       c.course_code,
                       c.course_name,
                       c.credits,
                       c.available,
                       c.capacity,
                       COUNT(allr.registration_id) AS enrolled
                FROM registrations studentr
                JOIN courses c
                    ON c.course_id = studentr.course_id
                LEFT JOIN registrations allr
                    ON allr.course_id = c.course_id
                WHERE studentr.student_id = ?
                GROUP BY c.course_id
                ORDER BY c.course_code
                """;

        List<Course> courses = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            try (ResultSet result = statement.executeQuery()) {
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
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not retrieve registered courses.", e);
        }

        return courses;
    }

    // Get registration details and completion status for the admin.
    public List<Object[]> findAllRegistrationDetails() {

        String sql = """
                SELECT r.registration_id,
                       u.user_id AS student_id,
                       u.first_name,
                       u.last_name,
                       u.username,
                       c.course_id,
                       c.course_code,
                       c.course_name,
                       r.registration_date,
                       CASE
                           WHEN completion.student_id IS NOT NULL
                           THEN 'Completed'
                           ELSE 'Not Completed'
                       END AS completion_status
                FROM registrations r
                JOIN users u
                    ON u.user_id = r.student_id
                JOIN courses c
                    ON c.course_id = r.course_id
                LEFT JOIN student_course_completions completion
                    ON completion.student_id = r.student_id
                   AND completion.course_id = r.course_id
                WHERE u.role = 'STUDENT'
                ORDER BY u.last_name, u.first_name, c.course_code
                """;

        List<Object[]> registrations = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                String fullName = (
                        result.getString("first_name") + " "
                        + result.getString("last_name")).trim();

                registrations.add(new Object[]{
                        result.getInt("registration_id"),
                        result.getInt("student_id"),
                        fullName,
                        result.getString("username"),
                        result.getInt("course_id"),
                        result.getString("course_code"),
                        result.getString("course_name"),
                        result.getString("registration_date"),
                        result.getString("completion_status")
                });
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not retrieve all registration details.", e);
        }

        return registrations;
    }
    
    // Certify completion only when the student is registered.
    // The database primary key prevents duplicate certification.
    public void certifyCompletion(int studentId, int courseId) {

        String sql = """
                INSERT INTO student_course_completions
                    (student_id, course_id)
                SELECT r.student_id, r.course_id
                FROM registrations r
                WHERE r.student_id = ?
                  AND r.course_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM student_course_completions completion
                      WHERE completion.student_id = r.student_id
                        AND completion.course_id = r.course_id
                  )
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);
            statement.setInt(2, courseId);

            int rowsInserted = statement.executeUpdate();

            if (rowsInserted == 0) {
                throw new IllegalArgumentException(
                        "The student is not registered for this course, "
                        + "or completion has already been certified.");
            }

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not certify course completion.", e);
        }
    }
    
    // Get registered courses and their completion status for a student.
    public List<Object[]> findRegisteredCoursesWithStatus(int studentId) {

        String sql = """
                SELECT c.course_id,
                       c.course_code,
                       c.course_name,
                       c.credits,
                       c.capacity,
                       (
                           SELECT COUNT(*)
                           FROM registrations allr
                           WHERE allr.course_id = c.course_id
                       ) AS enrolled,
                       CASE
                           WHEN completion.student_id IS NOT NULL
                           THEN 'Completed'
                           ELSE 'Not Completed'
                       END AS completion_status
                FROM registrations r
                JOIN courses c
                    ON c.course_id = r.course_id
                LEFT JOIN student_course_completions completion
                    ON completion.student_id = r.student_id
                   AND completion.course_id = r.course_id
                WHERE r.student_id = ?
                ORDER BY c.course_code
                """;

        List<Object[]> registrations = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    registrations.add(new Object[]{
                            result.getInt("course_id"),
                            result.getString("course_code"),
                            result.getString("course_name"),
                            result.getInt("credits"),
                            result.getInt("capacity"),
                            result.getInt("enrolled"),
                            result.getString("completion_status")
                    });
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not retrieve registered courses and completion status.",
                    e);
        }

        return registrations;
    }


}