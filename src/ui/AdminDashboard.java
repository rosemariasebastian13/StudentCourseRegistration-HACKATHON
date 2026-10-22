package ui;

import database.DBConnection;
import model.Course;
import model.User;
import repository.CourseRepository;
import repository.PrerequisiteRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AdminDashboard extends JFrame {

    private final User user;

    private final CourseRepository courseRepository =
            new CourseRepository();

    private final PrerequisiteRepository prerequisiteRepository =
            new PrerequisiteRepository();

    private final DefaultTableModel tableModel =
            new DefaultTableModel(
                    new String[]{
                            "ID",
                            "Code",
                            "Course Name",
                            "Credits",
                            "Capacity",
                            "Enrolled",
                            "Available"
                    }, 0) {

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    private final JTable courseTable = new JTable(tableModel);

    public AdminDashboard(User user) {

        this.user = user;

        setTitle("Admin Dashboard");
        setSize(1000, 550);
        setMinimumSize(new Dimension(850, 450));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JLabel title = new JLabel(
                "Admin Dashboard - " + user.getFullName());

        title.setFont(new Font("Arial", Font.BOLD, 20));

        JButton addButton = new JButton("Add Course");
        JButton updateButton = new JButton("Edit Course");
        JButton deleteButton = new JButton("Delete Course");
        JButton registrationsButton =
                new JButton("View Registrations");
        JButton refreshButton = new JButton("Refresh");
        JButton logoutButton = new JButton("Logout");

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(
                BorderFactory.createEmptyBorder(12, 15, 12, 15));

        topPanel.add(title, BorderLayout.WEST);
        topPanel.add(refreshButton, BorderLayout.EAST);

        JPanel buttonPanel = new JPanel(new FlowLayout());

        buttonPanel.add(addButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(registrationsButton);
        buttonPanel.add(logoutButton);

        courseTable.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        courseTable.setAutoCreateRowSorter(true);
        courseTable.setFillsViewportHeight(true);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(courseTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        addButton.addActionListener(e -> addCourse());
        updateButton.addActionListener(e -> updateCourse());
        deleteButton.addActionListener(e -> deleteCourse());

        registrationsButton.addActionListener(e ->
                new AdminRegistrationsFrame(user).setVisible(true));

        refreshButton.addActionListener(e -> loadCourses());

        logoutButton.addActionListener(e -> {
            new LoginFrame().setVisible(true);
            dispose();
        });

        loadCourses();
    }

    // Load courses, including capacity and actual enrolled count.
    private void loadCourses() {

        try {
            List<Course> courses = courseRepository.findAll();

            tableModel.setRowCount(0);

            for (Course course : courses) {
                tableModel.addRow(new Object[]{
                        course.getCourseId(),
                        course.getCourseCode(),
                        course.getCourseName(),
                        course.getCredits(),
                        course.getCapacity(),
                        course.getEnrolled(),
                        course.isAvailable() ? "Yes" : "No"
                });
            }

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load courses:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Add a course and its prerequisite in one transaction.
    private void addCourse() {

        JTextField codeField = new JTextField();
        JTextField nameField = new JTextField();
        JTextField creditsField = new JTextField();
        JTextField capacityField = new JTextField("60");

        JList<Object> prerequisiteList;

        try {
            prerequisiteList = createPrerequisiteList(0, null);
        } catch (RuntimeException e) {
            showDatabaseError("Could not load prerequisite courses.", e);
            return;
        }

        JScrollPane prerequisiteScroll = new JScrollPane(
                prerequisiteList);
        prerequisiteScroll.setPreferredSize(new Dimension(300, 100));

        Object[] fields = {
                "Course code:", codeField,
                "Course name:", nameField,
                "Credits:", creditsField,
                "Capacity:", capacityField,
                "Prerequisite course:", prerequisiteScroll
        };

        int choice = JOptionPane.showConfirmDialog(
                this, fields, "Add Course",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            int credits = Integer.parseInt(
                    creditsField.getText().trim());
            int capacity = Integer.parseInt(
                    capacityField.getText().trim());

            validateCourse(code, name, credits, capacity);

            Integer prerequisiteId =
                    getSelectedPrerequisiteId(prerequisiteList);

            Course course = new Course(
                    0, code, name, credits,
                    true, capacity, 0);

            try (Connection connection = DBConnection.getConnection()) {
                connection.setAutoCommit(false);

                try {
                    int newCourseId =
                            courseRepository.save(connection, course);

                    prerequisiteRepository.setPrerequisite(
                            connection, newCourseId, prerequisiteId);

                    connection.commit();

                } catch (SQLException | RuntimeException e) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackError) {
                        e.addSuppressed(rollbackError);
                    }
                    throw e;
                }
            }

            JOptionPane.showMessageDialog(
                    this, "Course added successfully.");

            loadCourses();

        } catch (NumberFormatException e) {
            showValidationError(
                    "Credits and capacity must be whole numbers.");

        } catch (IllegalArgumentException e) {
            showValidationError(e.getMessage());

        } catch (SQLException e) {
            showDatabaseError(
                    "Could not add the course and prerequisite. "
                            + "The changes were rolled back.", e);

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not add course. Check whether the course "
                            + "code already exists.\n" + e.getMessage(),
                    "Course Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Edit course details and prerequisite in one transaction.
    private void updateCourse() {

        int selectedRow = courseTable.getSelectedRow();

        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                    this, "Select a course first.");
            return;
        }

        // Convert the selected view row if the table is sorted.
        int row = courseTable.convertRowIndexToModel(selectedRow);

        int courseId = (int) tableModel.getValueAt(row, 0);
        String oldCode = (String) tableModel.getValueAt(row, 1);
        String oldName = (String) tableModel.getValueAt(row, 2);
        int oldCredits = (int) tableModel.getValueAt(row, 3);
        int oldCapacity = (int) tableModel.getValueAt(row, 4);
        int enrolled = (int) tableModel.getValueAt(row, 5);
        boolean oldAvailable =
                "Yes".equals(tableModel.getValueAt(row, 6));

        JTextField codeField = new JTextField(oldCode);
        JTextField nameField = new JTextField(oldName);
        JTextField creditsField =
                new JTextField(String.valueOf(oldCredits));
        JTextField capacityField =
                new JTextField(String.valueOf(oldCapacity));

        JCheckBox availableBox =
                new JCheckBox("Available", oldAvailable);

        JLabel enrolledLabel =
                new JLabel("Currently enrolled: " + enrolled);

        JList<Object> prerequisiteList;

        try {
            Integer currentPrerequisiteId =
                    prerequisiteRepository.findPrerequisiteId(courseId);

            prerequisiteList = createPrerequisiteList(
                    courseId, currentPrerequisiteId);

        } catch (RuntimeException e) {
            showDatabaseError("Could not load the course prerequisite.", e);
            return;
        }

        JScrollPane prerequisiteScroll = new JScrollPane(
                prerequisiteList);
        prerequisiteScroll.setPreferredSize(new Dimension(300, 100));

        Object[] fields = {
                "Course code:", codeField,
                "Course name:", nameField,
                "Credits:", creditsField,
                "Capacity:", capacityField,
                enrolledLabel,
                availableBox,
                "Prerequisite course:", prerequisiteScroll
        };

        int choice = JOptionPane.showConfirmDialog(
                this, fields, "Edit Course",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            int credits = Integer.parseInt(
                    creditsField.getText().trim());
            int capacity = Integer.parseInt(
                    capacityField.getText().trim());

            validateCourse(code, name, credits, capacity);

            if (capacity < enrolled) {
                throw new IllegalArgumentException(
                        "Capacity cannot be less than the "
                                + enrolled + " students already enrolled.");
            }

            Integer prerequisiteId =
                    getSelectedPrerequisiteId(prerequisiteList);

            Course updatedCourse = new Course(
                    courseId,
                    code,
                    name,
                    credits,
                    availableBox.isSelected(),
                    capacity,
                    enrolled);

            try (Connection connection = DBConnection.getConnection()) {
                connection.setAutoCommit(false);

                try {
                    courseRepository.update(connection, updatedCourse);

                    prerequisiteRepository.setPrerequisite(
                            connection, courseId, prerequisiteId);

                    connection.commit();

                } catch (SQLException | RuntimeException e) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackError) {
                        e.addSuppressed(rollbackError);
                    }
                    throw e;
                }
            }

            JOptionPane.showMessageDialog(
                    this, "Course updated successfully.");

            loadCourses();

        } catch (NumberFormatException e) {
            showValidationError(
                    "Credits and capacity must be whole numbers.");

        } catch (IllegalArgumentException e) {
            showValidationError(e.getMessage());

        } catch (SQLException e) {
            showDatabaseError(
                    "Could not update the course and prerequisite. "
                            + "The changes were rolled back.", e);

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not update course. Check whether the "
                            + "course code already exists.\n"
                            + e.getMessage(),
                    "Course Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Build a single-selection prerequisite list.
    // The first option, None, removes any existing prerequisite.
    private JList<Object> createPrerequisiteList(
            int courseId, Integer selectedPrerequisiteId) {

        DefaultListModel<Object> listModel = new DefaultListModel<>();
        listModel.addElement("None");

        int selectedIndex = 0;

        List<PrerequisiteRepository.CourseOption> courses =
                prerequisiteRepository.findOtherCourses(courseId);

        for (PrerequisiteRepository.CourseOption option : courses) {
            listModel.addElement(option);

            if (selectedPrerequisiteId != null
                    && option.getCourseId() == selectedPrerequisiteId) {
                selectedIndex = listModel.size() - 1;
            }
        }

        JList<Object> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(selectedIndex);
        list.setVisibleRowCount(4);

        return list;
    }

    // Convert the selected list item into a prerequisite ID.
    // None returns null.
    private Integer getSelectedPrerequisiteId(JList<Object> list) {

        Object selected = list.getSelectedValue();

        if (selected instanceof PrerequisiteRepository.CourseOption) {
            PrerequisiteRepository.CourseOption option =
                    (PrerequisiteRepository.CourseOption) selected;

            return option.getCourseId();
        }

        return null;
    }

    // Validate course details.
    private void validateCourse(
            String code, String name, int credits, int capacity) {

        if (code.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Course code and name cannot be empty.");
        }

        if (credits <= 0) {
            throw new IllegalArgumentException(
                    "Credits must be greater than zero.");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Capacity must be greater than zero.");
        }
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Invalid Course Details",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showDatabaseError(String message, Exception e) {
        JOptionPane.showMessageDialog(
                this,
                message + "\n" + e.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
    }

    // Delete a course.
    private void deleteCourse() {

        int selectedRow = courseTable.getSelectedRow();

        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                    this, "Select a course first.");
            return;
        }

        int row = courseTable.convertRowIndexToModel(selectedRow);

        int courseId = (int) tableModel.getValueAt(row, 0);
        String courseName = (String) tableModel.getValueAt(row, 2);

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete " + courseName
                        + "?\nIts registrations will also be deleted.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            courseRepository.deleteById(courseId);

            JOptionPane.showMessageDialog(
                    this, "Course deleted successfully.");

            loadCourses();

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not delete course:\n" + e.getMessage(),
                    "Course Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}