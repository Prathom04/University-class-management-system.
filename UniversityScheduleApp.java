import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.security.MessageDigest;

public class UniversityScheduleApp {
    private static final String DB_URL = "jdbc:sqlite:university.db";

    private JFrame frame;
    private int loggedInTeacherId = -1;
    private int loggedInStudentId = -1;

    public static void main(String[] args) {
        createTables();
        SwingUtilities.invokeLater(() -> new UniversityScheduleApp().showWelcomeScreen());
    }

    // Create tables with full schema (do not overwrite existing tables)
    private static void createTables() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            String createTeachers = """
                CREATE TABLE IF NOT EXISTS Teachers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    surname TEXT,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL
                )
                """;
            stmt.execute(createTeachers);

            String createStudents = """
                CREATE TABLE IF NOT EXISTS Students (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    surname TEXT,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    batch TEXT,
                    department TEXT
                )
                """;
            stmt.execute(createStudents);

            String createSchedule = """
                CREATE TABLE IF NOT EXISTS Schedule (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    teacher_id INTEGER,
                    teacher_name TEXT,
                    department TEXT,
                    batch TEXT,
                    course TEXT,
                    room TEXT,
                    time TEXT
                )
                """;
            stmt.execute(createSchedule);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Utility to hash passwords with SHA-256
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- GUI Screens ---

    private void showWelcomeScreen() {
        frame = new JFrame("University Scheduler");
        frame.setSize(400, 320);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(7, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30));

        JLabel titleLabel = new JLabel("Welcome to University Class Scheduler", JLabel.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        JButton teacherLogin = new JButton("Teacher Login");
        JButton studentLogin = new JButton("Student Login");
        JButton registerTeacher = new JButton("Register as Teacher");
        JButton registerStudent = new JButton("Register as Student");

        teacherLogin.addActionListener(e -> showLoginScreen(true));
        studentLogin.addActionListener(e -> showLoginScreen(false));

        registerTeacher.addActionListener(e -> {
            String answer = JOptionPane.showInputDialog(frame, "What is teachers password?");
            if ("UsTc1989@05102004".equals(answer)) {
                showRegisterScreen(true);
            } else {
                JOptionPane.showMessageDialog(frame, "Incorrect answer. Access denied.");
            }
        });

        registerStudent.addActionListener(e -> showRegisterScreen(false));

        panel.add(titleLabel);
        panel.add(teacherLogin);
        panel.add(studentLogin);
        panel.add(registerTeacher);
        panel.add(registerStudent);

        frame.setContentPane(panel);
        frame.setVisible(true);
    }

    private void showRegisterScreen(boolean isTeacher) {
        JFrame regFrame = new JFrame(isTeacher ? "Teacher Registration" : "Student Registration");
        regFrame.setSize(400, isTeacher ? 360 : 420);
        regFrame.setLayout(new GridLayout(isTeacher ? 7 : 9, 2, 8, 8));
        regFrame.setLocationRelativeTo(frame);

        JTextField nameField = new JTextField();
        JTextField surnameField = new JTextField();
        JTextField emailField = new JTextField();
        JTextField batchField = new JTextField();
        JTextField deptField = new JTextField();
        JPasswordField passwordField = new JPasswordField();

        regFrame.add(new JLabel("Name:"));
        regFrame.add(nameField);

        regFrame.add(new JLabel("Surname:"));
        regFrame.add(surnameField);

        regFrame.add(new JLabel("Email:"));
        regFrame.add(emailField);

        regFrame.add(new JLabel("Password:"));
        regFrame.add(passwordField);

        if (!isTeacher) {
            regFrame.add(new JLabel("Batch:"));
            regFrame.add(batchField);

            regFrame.add(new JLabel("Department:"));
            regFrame.add(deptField);
        }

        regFrame.add(new JLabel(""));  // Empty label for spacing

        JButton submitBtn = new JButton("Register");
        regFrame.add(submitBtn);

        submitBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String surname = surnameField.getText().trim();
            String email = emailField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(regFrame, "Please fill in all required fields.");
                return;
            }

            if (isTeacher && !email.endsWith("ustc.ac.bd")) {
                JOptionPane.showMessageDialog(regFrame, "Teacher email must end with 'ustc.ac.bd'");
                return;
            }

            if (!isTeacher) {
                if (batchField.getText().trim().isEmpty() || deptField.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(regFrame, "Please enter batch and department.");
                    return;
                }
            }

            String hashedPass = hashPassword(password);

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                String sql;
                PreparedStatement stmt;

                if (isTeacher) {
                    sql = "INSERT INTO Teachers (name, surname, email, password) VALUES (?, ?, ?, ?)";
                    stmt = conn.prepareStatement(sql);
                    stmt.setString(1, name);
                    stmt.setString(2, surname);
                    stmt.setString(3, email);
                    stmt.setString(4, hashedPass);
                } else {
                    sql = "INSERT INTO Students (name, surname, email, password, batch, department) VALUES (?, ?, ?, ?, ?, ?)";
                    stmt = conn.prepareStatement(sql);
                    stmt.setString(1, name);
                    stmt.setString(2, surname);
                    stmt.setString(3, email);
                    stmt.setString(4, hashedPass);
                    stmt.setString(5, batchField.getText().trim());
                    stmt.setString(6, deptField.getText().trim());
                }

                stmt.executeUpdate();
                JOptionPane.showMessageDialog(regFrame, "Registration successful!");
                regFrame.dispose();

            } catch (SQLException ex) {
                if (ex.getMessage().contains("UNIQUE")) {
                    JOptionPane.showMessageDialog(regFrame, "Email already in use.");
                } else {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(regFrame, "Registration failed due to database error.");
                }
            }
        });

        regFrame.setVisible(true);
    }

    private void showLoginScreen(boolean isTeacher) {
        JFrame loginFrame = new JFrame(isTeacher ? "Teacher Login" : "Student Login");
        loginFrame.setSize(350, 220);
        loginFrame.setLayout(new GridLayout(5, 1, 8, 8));
        loginFrame.setLocationRelativeTo(frame);

        JTextField emailField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton loginBtn = new JButton("Login");

        loginFrame.add(new JLabel("Enter Email:"));
        loginFrame.add(emailField);
        loginFrame.add(new JLabel("Enter Password:"));
        loginFrame.add(passwordField);
        loginFrame.add(loginBtn);

        loginBtn.addActionListener(e -> {
            String email = emailField.getText().trim();
            String inputPass = new String(passwordField.getPassword());
            String hashedInput = hashPassword(inputPass);

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                String sql = isTeacher
                        ? "SELECT id, password FROM Teachers WHERE email = ?"
                        : "SELECT id, password FROM Students WHERE email = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, email);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    if (storedHash.equals(hashedInput)) {
                        int userId = rs.getInt("id");
                        loginFrame.dispose();

                        if (isTeacher) {
                            loggedInTeacherId = userId;
                            showTeacherDashboard();
                        } else{
loggedInStudentId = userId;
showStudentDashboard();
}
} else {
JOptionPane.showMessageDialog(loginFrame, "Incorrect password.");
}
} else {
JOptionPane.showMessageDialog(loginFrame, "Email not found.");
}        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(loginFrame, "Login failed due to database error.");
        }
    });

    loginFrame.setVisible(true);
}

// Teacher Dashboard with Assign, Cancel, View, Return buttons
private void showTeacherDashboard() {
    JFrame dashFrame = new JFrame("Teacher Dashboard");
    dashFrame.setSize(500, 400);
    dashFrame.setLayout(new GridLayout(6, 1, 10, 10));
    dashFrame.setLocationRelativeTo(frame);

    JButton assignBtn = new JButton("Assign Class");
    JButton cancelBtn = new JButton("Cancel Class");
    JButton viewBtn = new JButton("View All Classes");
    JButton returnBtn = new JButton("Logout");

    dashFrame.add(new JLabel("Teacher Dashboard", JLabel.CENTER));
    dashFrame.add(assignBtn);
    dashFrame.add(cancelBtn);
    dashFrame.add(viewBtn);
    dashFrame.add(new JLabel());  // spacer
    dashFrame.add(returnBtn);

    assignBtn.addActionListener(e -> showAssignClassScreen(dashFrame));
    cancelBtn.addActionListener(e -> showCancelClassScreen(dashFrame));
    viewBtn.addActionListener(e -> showAllClassesScreen(dashFrame));
    returnBtn.addActionListener(e -> {
        loggedInTeacherId = -1;
        dashFrame.dispose();
        showWelcomeScreen();
    });

    dashFrame.setVisible(true);
}

private void showAssignClassScreen(JFrame parentFrame) {
    JDialog assignDialog = new JDialog(parentFrame, "Assign Class", true);
    assignDialog.setSize(400, 380);
    assignDialog.setLayout(new GridLayout(7, 2, 8, 8));
    assignDialog.setLocationRelativeTo(parentFrame);

    JTextField teacherNameField = new JTextField();
    JTextField departmentField = new JTextField();
    JTextField batchField = new JTextField();
    JTextField courseField = new JTextField();
    JTextField roomField = new JTextField();
    JTextField timeField = new JTextField();

    assignDialog.add(new JLabel("Teacher Name:"));
    assignDialog.add(teacherNameField);
    assignDialog.add(new JLabel("Department:"));
    assignDialog.add(departmentField);
    assignDialog.add(new JLabel("Batch:"));
    assignDialog.add(batchField);
    assignDialog.add(new JLabel("Course:"));
    assignDialog.add(courseField);
    assignDialog.add(new JLabel("Room:"));
    assignDialog.add(roomField);
    assignDialog.add(new JLabel("Time:"));
    assignDialog.add(timeField);

    JButton assignBtn = new JButton("Assign");
    assignDialog.add(new JLabel());
    assignDialog.add(assignBtn);

    assignBtn.addActionListener(e -> {
        String teacherName = teacherNameField.getText().trim();
        String department = departmentField.getText().trim();
        String batch = batchField.getText().trim();
        String course = courseField.getText().trim();
        String room = roomField.getText().trim();
        String time = timeField.getText().trim();

        if (teacherName.isEmpty() || department.isEmpty() || batch.isEmpty() ||
            course.isEmpty() || room.isEmpty() || time.isEmpty()) {
            JOptionPane.showMessageDialog(assignDialog, "Please fill in all fields.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                INSERT INTO Schedule (teacher_id, teacher_name, department, batch, course, room, time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, loggedInTeacherId);
            stmt.setString(2, teacherName);
            stmt.setString(3, department);
            stmt.setString(4, batch);
            stmt.setString(5, course);
            stmt.setString(6, room);
            stmt.setString(7, time);

            stmt.executeUpdate();
            JOptionPane.showMessageDialog(assignDialog, "Class assigned successfully.");
            assignDialog.dispose();

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(assignDialog, "Failed to assign class.");
        }
    });

    assignDialog.setVisible(true);
}

private void showCancelClassScreen(JFrame parentFrame) {
    JDialog cancelDialog = new JDialog(parentFrame, "Cancel Class", true);
    cancelDialog.setSize(350, 180);
    cancelDialog.setLayout(new GridLayout(3, 2, 8, 8));
    cancelDialog.setLocationRelativeTo(parentFrame);

    JTextField classIdField = new JTextField();

    cancelDialog.add(new JLabel("Enter Class ID to Cancel:"));
    cancelDialog.add(classIdField);

    JButton cancelBtn = new JButton("Cancel Class");
    cancelDialog.add(new JLabel());
    cancelDialog.add(cancelBtn);

    cancelBtn.addActionListener(e -> {
        String idText = classIdField.getText().trim();
        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(cancelDialog, "Please enter Class ID.");
            return;
        }

        try {
            int classId = Integer.parseInt(idText);

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                String sqlCheck = "SELECT teacher_id FROM Schedule WHERE id = ?";
                PreparedStatement stmtCheck = conn.prepareStatement(sqlCheck);
                stmtCheck.setInt(1, classId);
                ResultSet rs = stmtCheck.executeQuery();

                if (rs.next()) {
                    int tid = rs.getInt("teacher_id");
                    if (tid != loggedInTeacherId) {
                        JOptionPane.showMessageDialog(cancelDialog, "You can only cancel your own classes.");
                        return;
                    }

                    String sqlDelete = "DELETE FROM Schedule WHERE id = ?";
                    PreparedStatement stmtDelete = conn.prepareStatement(sqlDelete);
                    stmtDelete.setInt(1, classId);
                    int affected = stmtDelete.executeUpdate();

                    if (affected > 0) {
                        JOptionPane.showMessageDialog(cancelDialog, "Class canceled successfully.");
                        cancelDialog.dispose();
                    } else {
                        JOptionPane.showMessageDialog(cancelDialog, "Class ID not found.");
                    }
                } else {
                    JOptionPane.showMessageDialog(cancelDialog, "Class ID not found.");
                }
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(cancelDialog, "Invalid Class ID.");
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(cancelDialog, "Failed to cancel class.");
        }
    });

    cancelDialog.setVisible(true);
}

private void showAllClassesScreen(JFrame parentFrame) {
    JDialog allClassDialog = new JDialog(parentFrame, "All Scheduled Classes", true);
    allClassDialog.setSize(700, 400);
    allClassDialog.setLocationRelativeTo(parentFrame);

    String[] columns = {"ID", "Teacher ID", "Teacher Name", "Department", "Batch", "Course", "Room", "Time"};
    DefaultListModel<String> listModel = new DefaultListModel<>();

    JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    JScrollPane scrollPane = new JScrollPane(textArea);

    try (Connection conn = DriverManager.getConnection(DB_URL)) {
        String sql = "SELECT * FROM Schedule ORDER BY id";
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-10s %-15s %-15s %-10s %-15s %-8s %-10s%n",
                "ID", "TID", "Teacher Name", "Department", "Batch", "Course", "Room", "Time"));
        sb.append("---------------------------------------------------------------------------------------------\n");

        while (rs.next()) {
            sb.append(String.format("%-5d %-10d %-15s %-15s %-10s %-15s %-8s %-10s%n",
                    rs.getInt("id"),
                    rs.getInt("teacher_id"),
                    rs.getString("teacher_name"),
                    rs.getString("department"),
                    rs.getString("batch"),
                    rs.getString("course"),
                    rs.getString("room"),
                    rs.getString("time")));
        }

        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setText(sb.toString());

    } catch (SQLException ex) {
        ex.printStackTrace();
        textArea.setText("Failed to load classes.");
    }

    allClassDialog.add(scrollPane);
    allClassDialog.setVisible(true);
}

// Simple Student Dashboard placeholder (extend as needed)
private void showStudentDashboard() {
    JFrame studentFrame = new JFrame("Student Dashboard");
    studentFrame.setSize(400, 200);
    studentFrame.setLocationRelativeTo(frame);
    studentFrame.setLayout(new BorderLayout());

    JLabel welcomeLabel = new JLabel("Welcome Student!", JLabel.CENTER);
    welcomeLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
    studentFrame.add(welcomeLabel, BorderLayout.CENTER);

    JButton logoutBtn = new JButton("Logout");
    logoutBtn.addActionListener(e -> {
        loggedInStudentId = -1;
        studentFrame.dispose();
        showWelcomeScreen();
    });
    studentFrame.add(logoutBtn, BorderLayout.SOUTH);

    studentFrame.setVisible(true);
}
}