package ui;

import model.User;
import repository.RegistrationRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class AdminRegistrationsFrame extends JFrame {

    private final RegistrationRepository repository =
            new RegistrationRepository();

    // IDs are retained in the model for certification,
    // but hidden from the displayed table.
    private final DefaultTableModel tableModel =
            new DefaultTableModel(
                    new String[]{
                            "Registration ID",
                            "Student ID",
                            "Student Name",
                            "Username",
                            "Course ID",
                            "Course Code",
                            "Course Name",
                            "Registration Date",
                            "Completion Status"
                    }, 0) {

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    private final JTable table = new JTable(tableModel);

    private final JButton certifyButton =
            new JButton("Certify Completion");

    public AdminRegistrationsFrame(User admin) {

        setTitle("Student Registrations");
        setSize(1050, 500);
        setMinimumSize(new Dimension(850, 400));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JLabel title = new JLabel(
                "Student Registrations - " + admin.getFullName());
        title.setFont(new Font("Arial", Font.BOLD, 20));

        JButton refreshButton = new JButton("Refresh");
        JButton closeButton = new JButton("Close");

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(
                12, 12, 12, 12));
        topPanel.add(title, BorderLayout.CENTER);
        topPanel.add(refreshButton, BorderLayout.EAST);

        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);

        // Hide the three internal ID columns.
        table.removeColumn(table.getColumnModel().getColumn(4));
        table.removeColumn(table.getColumnModel().getColumn(1));
        table.removeColumn(table.getColumnModel().getColumn(0));

        JPanel bottomPanel =
                new JPanel(new FlowLayout(FlowLayout.RIGHT));

        certifyButton.setEnabled(false);

        bottomPanel.add(certifyButton);
        bottomPanel.add(closeButton);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> loadRegistrations());
        closeButton.addActionListener(e -> dispose());
        certifyButton.addActionListener(e -> certifyCompletion());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateCertifyButton();
            }
        });

        loadRegistrations();
    }

    private void loadRegistrations() {

        try {
            List<Object[]> registrations =
                    repository.findAllRegistrationDetails();

            tableModel.setRowCount(0);

            for (Object[] registration : registrations) {
                tableModel.addRow(registration);
            }

            updateCertifyButton();

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load student registrations:\n"
                            + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Enable certification only when a registration is selected
    // and its completion status is Not Completed.
    private void updateCertifyButton() {

        int selectedRow = table.getSelectedRow();

        if (selectedRow < 0) {
            certifyButton.setEnabled(false);
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);

        String status = String.valueOf(
                tableModel.getValueAt(modelRow, 8));

        certifyButton.setEnabled(
                "Not Completed".equals(status));
    }

    // Certify completion after confirmation.
    private void certifyCompletion() {

        int selectedRow = table.getSelectedRow();

        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                    this, "Select a registration first.");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);

        int registrationId =
                (int) tableModel.getValueAt(modelRow, 0);

        int studentId =
                (int) tableModel.getValueAt(modelRow, 1);

        String studentName =
                String.valueOf(tableModel.getValueAt(modelRow, 2));

        int courseId =
                (int) tableModel.getValueAt(modelRow, 4);

        String courseName =
                String.valueOf(tableModel.getValueAt(modelRow, 6));

        String status =
                String.valueOf(tableModel.getValueAt(modelRow, 8));

        if ("Completed".equals(status)) {
            JOptionPane.showMessageDialog(
                    this,
                    "This registration is already completed.");
            updateCertifyButton();
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Certify completion for this registration?\n\n"
                        + "Student: " + studentName + "\n"
                        + "Course: " + courseName,
                "Confirm Completion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            repository.certifyCompletion(studentId, courseId);

            JOptionPane.showMessageDialog(
                    this,
                    "Completion certified successfully for "
                            + studentName + " in " + courseName + ".");

            loadRegistrations();

        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    "Cannot Certify Completion",
                    JOptionPane.WARNING_MESSAGE);

            loadRegistrations();

        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not certify completion:\n"
                            + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}