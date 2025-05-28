import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.Vector;
import java.time.LocalDate; // For handling dates
import java.time.LocalTime; // For handling times
import java.time.LocalDateTime; // For combining date and time
import java.time.format.DateTimeParseException; // For parsing errors
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit; // For scheduling

public class UniversityScheduleApp {
    private static final String DB_URL = "jdbc:sqlite:university.db";

    private JFrame frame;
    private int loggedInTeacherId = -1;
    private int loggedInStudentId = -1;
    private String loggedInTeacherName = ""; // To store the logged-in teacher's full name

    // For scheduled class cleanup
    private ScheduledExecutorService scheduler;

    public static void main(String[] args) {
        // --- IMPORTANT: Ensure SQLite JDBC driver is loaded ---
        // This line makes sure the necessary driver is available.
        // If you get a ClassNotFoundException here, you need to add the SQLite JDBC JAR
        // to your project's classpath.
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite JDBC driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null, "Error: SQLite JDBC driver not found.\nPlease add 'sqlite-jdbc-(version).jar' to your project's classpath.", "Driver Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1); // Exit if driver is not found
        }

        // Create database tables if they don't exist
        createTables();

        // Start the Swing GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            UniversityScheduleApp app = new UniversityScheduleApp();
            app.showWelcomeScreen();
            app.startAutoClassCleanup(); // Start the scheduled task
        });
    }

    /**
     * Creates necessary database tables (Teachers, Students, Schedule) if they don't already exist.
     * This method ensures the database schema is set up correctly on application startup.
     */
    private static void createTables() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // SQL to create the Teachers table
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
            System.out.println("Teachers table checked/created.");

            // SQL to create the Students table
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
            System.out.println("Students table checked/created.");

            // SQL to create the Schedule table with new columns for date and end time
            // IMPORTANT: If you have an existing 'university.db', you might need to
            // manually add these columns using ALTER TABLE or recreate the database.
            // For example: ALTER TABLE Schedule ADD COLUMN class_date TEXT;
            // ALTER TABLE Schedule ADD COLUMN class_end_time TEXT;
            String createSchedule = """
                CREATE TABLE IF NOT EXISTS Schedule (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    teacher_id INTEGER,
                    teacher_name TEXT,
                    department TEXT,
                    batch TEXT,
                    course TEXT,
                    room TEXT,
                    time TEXT,
                    class_date TEXT,      -- New: YYYY-MM-DD format
                    class_end_time TEXT   -- New: HH:MM format
                )
                """;
            stmt.execute(createSchedule);
            System.out.println("Schedule table checked/created.");

            System.out.println("All database tables are ready.");
        } catch (SQLException e) {
            System.err.println("CRITICAL ERROR: Failed to create database tables. Please check database file permissions or integrity.");
            e.printStackTrace();
            // Display a message and exit if tables cannot be created, as the app won't function
            JOptionPane.showMessageDialog(null, "Critical Database Error: Cannot set up tables. Application will exit.", "Database Setup Failed", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    /**
     * Hashes a given password using SHA-256 for secure storage in the database.
     * This prevents storing passwords in plain text.
     * @param password The plain text password string to hash.
     * @return The SHA-256 hashed password as a hexadecimal string.
     * @throws RuntimeException if the SHA-256 algorithm is not available or UTF-8 encoding fails.
     */
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
            System.err.println("Error hashing password: " + e.getMessage());
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    // --- GUI Screens ---

    /**
     * Displays the initial welcome screen of the application, offering options for
     * teacher/student login and registration.
     */
    private void showWelcomeScreen() {
        frame = new JFrame("University Schedule App");
        frame.setSize(400, 320);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null); // Center the frame on the screen

        JPanel panel = new JPanel(new GridLayout(7, 1, 10, 10)); // Grid layout for buttons
        panel.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30)); // Padding around the panel

        JLabel titleLabel = new JLabel("Welcome to University Class Scheduler", JLabel.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        JButton teacherLogin = new JButton("Teacher Login");
        JButton studentLogin = new JButton("Student Login");
        JButton registerTeacher = new JButton("Register as Teacher");
        JButton registerStudent = new JButton("Register as Student");

        // Add action listeners for each button
        teacherLogin.addActionListener(e -> showLoginScreen(true));
        studentLogin.addActionListener(e -> showLoginScreen(false));

        registerTeacher.addActionListener(e -> {
            // Secret password check for teacher registration for security
            String answer = JOptionPane.showInputDialog(frame, "What is the teachers password?");
            if ("UsTc1989@05102004".equals(answer)) { // Hardcoded secret password
                showRegisterScreen(true);
            } else {
                JOptionPane.showMessageDialog(frame, "Incorrect password. Access denied.");
            }
        });

        registerStudent.addActionListener(e -> showRegisterScreen(false));

        // Add all components to the main panel
        panel.add(titleLabel);
        panel.add(teacherLogin);
        panel.add(studentLogin);
        panel.add(registerTeacher);
        panel.add(registerStudent);

        frame.setContentPane(panel);
        frame.setVisible(true);
    }

    /**
     * Displays the registration screen for either a teacher or a student.
     * Collects user details and attempts to register them in the database.
     * @param isTeacher True if registering a teacher, false for a student.
     */
    private void showRegisterScreen(boolean isTeacher) {
        JFrame regFrame = new JFrame(isTeacher ? "Teacher Registration" : "Student Registration");
        regFrame.setSize(400, 360); // Adjust frame size based on user type
        regFrame.setLayout(new GridLayout(isTeacher ? 7 : 9, 2, 8, 8)); // Grid layout for input fields
        regFrame.setLocationRelativeTo(frame);

        // Input fields for registration
        JTextField nameField = new JTextField();
        JTextField surnameField = new JTextField();
        JTextField emailField = new JTextField();
        JTextField batchField = new JTextField();
        JTextField deptField = new JTextField();
        JPasswordField passwordField = new JPasswordField();

        // Add common fields
        regFrame.add(new JLabel("Name:"));
        regFrame.add(nameField);
        regFrame.add(new JLabel("Surname:"));
        regFrame.add(surnameField);
        regFrame.add(new JLabel("Email:"));
        regFrame.add(emailField);
        regFrame.add(new JLabel("Password:"));
        regFrame.add(passwordField);

        // Add student-specific fields
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

            // Basic input validation: ensure all required fields are filled
            if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(regFrame, "Please fill in all required fields.", "Input Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Teacher-specific email domain validation
            if (isTeacher && !email.endsWith("ustc.ac.bd")) {
                JOptionPane.showMessageDialog(regFrame, "Teacher email must end with 'ustc.ac.bd'", "Email Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Student-specific field validation
            if (!isTeacher) {
                if (batchField.getText().trim().isEmpty() || deptField.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(regFrame, "Please enter batch and department.", "Input Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            String hashedPass = hashPassword(password); // Hash the password before storing

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

                stmt.executeUpdate(); // Execute the insert operation
                JOptionPane.showMessageDialog(regFrame, "Registration successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
                regFrame.dispose(); // Close registration frame on success

            } catch (SQLException ex) {
                // Handle specific SQL errors
                if (ex.getMessage().contains("UNIQUE constraint failed")) {
                    JOptionPane.showMessageDialog(regFrame, "Email already in use. Please use a different email.", "Registration Error", JOptionPane.WARNING_MESSAGE);
                } else {
                    System.err.println("Registration SQL error: " + ex.getMessage());
                    ex.printStackTrace(); // Print full stack trace to console for debugging
                    JOptionPane.showMessageDialog(regFrame, "Registration failed due to a database error. Check console for details.", "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        regFrame.setVisible(true);
    }

    /**
     * Displays the login screen for either a teacher or a student.
     * Authenticates user credentials against the database.
     * @param isTeacher True if logging in as a teacher, false for a student.
     */
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
            String hashedInput = hashPassword(inputPass); // Hash input password for comparison

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                String sql = isTeacher
                        ? "SELECT id, name, surname, password FROM Teachers WHERE email = ?" // Get name and surname for teacher
                        : "SELECT id, password FROM Students WHERE email = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, email);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) { // If a record is found for the email
                    String storedHash = rs.getString("password");
                    if (storedHash.equals(hashedInput)) { // Compare hashed passwords
                        int userId = rs.getInt("id");
                        loginFrame.dispose(); // Close login frame on successful login

                        if (isTeacher) {
                            loggedInTeacherId = userId;
                            // Store the teacher's full name for display in dashboard
                            loggedInTeacherName = rs.getString("name") + " " + rs.getString("surname");
                            showTeacherDashboard();
                        } else {
                            loggedInStudentId = userId;
                            showStudentDashboard();
                        }
                    } else {
                        JOptionPane.showMessageDialog(loginFrame, "Incorrect password.", "Login Failed", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(loginFrame, "Email not found.", "Login Failed", JOptionPane.WARNING_MESSAGE);
                }
            } catch (SQLException ex) {
                System.err.println("Login SQL error: " + ex.getMessage());
                ex.printStackTrace(); // Print full stack trace to console for debugging
                JOptionPane.showMessageDialog(loginFrame, "Login failed due to a database error. Check console for details.", "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        loginFrame.setVisible(true);
    }

    /**
     * Displays the teacher's main dashboard with options to manage classes.
     * Includes buttons for assigning, canceling, viewing, and editing classes.
     */
    private void showTeacherDashboard() {
        JFrame dashFrame = new JFrame("Teacher Dashboard");
        dashFrame.setSize(500, 400);
        dashFrame.setLayout(new GridLayout(8, 1, 10, 10)); // Increased rows for new button
        dashFrame.setLocationRelativeTo(frame);

        JLabel welcomeLabel = new JLabel("Welcome, " + loggedInTeacherName + "!", JLabel.CENTER);
        welcomeLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        JButton assignBtn = new JButton("Assign Class");
        JButton cancelBtn = new JButton("Cancel Class");
        JButton viewMyClassesBtn = new JButton("View My Assigned Classes"); // New button for teacher's own classes
        JButton viewAllBtn = new JButton("View All Classes");
        JButton editClassBtn = new JButton("Edit Class"); // Added Edit Class button
        JButton returnBtn = new JButton("Logout");

        dashFrame.add(welcomeLabel);
        dashFrame.add(assignBtn);
        dashFrame.add(cancelBtn);
        dashFrame.add(viewMyClassesBtn);
        dashFrame.add(viewAllBtn);
        dashFrame.add(editClassBtn); // Add Edit Class button to layout
        dashFrame.add(new JLabel());  // Spacer
        dashFrame.add(returnBtn);

        // Add action listeners for dashboard buttons
        assignBtn.addActionListener(e -> showAssignClassScreen(dashFrame));
        cancelBtn.addActionListener(e -> showCancelClassScreen(dashFrame));
        viewMyClassesBtn.addActionListener(e -> showMyClassesScreen(dashFrame)); // Action for new button
        viewAllBtn.addActionListener(e -> showAllClassesScreen(dashFrame));
        editClassBtn.addActionListener(e -> showEditClassScreen(dashFrame)); // Action for Edit Class button
        returnBtn.addActionListener(e -> {
            loggedInTeacherId = -1;
            loggedInTeacherName = ""; // Clear name on logout
            dashFrame.dispose();
            showWelcomeScreen();
        });

        dashFrame.setVisible(true);
    }

    /**
     * Displays a dialog for a teacher to assign a new class.
     * The auto-generated class ID is displayed upon successful assignment.
     * Teachers now input date and end time.
     * @param parentFrame The parent JFrame for this dialog.
     */
    private void showAssignClassScreen(JFrame parentFrame) {
        JDialog assignDialog = new JDialog(parentFrame, "Assign Class", true); // Modal dialog
        assignDialog.setSize(400, 450); // Increased height for new fields
        assignDialog.setLayout(new GridLayout(9, 2, 8, 8)); // Increased rows for new fields
        assignDialog.setLocationRelativeTo(parentFrame);

        // Teacher Name is pre-filled based on login and made uneditable
        JTextField teacherNameField = new JTextField(loggedInTeacherName);
        teacherNameField.setEditable(false);
        
        JTextField departmentField = new JTextField();
        JTextField batchField = new JTextField();
        JTextField courseField = new JTextField();
        JTextField roomField = new JTextField();
        JTextField timeField = new JTextField(); // Start time
        JTextField dateField = new JTextField(); // New: Date field (YYYY-MM-DD)
        JTextField endTimeField = new JTextField(); // New: End Time field (HH:MM)


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
        assignDialog.add(new JLabel("Start Time (HH:MM):"));
        assignDialog.add(timeField);
        assignDialog.add(new JLabel("Date (YYYY-MM-DD):")); // New label
        assignDialog.add(dateField); // New field
        assignDialog.add(new JLabel("End Time (HH:MM):")); // New label
        assignDialog.add(endTimeField); // New field


        JButton assignBtn = new JButton("Assign");
        assignDialog.add(new JLabel()); // Spacer
        assignDialog.add(assignBtn);

        assignBtn.addActionListener(e -> {
            String teacherName = teacherNameField.getText().trim();
            String department = departmentField.getText().trim();
            String batch = batchField.getText().trim();
            String course = courseField.getText().trim();
            String room = roomField.getText().trim();
            String startTime = timeField.getText().trim(); // This is now start time
            String classDate = dateField.getText().trim(); // New
            String classEndTime = endTimeField.getText().trim(); // New

            // Validate all fields are filled before attempting database insertion
            if (department.isEmpty() || batch.isEmpty() ||
                course.isEmpty() || room.isEmpty() || startTime.isEmpty() ||
                classDate.isEmpty() || classEndTime.isEmpty()) { // Validate new fields
                JOptionPane.showMessageDialog(assignDialog, "Please fill in all fields.", "Input Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Validate date and time formats
            try {
                LocalDate.parse(classDate); // Check YYYY-MM-DD format
                LocalTime.parse(startTime);  // Check HH:MM format
                LocalTime.parse(classEndTime); // Check HH:MM format
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(assignDialog, "Invalid date or time format. Use YYYY-MM-DD for date and HH:MM for time.", "Format Error", JOptionPane.WARNING_MESSAGE);
                return;
            }


            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                // SQL INSERT statement for the Schedule table, including new columns
                String sql = """
                    INSERT INTO Schedule (teacher_id, teacher_name, department, batch, course, room, time, class_date, class_end_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                // Use Statement.RETURN_GENERATED_KEYS to retrieve the auto-incremented 'id'
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, loggedInTeacherId);
                stmt.setString(2, teacherName);
                stmt.setString(3, department);
                stmt.setString(4, batch);
                stmt.setString(5, course);
                stmt.setString(6, room);
                stmt.setString(7, startTime); // Store start time
                stmt.setString(8, classDate); // Store date
                stmt.setString(9, classEndTime); // Store end time

                stmt.executeUpdate(); // Execute the insert operation

                // Retrieve the auto-generated class ID
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int classId = rs.getInt(1); // Get the first (and only) generated key
                    JOptionPane.showMessageDialog(assignDialog,
                        "Class assigned successfully!\nClass ID: " + classId +
                        "\nRemember this ID to cancel/edit the class later.", "Assignment Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(assignDialog, "Class assigned, but couldn't retrieve ID. Check database logs.", "Assignment Warning", JOptionPane.WARNING_MESSAGE);
                }

                assignDialog.dispose(); // Close the dialog after successful assignment

            } catch (SQLException ex) {
                // Log the specific SQL error to console for debugging
                System.err.println("Assign Class SQL error: " + ex.getMessage());
                ex.printStackTrace(); // Print full stack trace
                JOptionPane.showMessageDialog(assignDialog, "Failed to assign class due to a database error. Please check the console for details.", "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        assignDialog.setVisible(true);
    }

    /**
     * Displays a dialog for a teacher to cancel an existing class by its ID.
     * Ensures that only classes assigned by the currently logged-in teacher can be canceled.
     * @param parentFrame The parent JFrame for this dialog.
     */
    private void showCancelClassScreen(JFrame parentFrame) {
        JDialog cancelDialog = new JDialog(parentFrame, "Cancel Class", true); // Modal dialog
        cancelDialog.setSize(350, 180);
        cancelDialog.setLayout(new GridLayout(3, 2, 8, 8));
        cancelDialog.setLocationRelativeTo(parentFrame);

        JTextField classIdField = new JTextField();

        cancelDialog.add(new JLabel("Enter Class ID to Cancel:"));
        cancelDialog.add(classIdField);

        JButton cancelBtn = new JButton("Cancel Class");
        cancelDialog.add(new JLabel()); // Spacer
        cancelDialog.add(cancelBtn);

        cancelBtn.addActionListener(e -> {
            String idText = classIdField.getText().trim();
            if (idText.isEmpty()) {
                JOptionPane.showMessageDialog(cancelDialog, "Please enter a Class ID.", "Input Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                int classId = Integer.parseInt(idText); // Convert input ID to integer

                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    // First, verify that the class exists and belongs to the logged-in teacher
                    String sqlCheck = "SELECT teacher_id FROM Schedule WHERE id = ?";
                    PreparedStatement stmtCheck = conn.prepareStatement(sqlCheck);
                    stmtCheck.setInt(1, classId);
                    ResultSet rs = stmtCheck.executeQuery();

                    if (rs.next()) { // If class ID is found
                        int tid = rs.getInt("teacher_id");
                        if (tid != loggedInTeacherId) { // Check ownership
                            JOptionPane.showMessageDialog(cancelDialog, "You can only cancel your own classes.", "Permission Denied", JOptionPane.WARNING_MESSAGE);
                            return; // Prevent unauthorized cancellation
                        }

                        // If owned by the teacher, proceed with deletion
                        String sqlDelete = "DELETE FROM Schedule WHERE id = ?";
                        PreparedStatement stmtDelete = conn.prepareStatement(sqlDelete);
                        stmtDelete.setInt(1, classId);
                        int affected = stmtDelete.executeUpdate(); // Execute deletion

                        if (affected > 0) {
                            JOptionPane.showMessageDialog(cancelDialog, "Class canceled successfully.", "Cancellation Success", JOptionPane.INFORMATION_MESSAGE);
                            cancelDialog.dispose(); // Close dialog on success
                        } else {
                            // This case is unlikely if rs.next() was true, but good for robustness
                            JOptionPane.showMessageDialog(cancelDialog, "Class ID not found or no changes were made.", "Cancellation Failed", JOptionPane.WARNING_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(cancelDialog, "Class ID not found.", "Cancellation Failed", JOptionPane.WARNING_MESSAGE);
                    }
                }

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(cancelDialog, "Invalid Class ID. Please enter a valid number.", "Input Error", JOptionPane.WARNING_MESSAGE);
            } catch (SQLException ex) {
                System.err.println("Cancel Class SQL error: " + ex.getMessage());
                ex.printStackTrace(); // Print full stack trace
                JOptionPane.showMessageDialog(cancelDialog, "Failed to cancel class due to a database error. Check console for details.", "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelDialog.setVisible(true);
    }

    /**
     * Displays a dialog showing only the classes assigned by the currently logged-in teacher.
     * This helps teachers find the IDs of their classes for cancellation or editing.
     */
    private void showMyClassesScreen(JFrame parentFrame) {
        JDialog myClassesDialog = new JDialog(parentFrame, "My Assigned Classes", true); // Modal dialog
        myClassesDialog.setSize(900, 400); // Adjusted width for new columns
        myClassesDialog.setLocationRelativeTo(parentFrame);

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea); // Add scroll capability for long lists

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Select classes only for the logged-in teacher, ordered by ID
            String sql = "SELECT id, teacher_name, department, batch, course, room, time, class_date, class_end_time FROM Schedule WHERE teacher_id = ? ORDER BY id";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, loggedInTeacherId); // Filter results by the current teacher's ID
            ResultSet rs = stmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            // Format header for clear readability, including new date/time columns
            sb.append(String.format("%-5s %-15s %-15s %-10s %-15s %-8s %-10s %-12s %-12s%n",
                    "ID", "Teacher Name", "Department", "Batch", "Course", "Room", "Start Time", "Date", "End Time"));
            sb.append("---------------------------------------------------------------------------------------------------------------------------\n");

            boolean foundClasses = false;
            while (rs.next()) {
                foundClasses = true;
                sb.append(String.format("%-5d %-15s %-15s %-10s %-15s %-8s %-10s %-12s %-12s%n",
                        rs.getInt("id"),
                        rs.getString("teacher_name"),
                        rs.getString("department"),
                        rs.getString("batch"),
                        rs.getString("course"),
                        rs.getString("room"),
                        rs.getString("time"), // This is now start time
                        rs.getString("class_date"),
                        rs.getString("class_end_time")));
            }

            if (!foundClasses) {
                textArea.setText("You have not assigned any classes yet.");
            } else {
                textArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Use monospaced font for column alignment
                textArea.setText(sb.toString());
            }

        } catch (SQLException ex) {
            System.err.println("Show My Classes SQL error: " + ex.getMessage());
            ex.printStackTrace(); // Print full stack trace
            textArea.setText("Failed to load your classes due to a database error. Check console for details.");
        }

        myClassesDialog.add(scrollPane);
        myClassesDialog.setVisible(true);
    }

    /**
     * Displays a dialog showing all scheduled classes across all teachers in the system.
     */
    private void showAllClassesScreen(JFrame parentFrame) {
        JDialog allClassDialog = new JDialog(parentFrame, "All Scheduled Classes", true); // Modal dialog
        allClassDialog.setSize(950, 400); // Adjusted width for new columns
        allClassDialog.setLocationRelativeTo(parentFrame);

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM Schedule ORDER BY id";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            // Format header for readability, including Teacher ID (TID) and new date/time columns
            sb.append(String.format("%-5s %-10s %-15s %-15s %-10s %-15s %-8s %-10s %-12s %-12s%n",
                    "ID", "TID", "Teacher Name", "Department", "Batch", "Course", "Room", "Start Time", "Date", "End Time"));
            sb.append("-------------------------------------------------------------------------------------------------------------------------------\n");

            boolean foundClasses = false;
            while (rs.next()) {
                foundClasses = true;
                sb.append(String.format("%-5d %-10d %-15s %-15s %-10s %-15s %-8s %-10s %-12s %-12s%n",
                        rs.getInt("id"),
                        rs.getInt("teacher_id"),
                        rs.getString("teacher_name"),
                        rs.getString("department"),
                        rs.getString("batch"),
                        rs.getString("course"),
                        rs.getString("room"),
                        rs.getString("time"), // This is now start time
                        rs.getString("class_date"),
                        rs.getString("class_end_time")));
            }

            if (!foundClasses) {
                textArea.setText("No classes are scheduled yet.");
            } else {
                textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
                textArea.setText(sb.toString());
            }

        } catch (SQLException ex) {
            System.err.println("Show All Classes SQL error: " + ex.getMessage());
            ex.printStackTrace(); // Print full stack trace
            textArea.setText("Failed to load all classes due to a database error. Check console for details.");
        }

        allClassDialog.add(scrollPane);
        allClassDialog.setVisible(true);
    }

    /**
     * Displays the student's main dashboard.
     * Includes an option for students to view their personalized schedule.
     */
    private void showStudentDashboard() {
        JFrame studentFrame = new JFrame("Student Dashboard");
        studentFrame.setSize(400, 200);
        studentFrame.setLocationRelativeTo(frame);
        studentFrame.setLayout(new BorderLayout());

        JLabel welcomeLabel = new JLabel("Welcome Student!", JLabel.CENTER);
        welcomeLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        studentFrame.add(welcomeLabel, BorderLayout.CENTER);

        // Add a button for students to view their relevant classes (by batch/department)
        JButton viewStudentClassesBtn = new JButton("View My Schedule");
        viewStudentClassesBtn.addActionListener(e -> showStudentScheduleScreen(studentFrame));
        studentFrame.add(viewStudentClassesBtn, BorderLayout.NORTH);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> {
            loggedInStudentId = -1;
            studentFrame.dispose();
            showWelcomeScreen();
        });
        studentFrame.add(logoutBtn, BorderLayout.SOUTH);

        studentFrame.setVisible(true);
    }

    /**
     * Displays a dialog showing the schedule relevant to the logged-in student's batch and department.
     */
    private void showStudentScheduleScreen(JFrame parentFrame) {
        JDialog studentScheduleDialog = new JDialog(parentFrame, "My University Schedule", true);
        studentScheduleDialog.setSize(900, 400); // Adjusted width for new columns
        studentScheduleDialog.setLocationRelativeTo(parentFrame);

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);

        // Retrieve student's batch and department to filter the schedule
        String studentBatch = "";
        String studentDepartment = "";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT batch, department FROM Students WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, loggedInStudentId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                studentBatch = rs.getString("batch");
                studentDepartment = rs.getString("department");
            } else {
                JOptionPane.showMessageDialog(studentScheduleDialog, "Could not retrieve your student details. Please ensure your profile is complete.", "Data Missing", JOptionPane.WARNING_MESSAGE);
                studentScheduleDialog.dispose();
                return;
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving student details for schedule: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(studentScheduleDialog, "Error retrieving student details due to a database error. Check console for details.", "Database Error", JOptionPane.ERROR_MESSAGE);
            studentScheduleDialog.dispose();
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Select classes matching the student's batch and department, ordered by time
            String sql = "SELECT teacher_name, department, batch, course, room, time, class_date, class_end_time FROM Schedule WHERE batch = ? AND department = ? ORDER BY class_date, time"; // Order by date then start time
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, studentBatch);
            stmt.setString(2, studentDepartment);
            ResultSet rs = stmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            // Format header for readability, including new date/time columns
            sb.append(String.format("%-15s %-15s %-10s %-15s %-8s %-10s %-12s %-12s%n",
                    "Teacher Name", "Department", "Batch", "Course", "Room", "Start Time", "Date", "End Time"));
            sb.append("-----------------------------------------------------------------------------------------------------------\n");

            boolean foundClasses = false;
            while (rs.next()) {
                foundClasses = true;
                sb.append(String.format("%-15s %-15s %-10s %-15s %-8s %-10s %-12s %-12s%n",
                        rs.getString("teacher_name"),
                        rs.getString("department"),
                        rs.getString("batch"),
                        rs.getString("course"),
                        rs.getString("room"),
                        rs.getString("time"),
                        rs.getString("class_date"),
                        rs.getString("class_end_time")));
            }

            if (!foundClasses) {
                textArea.setText("No classes scheduled for your batch '" + studentBatch + "' and department '" + studentDepartment + "' yet.");
            } else {
                textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
                textArea.setText(sb.toString());
            }

        } catch (SQLException ex) {
            System.err.println("Show Student Schedule SQL error: " + ex.getMessage());
            ex.printStackTrace(); // Print full stack trace
            textArea.setText("Failed to load your schedule due to a database error. Check console for details.");
        }

        studentScheduleDialog.add(scrollPane);
        studentScheduleDialog.setVisible(true);
    }

    /**
     * Updates an existing class in the Schedule table.
     * Only the teacher who assigned the class can update it.
     *
     * @param classId The ID of the class to update.
     * @param newDepartment The new department (can be null or empty if not changing).
     * @param newBatch The new batch (can be null or empty if not changing).
     * @param newCourse The new course name (can be null or empty if not changing).
     * @param newRoom The new room (can be null or empty if not changing).
     * @param newTime The new start time (can be null or empty if not changing).
     * @param newDate The new class date (can be null or empty if not changing).
     * @param newEndTime The new class end time (can be null or empty if not changing).
     * @return true if the update was successful, false otherwise.
     */
    private boolean updateClass(int classId, String newDepartment, String newBatch,
                                String newCourse, String newRoom, String newTime,
                                String newDate, String newEndTime) {
        // Construct the SQL UPDATE statement dynamically based on what needs to be updated
        StringBuilder sqlBuilder = new StringBuilder("UPDATE Schedule SET ");
        boolean firstParam = true;

        if (newDepartment != null && !newDepartment.isEmpty()) {
            sqlBuilder.append("department = ?");
            firstParam = false;
        }
        if (newBatch != null && !newBatch.isEmpty()) {
            if (!firstParam) sqlBuilder.append(", ");
            sqlBuilder.append("batch = ?");
            firstParam = false;
        }
        if (newCourse != null && !newCourse.isEmpty()) {
            if (!firstParam) sqlBuilder.append(", ");
            sqlBuilder.append("course = ?");
            firstParam = false;
        }
        if (newRoom != null && !newRoom.isEmpty()) {
            if (!firstParam) sqlBuilder.append(", ");
            sqlBuilder.append("room = ?");
            firstParam = false;
        }
        if (newTime != null && !newTime.isEmpty()) {
            if (!firstParam) sqlBuilder.append(", ");
            sqlBuilder.append("time = ?");
            firstParam = false;
        }
        if (newDate != null && !newDate.isEmpty()) {
            if (!firstParam) sqlBuilder.append(", ");
            sqlBuilder.append("class_date = ?");
            firstParam = false;
        }
        if (newEndTime != null && !newEndTime.isEmpty()) {
            if (!firstParam) sqlBuilder.append(", ");
            sqlBuilder.append("class_end_time = ?");
            firstParam = false;
        }

        // If no fields are provided for update, inform the user and return
        if (firstParam) {
            JOptionPane.showMessageDialog(null, "No fields provided for update. Please enter at least one new value.", "Update Error", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // Add WHERE clause to target a specific class owned by the logged-in teacher
        sqlBuilder.append(" WHERE id = ? AND teacher_id = ?");

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {

            int paramIndex = 1;
            // Set parameters dynamically based on which fields are being updated
            if (newDepartment != null && !newDepartment.isEmpty()) {
                stmt.setString(paramIndex++, newDepartment);
            }
            if (newBatch != null && !newBatch.isEmpty()) {
                stmt.setString(paramIndex++, newBatch);
            }
            if (newCourse != null && !newCourse.isEmpty()) {
                stmt.setString(paramIndex++, newCourse);
            }
            if (newRoom != null && !newRoom.isEmpty()) {
                stmt.setString(paramIndex++, newRoom);
            }
            if (newTime != null && !newTime.isEmpty()) {
                stmt.setString(paramIndex++, newTime);
            }
            if (newDate != null && !newDate.isEmpty()) {
                stmt.setString(paramIndex++, newDate);
            }
            if (newEndTime != null && !newEndTime.isEmpty()) {
                stmt.setString(paramIndex++, newEndTime);
            }


            stmt.setInt(paramIndex++, classId); // Set the class ID for the WHERE clause
            stmt.setInt(paramIndex++, loggedInTeacherId); // Set the teacher ID for the WHERE clause

            int rowsAffected = stmt.executeUpdate(); // Execute the update operation

            if (rowsAffected > 0) {
                JOptionPane.showMessageDialog(null, "Class ID " + classId + " updated successfully.", "Update Success", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } else {
                JOptionPane.showMessageDialog(null, "Class ID " + classId + " not found or you don't have permission to update it.", "Update Failed", JOptionPane.WARNING_MESSAGE);
                return false;
            }

        } catch (SQLException e) {
            System.err.println("Database update error: " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging
            JOptionPane.showMessageDialog(null, "Failed to update class due to a database error. Check console for details.", "Database Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Displays a dialog for a teacher to edit an existing class.
     * Allows loading current class details and updating selected fields, including new date/time.
     * @param parentFrame The parent JFrame for this dialog.
     */
    private void showEditClassScreen(JFrame parentFrame) {
        JDialog editDialog = new JDialog(parentFrame, "Edit Class", true);
        editDialog.setSize(450, 500); // Increased height for new fields
        editDialog.setLayout(new GridLayout(10, 2, 8, 8)); // Increased rows
        editDialog.setLocationRelativeTo(parentFrame);

        JTextField classIdField = new JTextField();
        JTextField departmentField = new JTextField();
        JTextField batchField = new JTextField();
        JTextField courseField = new JTextField();
        JTextField roomField = new JTextField();
        JTextField timeField = new JTextField(); // Start time
        JTextField dateField = new JTextField(); // New
        JTextField endTimeField = new JTextField(); // New

        editDialog.add(new JLabel("Class ID to Edit:"));
        editDialog.add(classIdField);
        editDialog.add(new JLabel("New Department (leave blank to keep current):"));
        editDialog.add(departmentField);
        editDialog.add(new JLabel("New Batch (leave blank to keep current):"));
        editDialog.add(batchField);
        editDialog.add(new JLabel("New Course (leave blank to keep current):"));
        editDialog.add(courseField);
        editDialog.add(new JLabel("New Room (leave blank to keep current):"));
        editDialog.add(roomField);
        editDialog.add(new JLabel("New Start Time (HH:MM, leave blank to keep current):")); // Label update
        editDialog.add(timeField);
        editDialog.add(new JLabel("New Date (YYYY-MM-DD, leave blank to keep current):")); // New label
        editDialog.add(dateField); // New field
        editDialog.add(new JLabel("New End Time (HH:MM, leave blank to keep current):")); // New label
        editDialog.add(endTimeField); // New field

        JButton loadClassBtn = new JButton("Load Class Details"); // Button to pre-fill current data
        JButton updateBtn = new JButton("Update Class");

        editDialog.add(loadClassBtn);
        editDialog.add(updateBtn);

        // Action listener for loading existing class details
        loadClassBtn.addActionListener(e -> {
            String idText = classIdField.getText().trim();
            if (idText.isEmpty()) {
                JOptionPane.showMessageDialog(editDialog, "Please enter a Class ID to load details.", "Input Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                int classId = Integer.parseInt(idText);
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    // Select existing details for the given class ID and logged-in teacher
                    String sql = "SELECT department, batch, course, room, time, class_date, class_end_time FROM Schedule WHERE id = ? AND teacher_id = ?";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setInt(1, classId);
                    stmt.setInt(2, loggedInTeacherId);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) { // If class found and owned by teacher
                        departmentField.setText(rs.getString("department"));
                        batchField.setText(rs.getString("batch"));
                        courseField.setText(rs.getString("course"));
                        roomField.setText(rs.getString("room"));
                        timeField.setText(rs.getString("time"));
                        dateField.setText(rs.getString("class_date"));
                        endTimeField.setText(rs.getString("class_end_time"));
                        JOptionPane.showMessageDialog(editDialog, "Class details loaded successfully.", "Load Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(editDialog, "Class ID not found or you don't own this class.", "Load Failed", JOptionPane.WARNING_MESSAGE);
                        // Clear fields if not found or not owned
                        departmentField.setText("");
                        batchField.setText("");
                        courseField.setText("");
                        roomField.setText("");
                        timeField.setText("");
                        dateField.setText("");
                        endTimeField.setText("");
                    }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(editDialog, "Invalid Class ID. Please enter a number.", "Input Error", JOptionPane.WARNING_MESSAGE);
            } catch (SQLException ex) {
                System.err.println("Error loading class details: " + ex.getMessage());
                ex.printStackTrace(); // Print full stack trace
                JOptionPane.showMessageDialog(editDialog, "Failed to load class details due to a database error. Check console for details.", "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Action listener for updating class details
        updateBtn.addActionListener(e -> {
            String idText = classIdField.getText().trim();
            if (idText.isEmpty()) {
                JOptionPane.showMessageDialog(editDialog, "Please enter the Class ID to update.", "Input Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                int classId = Integer.parseInt(idText);
                // Get new values from text fields (trimmed)
                String newDepartment = departmentField.getText().trim();
                String newBatch = batchField.getText().trim();
                String newCourse = courseField.getText().trim();
                String newRoom = roomField.getText().trim();
                String newStartTime = timeField.getText().trim();
                String newDate = dateField.getText().trim();
                String newEndTime = endTimeField.getText().trim();

                // Validate new date and time formats if they are not empty
                if (!newDate.isEmpty() && !newStartTime.isEmpty() && !newEndTime.isEmpty()) {
                    try {
                        LocalDate.parse(newDate);
                        LocalTime.parse(newStartTime);
                        LocalTime.parse(newEndTime);
                    } catch (DateTimeParseException ex) {
                        JOptionPane.showMessageDialog(editDialog, "Invalid date or time format. Use YYYY-MM-DD for date and HH:MM for time.", "Format Error", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }


                // Call the updateClass method to perform the database update
                boolean success = updateClass(classId, newDepartment, newBatch, newCourse, newRoom, newStartTime, newDate, newEndTime);

                if (success) {
                    editDialog.dispose(); // Close dialog on successful update
                }
                // The updateClass method already displays success/failure messages, so no need for another here.
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(editDialog, "Invalid Class ID. Please enter a valid number.", "Input Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        editDialog.setVisible(true);
    }

    /**
     * Starts a scheduled task to periodically clean up expired classes from the database.
     * This runs only when the application is active.
     */
    private void startAutoClassCleanup() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        // Schedule the cleanup task to run every 5 minutes (adjust as needed)
        scheduler.scheduleAtFixedRate(this::cleanupExpiredClasses, 0, 5, TimeUnit.MINUTES);
        System.out.println("Auto class cleanup scheduled to run every 5 minutes.");

        // Add a shutdown hook to gracefully shut down the scheduler when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                        System.err.println("Scheduler did not terminate gracefully, forced shutdown.");
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                System.out.println("Auto class cleanup scheduler shut down.");
            }
        }));
    }

    /**
     * Connects to the database and deletes classes whose `class_date` and `class_end_time`
     * are in the past relative to the current time.
     */
    private void cleanupExpiredClasses() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("Running scheduled class cleanup at: " + now);

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // SQL to delete classes where the class_date and class_end_time are in the past
            // We concatenate date and time strings for comparison as YYYY-MM-DDTHH:MM
            // SQLite's DATETIME function can convert text to a comparable format.
            String sql = "DELETE FROM Schedule WHERE class_date || 'T' || class_end_time < STRFTIME('%Y-%m-%dT%H:%M', 'now', 'localtime')";
            int deletedRows = stmt.executeUpdate(sql);

            if (deletedRows > 0) {
                System.out.println("Cleaned up " + deletedRows + " expired classes.");
            } else {
                System.out.println("No expired classes to clean up.");
            }

        } catch (SQLException e) {
            System.err.println("Error during scheduled class cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }
}