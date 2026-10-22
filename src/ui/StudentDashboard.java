package ui;

import model.Course;
import model.User;
import repository.CourseRepository;
import repository.RegistrationRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class StudentDashboard extends JFrame {

    private final User user;

    private final CourseRepository courseRepository =
            new CourseRepository();

    private final RegistrationRepository registrationRepository =
            new RegistrationRepository();

    // Navigation pages
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    private static final String AVAILABLE_PAGE = "AVAILABLE";
    private static final String REGISTERED_PAGE = "REGISTERED";

    // Available courses table
    private final DefaultTableModel availableModel =
            createTableModel(false);

    private final JTable availableTable =
            new JTable(availableModel);

    // Registered courses table
    private final DefaultTableModel registeredModel =
            createTableModel(true);

    private final JTable registeredTable =
            new JTable(registeredModel);

    private final JButton availableNavButton =
            new JButton("Available Courses");

    private final JButton registeredNavButton =
            new JButton("My Registrations");

    private final JLabel pageTitle =
            new JLabel("Available Courses");

    public StudentDashboard(User user) {

        this.user = user;

        setTitle("Student Dashboard");
        setSize(1000, 600);
        setMinimumSize(new Dimension(800, 500));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        buildInterface();

        refreshData();
        showPage(AVAILABLE_PAGE);
    }

    // Completion status is included only in the registered courses table.
    private DefaultTableModel createTableModel(boolean includeStatus) {

        String[] columns;

        if (includeStatus) {
            columns = new String[]{
                    "ID",
                    "Course Code",
                    "Course Name",
                    "Credits",
                    "Capacity",
                    "Enrolled",
                    "Completion Status"
            };
        } else {
            columns = new String[]{
                    "ID",
                    "Course Code",
                    "Course Name",
                    "Credits",
                    "Capacity",
                    "Enrolled"
            };
        }

        return new DefaultTableModel(columns, 0) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void buildInterface() {

        // Top header
        JLabel welcomeLabel =
                new JLabel("Welcome, " + user.getFullName());

        welcomeLabel.setFont(
                new Font("Arial", Font.BOLD, 20));

        JButton refreshButton = new JButton("Refresh");
        JButton logoutButton = new JButton("Logout");

        JPanel topButtons = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 10, 0));

        topButtons.add(refreshButton);
        topButtons.add(logoutButton);

        JPanel header = new JPanel(new BorderLayout());

        header.setBorder(
                BorderFactory.createEmptyBorder(15, 18, 15, 18));

        header.add(welcomeLabel, BorderLayout.WEST);
        header.add(topButtons, BorderLayout.EAST);

        // Left navigation menu
        JPanel navigationPanel = new JPanel();

        navigationPanel.setLayout(
                new BoxLayout(navigationPanel, BoxLayout.Y_AXIS));

        navigationPanel.setBorder(
                BorderFactory.createEmptyBorder(15, 10, 15, 10));

        navigationPanel.setPreferredSize(
                new Dimension(200, 0));

        JLabel menuTitle = new JLabel("STUDENT MENU");

        menuTitle.setFont(
                new Font("Arial", Font.BOLD, 14));

        menuTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        availableNavButton.setAlignmentX(
                Component.LEFT_ALIGNMENT);

        registeredNavButton.setAlignmentX(
                Component.LEFT_ALIGNMENT);

        availableNavButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42));

        registeredNavButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42));

        navigationPanel.add(menuTitle);
        navigationPanel.add(Box.createVerticalStrut(20));
        navigationPanel.add(availableNavButton);
        navigationPanel.add(Box.createVerticalStrut(10));
        navigationPanel.add(registeredNavButton);
        navigationPanel.add(Box.createVerticalGlue());

        // Available courses page
        JPanel availablePage =
                new JPanel(new BorderLayout(10, 10));

        availablePage.setBorder(
                BorderFactory.createEmptyBorder(15, 15, 15, 15));

        configureTable(availableTable);
        hideIdColumn(availableTable);

        JButton registerButton =
                new JButton("Register for Selected Course");

        JPanel availableBottom = new JPanel(
                new FlowLayout(FlowLayout.RIGHT));

        availableBottom.add(registerButton);

        availablePage.add(
                new JScrollPane(availableTable),
                BorderLayout.CENTER);

        availablePage.add(
                availableBottom,
                BorderLayout.SOUTH);

        // My registrations page
        JPanel registeredPage =
                new JPanel(new BorderLayout(10, 10));

        registeredPage.setBorder(
                BorderFactory.createEmptyBorder(15, 15, 15, 15));

        configureTable(registeredTable);
        hideIdColumn(registeredTable);

        JButton cancelButton =
                new JButton("Cancel Selected Registration");

        JPanel registeredBottom = new JPanel(
                new FlowLayout(FlowLayout.RIGHT));

        registeredBottom.add(cancelButton);

        registeredPage.add(
                new JScrollPane(registeredTable),
                BorderLayout.CENTER);

        registeredPage.add(
                registeredBottom,
                BorderLayout.SOUTH);

        // Show one page at a time.
        contentPanel.add(availablePage, AVAILABLE_PAGE);
        contentPanel.add(registeredPage, REGISTERED_PAGE);

        // Main content area
        JPanel mainPanel = new JPanel(new BorderLayout());

        pageTitle.setFont(
                new Font("Arial", Font.BOLD, 18));

        pageTitle.setBorder(
                BorderFactory.createEmptyBorder(15, 18, 5, 18));

        mainPanel.add(pageTitle, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        JPanel bodyPanel = new JPanel(new BorderLayout());

        bodyPanel.add(navigationPanel, BorderLayout.WEST);
        bodyPanel.add(mainPanel, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(bodyPanel, BorderLayout.CENTER);

        // Button actions
        refreshButton.addActionListener(
                e -> refreshData());

        logoutButton.addActionListener(e -> {
            new LoginFrame().setVisible(true);
            dispose();
        });

        availableNavButton.addActionListener(
                e -> showPage(AVAILABLE_PAGE));

        registeredNavButton.addActionListener(
                e -> showPage(REGISTERED_PAGE));

        registerButton.addActionListener(
                e -> registerForCourse());

        cancelButton.addActionListener(
                e -> cancelRegistration());
    }

    // Configure table selection and sorting.
    private void configureTable(JTable table) {

        table.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);

        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
    }

    // Hide only the displayed ID column.
    // The ID remains in the underlying table model.
    private void hideIdColumn(JTable table) {

        table.removeColumn(
                table.getColumnModel().getColumn(0));
    }

    // Switch between the two pages.
    private void showPage(String page) {

        cardLayout.show(contentPanel, page);

        if (AVAILABLE_PAGE.equals(page)) {
            pageTitle.setText("Available Courses");
        } else {
            pageTitle.setText("My Registrations");
        }
    }

    // Refresh both tables.
    private void refreshData() {

        loadAvailableCourses();
        loadRegisteredCourses();
    }

    // Load courses that are marked available.
    private void loadAvailableCourses() {

        try {
            List<Course> courses =
                    courseRepository.findAvailableCourses();

            availableModel.setRowCount(0);

            for (Course course : courses) {
                availableModel.addRow(new Object[]{
                        course.getCourseId(),
                        course.getCourseCode(),
                        course.getCourseName(),
                        course.getCredits(),
                        course.getCapacity(),
                        course.getEnrolled()
                });
            }

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load available courses:\n"
                            + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Load registered courses and their completion status.
    private void loadRegisteredCourses() {

        try {
            List<Object[]> registrations =
                    registrationRepository.findRegisteredCoursesWithStatus(
                            user.getUserId());

            registeredModel.setRowCount(0);

            for (Object[] registration : registrations) {
                registeredModel.addRow(registration);
            }

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load registered courses:\n"
                            + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Get the selected course, including its hidden ID.
    private Course getSelectedCourse(
            JTable table, DefaultTableModel model) {

        int selectedRow = table.getSelectedRow();

        if (selectedRow < 0) {
            return null;
        }

        int modelRow =
                table.convertRowIndexToModel(selectedRow);

        int courseId =
                (int) model.getValueAt(modelRow, 0);

        String code =
                (String) model.getValueAt(modelRow, 1);

        String name =
                (String) model.getValueAt(modelRow, 2);

        int credits =
                (int) model.getValueAt(modelRow, 3);

        int capacity =
                (int) model.getValueAt(modelRow, 4);

        int enrolled =
                (int) model.getValueAt(modelRow, 5);

        return new Course(
                courseId,
                code,
                name,
                credits,
                true,
                capacity,
                enrolled);
    }

    // Register the student for the selected course.
    private void registerForCourse() {

        Course selectedCourse =
                getSelectedCourse(
                        availableTable, availableModel);

        if (selectedCourse == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select a course from the table first.");
            return;
        }

        try {
            registrationRepository.save(
                    user.getUserId(),
                    selectedCourse.getCourseId());

            JOptionPane.showMessageDialog(
                    this,
                    "Successfully registered for "
                            + selectedCourse.getCourseName() + "!");

            refreshData();

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Registration failed.\n" + e.getMessage(),
                    "Registration Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Cancel the selected registration.
    private void cancelRegistration() {

        Course selectedCourse =
                getSelectedCourse(
                        registeredTable, registeredModel);

        if (selectedCourse == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select a course from My Registrations first.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Cancel registration for "
                        + selectedCourse.getCourseName() + "?",
                "Confirm Cancellation",
                JOptionPane.YES_NO_OPTION);

        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            registrationRepository.delete(
                    user.getUserId(),
                    selectedCourse.getCourseId());

            JOptionPane.showMessageDialog(
                    this,
                    "Registration cancelled successfully.");

            refreshData();

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not cancel registration:\n"
                            + e.getMessage(),
                    "Registration Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}