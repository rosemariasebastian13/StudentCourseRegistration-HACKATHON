# Course Registration System
## Setup Instructions and Extension Report

### 1. Project Overview

The Course Registration System is a desktop application developed using Java Swing for the graphical user interface and SQLite for database storage. JDBC is used to communicate with the database.

The system supports student registration and login, course browsing, course registration and cancellation, administrator course management, course prerequisites, and course completion certification.

### 2. Implemented Extensions

#### 2.1 Course Capacity and Enrollment Management

The system supports course capacity management and enrollment tracking.

- **Course capacity:** Each course has a maximum number of students who can register.
- **Enrollment count:** The number of students registered for each course is calculated from registration records.
- **Admin management:** Administrators can add courses, edit course details, change capacity, and control course availability.
- **Student dashboard:** Students can view available courses and their registered courses on separate pages.
- **Registration limit:** The registration process checks course availability and capacity before inserting a registration.
- **Data persistence:** Course details and registration records are stored in SQLite and remain available after restarting the application.

#### 2.2 Course Prerequisite Management

The system supports assigning one prerequisite course to another course.

- **Prerequisite assignment:** Administrators can select a prerequisite while adding or editing a course.
- **Prerequisite replacement:** Administrators can replace an existing prerequisite or select `None` to remove it.
- **Registration validation:** A student must complete the required prerequisite and receive completion certification before registering for the dependent course.
- **Missing prerequisite message:** If the student has not completed the prerequisite, registration is rejected with an error identifying the required course.
- **Circular dependency prevention:** The prerequisite repository checks for direct and indirect prerequisite cycles.
- **Existing registrations:** Changing a prerequisite affects future registration attempts and does not automatically cancel existing registrations.

#### 2.3 Course Completion Certification

Administrators can certify that a student has completed a course for which they are registered.

- **Registration-based certification:** Administrators can certify completion only for courses the student is registered for.
- **Confirmation dialog:** The system asks the administrator to confirm the student's name and course before certification.
- **Duplicate prevention:** A course completion cannot be certified twice for the same student.
- **Completion status:** The administrator can see whether a registration is `Completed` or `Not Completed`.
- **Student visibility:** Students can view completion status on the My Registrations page.
- **Admin-only action:** Students can view completion status but cannot certify their own completion.
- **Permanent certification:** The application does not provide an option to undo completion certification.

### 3. Relevant Code Changes

| File | Changes and responsibilities |
|---|---|
| `DatabaseInitializer.java` | Creates missing tables and migrates an existing database by adding the capacity column when required. Creates the prerequisite and completion tables without recreating existing data. |
| `Course.java` | Represents course information, including capacity and enrollment count. |
| `CourseRepository.java` | Retrieves courses and enrollment counts. Supports course insertion and updates, including connection-aware methods for transaction management. |
| `PrerequisiteRepository.java` | Assigns, replaces, and removes prerequisites; retrieves prerequisite information; lists eligible prerequisite choices; and prevents circular dependencies. |
| `RegistrationRepository.java` | Handles registration, cancellation, registration lookup, capacity and availability checks, prerequisite validation, completion certification, and retrieval of completion status. |
| `StudentDashboard.java` | Displays Available Courses and My Registrations separately. Shows completion status for registered courses. |
| `AdminDashboard.java` | Allows administrators to add, edit, and delete courses, manage prerequisites, control availability, and view capacity and enrollment information. |
| `AdminRegistrationsFrame.java` | Displays student registration details and provides the completion certification workflow for administrators. |
| `DBConnection.java` | Provides SQLite database connections through JDBC. |
| `Registration.java` | Represents a student's registration record. |
| `User.java` | Represents user information used by the application. |

### 4. Design Decisions

#### 4.1 Database Migration

The existing SQLite database is updated without recreating existing tables or intentionally deleting existing records. The initializer checks whether the capacity column exists before adding it.

The prerequisite and completion tables are created using `CREATE TABLE IF NOT EXISTS`. Existing courses do not automatically receive a prerequisite.

#### 4.2 Calculated Enrollment Count

Enrollment is calculated by counting records in the `registrations` table for each course. This avoids maintaining a separate enrollment counter that could become inconsistent with actual registrations.

#### 4.3 Separation of Responsibilities

The application separates responsibilities into model, repository, database, and user-interface classes.

- **Model:** Represents application data.
- **Repository:** Handles database queries and data operations.
- **Database:** Provides connections and initializes the schema.
- **User interface:** Provides the Swing screens and handles user interaction.

This separation improves maintainability and makes individual components easier to understand and test.

#### 4.4 User Interface Design

The student dashboard uses `CardLayout` to display Available Courses and My Registrations one at a time.

The administrator dashboard provides a table-based interface for managing courses. Prerequisites are selected within the existing Add Course and Edit Course dialogs.

The administrator registration screen displays completion status and provides a dedicated certification button.

#### 4.5 Capacity Validation

A course cannot accept a registration through the repository's normal registration process when it is unavailable or its enrollment count has reached capacity.

The administrator interface checks that capacity is positive and is not lower than the displayed enrollment count.

#### 4.6 Prerequisite Validation

Prerequisite validation is performed by `RegistrationRepository` before a registration is inserted.

When a course has a prerequisite, the student must have a corresponding entry in `student_course_completions`. Without that entry, registration is rejected.

The prerequisite repository checks the dependency chain to prevent direct and indirect circular prerequisites.

#### 4.7 Completion Certification

Completion records are stored in `student_course_completions`, with a unique student-course pair enforced by the table's primary key.

The certification operation checks that the student is registered for the course and prevents duplicate completion records.

Completion status is retrieved from the database rather than stored as a separate manually maintained status in the registration record.

#### 4.8 Transaction Management

Connection-aware repository methods allow related database operations to use a caller-managed connection and transaction.

When adding or editing a course together with its prerequisite, the operations are intended to commit together or roll back together if an operation fails.

### 5. Setup Instructions

#### Prerequisites

- Java Development Kit (JDK)
- Visual Studio Code with Java support, or another Java IDE
- SQLite JDBC driver JAR

#### Project Structure

The project should contain a structure similar to the following:

```text
CourseRegistrationSystem/
├── src/
│   ├── Main.java
│   ├── database/
│   │   ├── DBConnection.java
│   │   └── DatabaseInitializer.java
│   ├── model/
│   ├── repository/
│   │   ├── CourseRepository.java
│   │   ├── PrerequisiteRepository.java
│   │   ├── RegistrationRepository.java
│   │   └── UserRepository.java
│   ├── security/
│   └── ui/
│       ├── AdminDashboard.java
│       ├── AdminRegistrationsFrame.java
│       ├── StudentDashboard.java
│       └── LoginFrame.java
├── lib/
│   └── sqlite-jdbc-<version>.jar
├── student_registration.db
└── README.md
```

The exact file names may vary depending on the project version. The database file and JDBC driver must be available at the paths expected by the application.

#### Step 1: Open the Project

Open the `CourseRegistrationSystem` folder in Visual Studio Code.

#### Step 2: Verify Java

Open the integrated terminal and execute:

```powershell
java -version
javac -version
```

Both commands should report an installed Java version.

#### Step 3: Compile the Application

From the project root, execute:

```powershell
javac -cp "lib/*" -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
```

If compilation succeeds, the compiled classes will be placed in the `out` directory.

#### Step 4: Run the Application

If `Main.java` is in the default package, execute:

```powershell
java -cp "out;lib/*" Main
```

If the main class belongs to a named package, use its fully qualified class name instead.

#### Step 5: Initialize the Database

The main application should call `DatabaseInitializer.initialize()` during startup.

The initializer creates missing tables and adds the capacity column when necessary. It also creates the prerequisite and completion tables.

If the application uses the following JDBC URL:

```java
jdbc:sqlite:student_registration.db
```

run it from the project root so the relative database path points to the intended file.

**Important:** Back up the database before testing or modifying the project. Do not delete the existing database if you want to preserve users, courses, and registrations.

### 6. Testing Instructions

#### 6.1 Capacity and Enrollment

1. Log in as an administrator.
2. Add a course and specify its capacity.
3. Edit a course and verify its details and capacity.
4. Log in as a student and open Available Courses.
5. Register for a course and verify the enrollment count.
6. Confirm that unavailable or full courses reject new registrations.

#### 6.2 Prerequisite Management

1. Add a course with no prerequisite.
2. Edit the course and assign a prerequisite.
3. Attempt to register for the dependent course before completing the prerequisite.
4. Confirm that registration is rejected with a message naming the missing prerequisite.
5. Certify completion of the prerequisite as an administrator.
6. Retry registration for the dependent course.
7. Replace or remove the prerequisite and verify the change.
8. Attempt to create a direct or indirect circular prerequisite and confirm that it is rejected.

#### 6.3 Completion Certification

1. Open View Registrations from the administrator dashboard.
2. Select a student registration with status `Not Completed`.
3. Click Certify Completion.
4. Cancel the confirmation once and verify that the status remains unchanged.
5. Confirm certification and verify that the status changes to `Completed`.
6. Confirm that the certification button is disabled for a completed registration.
7. Attempt certification again and verify that duplicate completion is prevented.

#### 6.4 Student Completion Status

1. Log in as a student.
2. Open My Registrations.
3. Verify that each registered course displays its completion status.
4. Certify a course as an administrator.
5. Refresh the student dashboard and verify that the status changes to `Completed`.

#### 6.5 Persistence and Existing Data

1. Restart the application.
2. Confirm that existing users, courses, and registrations remain available.
3. Verify that prerequisite assignments and completion records persist.
4. Confirm that the application can initialize the database without recreating existing tables.

### 7. Conclusion

The Course Registration System has been extended with capacity-aware registration, enrollment reporting, course prerequisites, and administrator-controlled course completion certification.

The prerequisite feature prevents students from registering for dependent courses until the required course has been completed and certified. Administrators can manage prerequisites and certify completion, while students can view their completion status.

SQLite provides persistent storage, JDBC handles database communication, and Java Swing provides the graphical interface. The repository pattern separates database operations from the user interface, supporting maintainability and future extensions.

**Implementation note:** Successful compilation does not guarantee that every runtime workflow has been tested. Complete the testing procedures above before claiming that all features have passed functional testing.
